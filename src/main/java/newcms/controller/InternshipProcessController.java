package newcms.controller;

import com.alibaba.fastjson.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import newcms.annotation.PathRestController;
import newcms.base.BaseResponse;
import newcms.service.IInternshipService;
import newcms.utils.LogUtil;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Arrays;
import java.util.List;

@Tag(name = "实习项目管理")
@PathRestController("internshipProcess")
public class InternshipProcessController {

    @Resource
    private IInternshipService iInternshipService;

    // ==================== 实习项目管理（无需审核） ====================

    @Operation(summary = "新增实习项目", description = "创建实习项目并自动配置流程，无需审核")
    @PostMapping(value = "/addNewInternship", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object addNewInternship(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("addNewInternship", requestJson);
        if (requestJson == null) {
            throw BaseResponse.parameterInvalid.error("请求参数不能为空");
        }
        JSONObject node = requestJson.getJSONObject("node");
        if (node == null) {
            throw BaseResponse.parameterInvalid.error("node 参数不能为空");
        }
        return BaseResponse.ok(iInternshipService.addNewInternship(node));
    }

    // ==================== 实习计划流程（需要审核） ====================

    @Operation(summary = "提交实习计划", description = "提交计划制定流程，创建审核记录")
    @PostMapping(value = "/submitInternshipPlan", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object submitInternshipPlan(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("submitInternshipPlan", requestJson);
        if (requestJson == null) {
            throw BaseResponse.parameterInvalid.error("请求参数不能为空");
        }
        return BaseResponse.ok(iInternshipService.submitInternshipPlan(requestJson));
    }

    @Operation(summary = "删除实习项目", description = "删除实习项目及其关联的流程配置")
    @PostMapping(value = "/deleteNewInternship", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object deleteNewInternship(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("deleteNewInternship", requestJson);
        if (requestJson == null) {
            throw BaseResponse.parameterInvalid.error("请求参数不能为空");
        }
        String idsStr = requestJson.getString("ids");
        if (idsStr == null || idsStr.trim().isEmpty()) {
            throw BaseResponse.parameterInvalid.error("ids 参数不能为空");
        }
        // 去除首尾的方括号（如果存在）
        idsStr = idsStr.trim();
        if (idsStr.startsWith("[") && idsStr.endsWith("]")) {
            idsStr = idsStr.substring(1, idsStr.length() - 1);
        }
        // 解析逗号分隔的id字符串
        List<Integer> ids = Arrays.asList(idsStr.split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    // 再次去除可能的方括号
                    s = s.replaceAll("^\\[|\\]$", "");
                    return Integer.parseInt(s.trim());
                })
                .collect(java.util.stream.Collectors.toList());
        if (ids.isEmpty()) {
            throw BaseResponse.parameterInvalid.error("ids 参数不能为空");
        }
        return BaseResponse.ok(iInternshipService.deleteNewInternship(ids));
    }
}
