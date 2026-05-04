package newcms.controller;

import com.alibaba.fastjson.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import newcms.annotation.PathRestController;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.service.IMainLeaveService;
import newcms.utils.LogUtil;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "请假管理")
@PathRestController("main-leave")
public class MainLeaveController {

    @Resource
    private IMainLeaveService iMainLeaveService;

    @Operation(summary = "请假提交审核（写入首条待审 MainVerifyProcess）",
            description = "在已保存的 MainLeave 上创建 MainVerifyProcess，relationId=请假 id，tableName=MainLeave。"
                    + "若已存在「提交待审核」或「审核通过」记录则幂等跳过。"
                    + "审核人：仅按 MainLeave.verifyFirstRoleId 解析；解析不到则系统自动通过。")
    @PostMapping(value = "/submit-audit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object submitLeaveAudit(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("mainLeave.submitLeaveAudit", requestJson);
        JSONObject node = requestJson.getJSONObject("node");
        if (node == null) {
            throw BaseResponse.parameterInvalid.error("node 不能为空");
        }
        Integer leaveId = node.getInteger("leaveId");
        if (leaveId == null) {
            throw BaseResponse.parameterInvalid.error("leaveId 不能为空");
        }
        Integer processId = getOptionalPositiveInteger(node, "processId");
        if (processId == null) {
            processId = getOptionalPositiveInteger(requestJson, "processId");
        }
        String processTypeCode = getOptionalText(node, "processTypeCode");
        if (processTypeCode == null) {
            processTypeCode = getOptionalText(requestJson, "processTypeCode");
        }
        iMainLeaveService.ensureSubmitVerifyProcess(leaveId, Base.getLoginUserId(), processId, processTypeCode);
        return BaseResponse.ok(null);
    }

    private Integer getOptionalPositiveInteger(JSONObject json, String key) {
        if (json == null || !json.containsKey(key)) {
            return null;
        }
        Object raw = json.get(key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number) {
            Integer value = ((Number) raw).intValue();
            return value > 0 ? value : null;
        }
        String value = String.valueOf(raw).trim();
        if (value.isEmpty() || "-".equals(value)) {
            return null;
        }
        try {
            Integer parsed = Integer.valueOf(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            throw BaseResponse.parameterInvalid.error(key + " must be a positive integer");
        }
    }

    private String getOptionalText(JSONObject json, String key) {
        if (json == null || !json.containsKey(key)) {
            return null;
        }
        Object raw = json.get(key);
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() || "-".equals(value) ? null : value;
    }
}
