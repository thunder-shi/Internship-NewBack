package newcms.controller;

import com.alibaba.fastjson.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import newcms.annotation.PathRestController;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.base.Constant;
import newcms.service.IEnterpriseInfoService;
import newcms.utils.LogUtil;
import org.springframework.data.domain.Sort;
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
        QueryArgs args = parseQueryArgs(requestJson);
        return BaseResponse.ok(enterpriseInfoService.listMyHistory(
                args.searchKeys, Base.getLoginUserId(), args.sort, args.page, args.size));
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
        QueryArgs args = parseQueryArgs(requestJson);
        return BaseResponse.ok(enterpriseInfoService.listAudits(
                args.searchKeys, Base.getLoginUserId(), args.sort, args.page, args.size));
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

    private QueryArgs parseQueryArgs(JSONObject requestJson) {
        JSONObject node = requestJson == null ? null : requestJson.getJSONObject("node");
        JSONObject searchKeys = node != null && node.getJSONObject("searchKey") != null
                ? node.getJSONObject("searchKey")
                : requestJson != null && requestJson.getJSONObject("searchKey") != null
                ? requestJson.getJSONObject("searchKey")
                : new JSONObject();
        JSONObject pageInfo = node != null && node.getJSONObject("pageInfo") != null
                ? node.getJSONObject("pageInfo")
                : requestJson != null ? requestJson.getJSONObject("pageInfo") : null;
        Integer page = pageInfo != null ? pageInfo.getInteger("page") : null;
        Integer size = pageInfo != null ? pageInfo.getInteger("size") : null;
        if (page == null) {
            page = node != null ? node.getInteger("page") : requestJson == null ? null : requestJson.getInteger("page");
        }
        if (size == null) {
            size = node != null ? node.getInteger("size") : requestJson == null ? null : requestJson.getInteger("size");
        }
        JSONObject sortJson = node != null && node.getJSONObject("sort") != null
                ? node.getJSONObject("sort")
                : requestJson != null ? requestJson.getJSONObject("sort") : null;
        Sort sort = parseSort(sortJson);
        return new QueryArgs(searchKeys, sort,
                page == null ? Constant.DEFAULT_PAGE : page,
                size == null ? Constant.DEFAULT_SIZE : size);
    }

    private Sort parseSort(JSONObject sortJson) {
        if (sortJson == null) {
            return Sort.by(Sort.Direction.DESC, "id");
        }
        String properties = sortJson.getString("properties");
        if (properties == null || properties.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "id");
        }
        Sort.Direction direction = Sort.Direction.DESC;
        String directionStr = sortJson.getString("direction");
        if (directionStr != null && !directionStr.isBlank()) {
            try {
                direction = Sort.Direction.fromString(directionStr);
            } catch (IllegalArgumentException ignored) {
                direction = Sort.Direction.DESC;
            }
        }
        return Sort.by(direction, properties.split(Constant.SPLIT_OPERATOR.COMMA));
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

    private static class QueryArgs {
        private final JSONObject searchKeys;
        private final Sort sort;
        private final Integer page;
        private final Integer size;

        private QueryArgs(JSONObject searchKeys, Sort sort, Integer page, Integer size) {
            this.searchKeys = searchKeys;
            this.sort = sort;
            this.page = page;
            this.size = size;
        }
    }
}
