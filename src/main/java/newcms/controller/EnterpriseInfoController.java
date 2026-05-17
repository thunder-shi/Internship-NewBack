package newcms.controller;

import com.alibaba.fastjson.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import newcms.annotation.PathRestController;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.controller.commonCtrl.ControllerQueryArgs;
import newcms.service.IEnterpriseInfoService;
import newcms.utils.LogUtil;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "企业信息维护")
@PathRestController("enterpriseInfo")
public class EnterpriseInfoController {
    @Resource
    private IEnterpriseInfoService enterpriseInfoService;

    @Operation(summary = "当前企业信息概览")
    @PostMapping(value = "/mine", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object mine(@RequestBody(required = false) JSONObject requestJson) {
        LogUtil.loggerRecord("enterpriseInfo.mine", requestJson);
        return BaseResponse.ok(enterpriseInfoService.getMine(Base.getLoginUserId()));
    }

    @Operation(summary = "当前企业信息历史记录")
    @PostMapping(value = "/history", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object history(@RequestBody(required = false) JSONObject requestJson) {
        LogUtil.loggerRecord("enterpriseInfo.history", requestJson);
        ControllerQueryArgs args = ControllerQueryArgs.parse(requestJson);
        return BaseResponse.ok(enterpriseInfoService.listMyHistory(
                args.searchKeys(), Base.getLoginUserId(), args.sort(), args.page(), args.size()));
    }

    @Operation(summary = "企业信息详情")
    @PostMapping(value = "/detail", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object detail(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("enterpriseInfo.detail", requestJson);
        JSONObject node = requireNode(requestJson);
        Integer enterpriseInfoId = firstInteger(node, "enterpriseInfoId", "id");
        return BaseResponse.ok(enterpriseInfoService.detail(enterpriseInfoId, Base.getLoginUserId()));
    }

    @Operation(summary = "保存企业信息草稿")
    @PostMapping(value = "/saveDraft", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object saveDraft(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("enterpriseInfo.saveDraft", requestJson);
        JSONObject node = requireNode(requestJson);
        return BaseResponse.ok(enterpriseInfoService.saveDraft(node, Base.getLoginUserId()));
    }

    @Operation(summary = "提交企业信息审核")
    @PostMapping(value = "/submit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object submit(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("enterpriseInfo.submit", requestJson);
        JSONObject node = requireNode(requestJson);
        return BaseResponse.ok(enterpriseInfoService.submit(node, Base.getLoginUserId()));
    }

    @Operation(summary = "退回后重新提交企业信息审核")
    @PostMapping(value = "/resubmit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object resubmit(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("enterpriseInfo.resubmit", requestJson);
        JSONObject node = requireNode(requestJson);
        return BaseResponse.ok(enterpriseInfoService.resubmit(node, Base.getLoginUserId()));
    }

    @Operation(summary = "企业信息审核列表")
    @PostMapping(value = "/audit/list", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object auditList(@RequestBody(required = false) JSONObject requestJson) {
        LogUtil.loggerRecord("enterpriseInfo.auditList", requestJson);
        ControllerQueryArgs args = ControllerQueryArgs.parse(requestJson);
        return BaseResponse.ok(enterpriseInfoService.listAudits(
                args.searchKeys(), Base.getLoginUserId(), args.sort(), args.page(), args.size()));
    }

    @Operation(summary = "企业信息审核详情")
    @PostMapping(value = "/audit/detail", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object auditDetail(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("enterpriseInfo.auditDetail", requestJson);
        JSONObject node = requireNode(requestJson);
        Integer enterpriseInfoId = firstInteger(node, "enterpriseInfoId", "id");
        return BaseResponse.ok(enterpriseInfoService.auditDetail(enterpriseInfoId, Base.getLoginUserId()));
    }

    @Operation(summary = "企业信息审核处理")
    @PostMapping(value = "/audit/process", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object auditProcess(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("enterpriseInfo.auditProcess", requestJson);
        JSONObject node = requireNode(requestJson);
        return BaseResponse.ok(enterpriseInfoService.audit(node, Base.getLoginUserId()));
    }

    @Operation(summary = "获取企业信息审核配置")
    @PostMapping(value = "/verifyConfig/get", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object getVerifyConfig(@RequestBody(required = false) JSONObject requestJson) {
        LogUtil.loggerRecord("enterpriseInfo.getVerifyConfig", requestJson);
        JSONObject node = requestJson == null ? null : requestJson.getJSONObject("node");
        Integer schoolId = node != null ? node.getInteger("schoolId") : requestJson == null ? null : requestJson.getInteger("schoolId");
        return BaseResponse.ok(enterpriseInfoService.getVerifyConfig(schoolId, Base.getLoginUserId()));
    }

    @Operation(summary = "保存企业信息审核配置")
    @PostMapping(value = "/verifyConfig/save", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object saveVerifyConfig(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("enterpriseInfo.saveVerifyConfig", requestJson);
        JSONObject node = requireNode(requestJson);
        return BaseResponse.ok(enterpriseInfoService.saveVerifyConfig(node, Base.getLoginUserId()));
    }

    private JSONObject requireNode(JSONObject requestJson) {
        JSONObject node = requestJson == null ? null : requestJson.getJSONObject("node");
        if (node == null) {
            throw BaseResponse.parameterInvalid.error("node cannot be empty");
        }
        return node;
    }

    private Integer firstInteger(JSONObject node, String... keys) {
        for (String key : keys) {
            Integer value = node.getInteger(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
