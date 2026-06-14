package newcms.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import newcms.base.BaseResponse;
import newcms.config.CozeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 调用 Coze 开放平台工作流 API，用于实习日志 AI 批改。
 * <p>工作流约定：入参 {@code file}，出参 {@code score}、{@code output}。</p>
 * <p>Coze 文件类型入参需先 {@code /v1/files/upload}，再以 JSON 字符串 {@code {"file_id":"..."}} 传入。</p>
 */
@Service
public class CozeWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(CozeWorkflowService.class);

    @Resource
    private CozeProperties cozeProperties;

    private final RestClient restClient = RestClient.create();

    /**
     * 从本地字节上传至 Coze 后执行工作流。
     */
    public JSONObject runDiaryReview(byte[] fileBytes, String fileName) {
        assertConfigured();
        String fileParamValue = cozeProperties.useFileIdInput()
                ? buildFileIdParameter(uploadToCoze(fileBytes, fileName))
                : null;
        if (fileParamValue == null) {
            throw BaseResponse.moreInfoError.error("请使用 runDiaryReviewByUrl 或设置 coze.file-input-mode=file-id");
        }
        return invokeWorkflow(fileParamValue);
    }

    /**
     * 使用 MinIO presigned URL 执行工作流（备用模式）。
     */
    public JSONObject runDiaryReviewByUrl(String fileUrl, String fileName) {
        assertConfigured();
        String url = appendCozeFileNameHint(fileUrl, fileName);
        return invokeWorkflow(url);
    }

    private JSONObject invokeWorkflow(String fileParamValue) {
        JSONObject parameters = new JSONObject();
        parameters.put(cozeProperties.getFileUrlParam(), fileParamValue);

        JSONObject body = new JSONObject();
        body.put("workflow_id", cozeProperties.getWorkflowId());
        body.put("parameters", parameters);

        String url = cozeProperties.getApiBase().replaceAll("/$", "") + "/v1/workflow/run";
        log.info("调用 Coze 工作流: workflowId={}, param={}, mode={}",
                cozeProperties.getWorkflowId(),
                cozeProperties.getFileUrlParam(),
                cozeProperties.getFileInputMode());
        log.debug("Coze parameters: {}", parameters.toJSONString());

        String respBody = postJson(url, body.toJSONString());

        JSONObject root = JSON.parseObject(respBody);
        if (root == null) {
            throw BaseResponse.moreInfoError.error("Coze 返回空响应");
        }
        if (root.getIntValue("code") != 0) {
            String msg = root.getString("msg");
            String debugUrl = root.getString("debug_url");
            log.warn("Coze 工作流失败: code={}, msg={}, debug_url={}", root.getInteger("code"), msg, debugUrl);
            throw BaseResponse.moreInfoError.error(
                    "Coze 工作流失败: " + (msg != null ? msg : "未知错误")
                            + (debugUrl != null ? "（debug: " + debugUrl + "）" : ""));
        }

        String dataStr = root.getString("data");
        JSONObject parsed = parseWorkflowData(dataStr);

        JSONObject result = new JSONObject();
        result.put("output", parsed.getString("output"));
        result.put("score", parsed.get("score"));
        result.put("rawData", dataStr);
        result.put("debugUrl", root.getString("debug_url"));
        return result;
    }

    private String uploadToCoze(byte[] fileBytes, String fileName) {
        String safeName = (fileName == null || fileName.isBlank()) ? "diary.pdf" : fileName;
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return safeName;
            }
        });

        String url = cozeProperties.getApiBase().replaceAll("/$", "") + "/v1/files/upload";
        String respBody;
        try {
            respBody = restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + cozeProperties.getPat())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("Coze 文件上传失败", e);
            throw BaseResponse.moreInfoError.error("Coze 文件上传失败: " + e.getMessage());
        }

        JSONObject root = JSON.parseObject(respBody);
        if (root == null || root.getIntValue("code") != 0) {
            throw BaseResponse.moreInfoError.error(
                    "Coze 文件上传失败: " + (root != null ? root.getString("msg") : "空响应"));
        }
        JSONObject data = root.getJSONObject("data");
        String fileId = data != null ? data.getString("id") : null;
        if (fileId == null || fileId.isBlank()) {
            throw BaseResponse.moreInfoError.error("Coze 文件上传未返回 file_id");
        }
        log.info("Coze 文件上传成功: fileId={}, fileName={}", fileId, safeName);
        return fileId;
    }

    /** Coze 文件类型入参：值为 JSON 字符串，不是对象 */
    private String buildFileIdParameter(String fileId) {
        JSONObject fileRef = new JSONObject();
        fileRef.put("file_id", fileId);
        return fileRef.toJSONString();
    }

    private String appendCozeFileNameHint(String fileUrl, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return fileUrl;
        }
        String separator = fileUrl.contains("?") ? "&" : "?";
        return fileUrl + separator + "x-wf-file_name="
                + URLEncoder.encode(fileName, StandardCharsets.UTF_8);
    }

    private String postJson(String url, String jsonBody) {
        try {
            return restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + cozeProperties.getPat())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("Coze HTTP 请求失败: {}", url, e);
            throw BaseResponse.moreInfoError.error("Coze 请求失败: " + e.getMessage());
        }
    }

    private void assertConfigured() {
        if (!cozeProperties.isConfigured()) {
            throw BaseResponse.moreInfoError.error(
                    "Coze 未配置：请在 application.properties 中设置 coze.enabled=true、coze.workflow-id、coze.pat");
        }
    }

    private JSONObject parseWorkflowData(String dataStr) {
        JSONObject result = new JSONObject();
        if (dataStr == null || dataStr.isBlank()) {
            return result;
        }
        try {
            JSONObject obj = JSON.parseObject(dataStr);
            result.put("output", obj.getString("output"));
            result.put("score", parseScore(obj.get("score")));
        } catch (Exception e) {
            log.warn("Coze data 非 JSON，按纯文本作为 output: {}", dataStr);
            result.put("output", dataStr.trim());
        }
        return result;
    }

    private BigDecimal parseScore(Object scoreObj) {
        if (scoreObj == null) {
            return null;
        }
        if (scoreObj instanceof BigDecimal bd) {
            return bd;
        }
        if (scoreObj instanceof Number num) {
            return BigDecimal.valueOf(num.doubleValue());
        }
        String text = scoreObj.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            log.warn("Coze score 无法解析为数字: {}", text);
            return null;
        }
    }
}
