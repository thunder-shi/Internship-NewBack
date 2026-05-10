package newcms.controller;

import com.alibaba.fastjson.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import newcms.annotation.PathRestController;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.base.Constant;
import newcms.service.IInternshipService;
import newcms.service.IInternshipTerminationService;
import newcms.utils.LogUtil;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "终止学生实习")
@PathRestController("internshipTermination")
public class InternshipTerminationController {
    @Resource
    private IInternshipTerminationService internshipTerminationService;
    @Resource
    private IInternshipService internshipService;

    @Operation(summary = "终止学生实习候选列表")
    @PostMapping(value = "/listCandidates", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object listCandidates(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("internshipTermination.listCandidates", requestJson);
        QueryArgs args = parseQueryArgs(requestJson);
        normalizeCandidateSearch(args);
        Integer currentUserId = Base.getLoginUserId();
        if (currentUserId == null) {
            throw BaseResponse.lackPermissions.error("current user cannot be empty");
        }
        args.searchKeys.put("studentId", currentUserId);
        args.regMap.remove("studentId");
        return BaseResponse.ok(internshipTerminationService.listCandidates(
                args.searchKeys, args.regMap, args.sort, args.page, args.size));
    }

    @Operation(summary = "终止学生实习审核列表")
    @PostMapping(value = "/listAudits", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object listAudits(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("internshipTermination.listAudits", requestJson);
        QueryArgs args = parseQueryArgs(requestJson);
        JSONObject node = requestJson == null ? null : requestJson.getJSONObject("node");
        boolean onlyMine = getBoolean(node, "onlyMine") || getBoolean(requestJson, "onlyMine");
        if (onlyMine) {
            args.searchKeys.put("verifyUserId", String.valueOf(Base.getLoginUserId()));
            args.regMap.put("verifyUserId", Constant.FIND_IN);
            args.searchKeys.put("isAudit", Constant.AUDIT_STATUS.SUBMIT);
        }
        return BaseResponse.ok(internshipTerminationService.listAudits(
                args.searchKeys, args.regMap, args.sort, args.page, args.size));
    }

    @Operation(summary = "发起终止学生实习")
    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object create(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("internshipTermination.create", requestJson);
        JSONObject node = requireNode(requestJson);
        return BaseResponse.ok(internshipTerminationService.create(node, Base.getLoginUserId()));
    }

    @Operation(summary = "终止学生实习审核")
    @PostMapping(value = "/audit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object audit(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("internshipTermination.audit", requestJson);
        Object node = requestJson == null ? null : requestJson.get("node");
        if (node == null) {
            throw BaseResponse.parameterInvalid.error("node cannot be empty");
        }
        return BaseResponse.ok(internshipService.auditProcess(node));
    }

    @Operation(summary = "终止学生实习详情")
    @PostMapping(value = "/detail", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object detail(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("internshipTermination.detail", requestJson);
        JSONObject node = requireNode(requestJson);
        Integer terminationId = node.getInteger("terminationId");
        if (terminationId == null) {
            terminationId = node.getInteger("id");
        }
        return BaseResponse.ok(internshipTerminationService.detail(terminationId));
    }

    @Operation(summary = "取消终止学生实习申请")
    @PostMapping(value = "/cancel", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object cancel(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("internshipTermination.cancel", requestJson);
        JSONObject node = requireNode(requestJson);
        Integer terminationId = node.getInteger("terminationId");
        if (terminationId == null) {
            terminationId = node.getInteger("id");
        }
        return BaseResponse.ok(internshipTerminationService.cancel(terminationId, Base.getLoginUserId()));
    }

    @Operation(summary = "退回后重新提交终止学生实习申请")
    @PostMapping(value = "/resubmit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object resubmit(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("internshipTermination.resubmit", requestJson);
        JSONObject node = requireNode(requestJson);
        return BaseResponse.ok(internshipTerminationService.resubmit(node, Base.getLoginUserId()));
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
        JSONObject searchKeys = parseSearchKeys(requestJson, node);
        JSONObject regJson = node != null && node.getJSONObject("reg") != null
                ? node.getJSONObject("reg")
                : requestJson != null && requestJson.getJSONObject("reg") != null
                ? requestJson.getJSONObject("reg")
                : new JSONObject();
        Map<String, String> regMap = new HashMap<>();
        for (String key : regJson.keySet()) {
            regMap.put(key, regJson.getString(key));
        }
        JSONObject pageInfo = node != null && node.getJSONObject("pageInfo") != null
                ? node.getJSONObject("pageInfo")
                : requestJson != null ? requestJson.getJSONObject("pageInfo") : null;
        Integer requestPage = pageInfo != null ? pageInfo.getInteger("page") : null;
        Integer requestSize = pageInfo != null ? pageInfo.getInteger("size") : null;
        if (requestPage == null) {
            requestPage = node != null && node.getInteger("page") != null
                    ? node.getInteger("page")
                    : requestJson != null ? requestJson.getInteger("page") : null;
        }
        if (requestSize == null) {
            requestSize = node != null && node.getInteger("size") != null
                    ? node.getInteger("size")
                    : requestJson != null ? requestJson.getInteger("size") : null;
        }
        int page = requestPage == null ? Constant.DEFAULT_PAGE : requestPage;
        int size = requestSize == null ? Constant.DEFAULT_SIZE : requestSize;
        JSONObject sortJson = node != null && node.getJSONObject("sort") != null
                ? node.getJSONObject("sort")
                : requestJson != null ? requestJson.getJSONObject("sort") : null;
        Sort sort = parseSort(sortJson);
        return new QueryArgs(searchKeys, regMap, sort, page, size);
    }

    private JSONObject parseSearchKeys(JSONObject requestJson, JSONObject node) {
        JSONObject explicitSearchKeys = node != null && node.getJSONObject("searchKey") != null
                ? node.getJSONObject("searchKey")
                : requestJson != null ? requestJson.getJSONObject("searchKey") : null;
        JSONObject searchKeys = new JSONObject();
        if (explicitSearchKeys != null) {
            searchKeys.putAll(explicitSearchKeys);
            return searchKeys;
        }

        if (requestJson != null) {
            searchKeys.putAll(requestJson);
        }
        if (node != null) {
            searchKeys.putAll(node);
        }
        searchKeys.remove("node");
        searchKeys.remove("reg");
        searchKeys.remove("pageInfo");
        searchKeys.remove("sort");
        searchKeys.remove("page");
        searchKeys.remove("size");
        searchKeys.remove("onlyMine");
        return searchKeys;
    }

    private void normalizeCandidateSearch(QueryArgs args) {
        normalizeCandidateInternshipMode(args);
        normalizeCandidateStatus(args);
    }

    private void normalizeCandidateInternshipMode(QueryArgs args) {
        Object raw = firstPresent(args.searchKeys,
                "internshipMode", "internshipType", "type", "intTypeId", "relationTable");
        removeSearchAliases(args, "internshipType", "type", "intTypeId");
        if (raw == null) {
            return;
        }
        String mode = normalizeInternshipMode(raw);
        if (mode == null) {
            args.searchKeys.remove("internshipMode");
            args.searchKeys.remove("relationTable");
            args.regMap.remove("internshipMode");
            args.regMap.remove("relationTable");
            return;
        }
        args.searchKeys.put("internshipMode", mode);
        args.searchKeys.remove("relationTable");
        args.regMap.remove("internshipMode");
        args.regMap.remove("relationTable");
    }

    private void normalizeCandidateStatus(QueryArgs args) {
        Object raw = firstPresent(args.searchKeys,
                "internshipStatus", "relationStatus", "status", "terminationStatus");
        removeSearchAliases(args, "relationStatus", "status", "terminationStatus");
        if (raw == null) {
            return;
        }
        Integer status = normalizeInternshipStatus(raw);
        if (status == null) {
            args.searchKeys.remove("internshipStatus");
            args.regMap.remove("internshipStatus");
            return;
        }
        args.searchKeys.put("internshipStatus", status);
        args.regMap.remove("internshipStatus");
    }

    private Object firstPresent(JSONObject json, String... keys) {
        if (json == null) {
            return null;
        }
        for (String key : keys) {
            Object value = json.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void removeSearchAliases(QueryArgs args, String... keys) {
        for (String key : keys) {
            args.searchKeys.remove(key);
            args.regMap.remove(key);
        }
    }

    private String normalizeInternshipMode(Object raw) {
        String value = String.valueOf(raw).trim();
        if (value.isBlank() || "全部".equals(value) || "全部类型".equals(value) || "ALL".equalsIgnoreCase(value)) {
            return null;
        }
        String normalized = value.replace("_", "").replace("-", "").toUpperCase();
        if ("2".equals(value)
                || "EXTERNAL".equals(normalized)
                || "OUT".equals(normalized)
                || "OUTSCHOOL".equals(normalized)
                || "RELSTUINTERNSHIPPOST".equals(normalized)
                || value.contains("校外")) {
            return "EXTERNAL";
        }
        if ("1".equals(value)
                || "INTERNAL".equals(normalized)
                || "IN".equals(normalized)
                || "INSCHOOL".equals(normalized)
                || "RELTITLESTUDENT".equals(normalized)
                || value.contains("校内")) {
            return "INTERNAL";
        }
        return value;
    }

    private Integer normalizeInternshipStatus(Object raw) {
        String value = String.valueOf(raw).trim();
        if (value.isBlank() || "全部".equals(value) || "全部状态".equals(value) || "ALL".equalsIgnoreCase(value)) {
            return null;
        }
        String normalized = value.replace("_", "").replace("-", "").toUpperCase();
        if ("TERMINATING".equals(normalized)
                || "PENDING".equals(normalized)
                || value.contains("审核中")
                || value.contains("终止审核")) {
            return Constant.INTERNSHIP_RELATION_STATUS.TERMINATING;
        }
        if ("TERMINATED".equals(normalized)
                || value.contains("已终止")
                || value.contains("终止完成")) {
            return Constant.INTERNSHIP_RELATION_STATUS.TERMINATED;
        }
        if ("ACTIVE".equals(normalized) || "NORMAL".equals(normalized) || value.contains("正常")) {
            return Constant.INTERNSHIP_RELATION_STATUS.ACTIVE;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
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

    private boolean getBoolean(JSONObject json, String key) {
        return json != null && Boolean.TRUE.equals(json.getBoolean(key));
    }

    private static class QueryArgs {
        private final JSONObject searchKeys;
        private final Map<String, String> regMap;
        private final Sort sort;
        private final Integer page;
        private final Integer size;

        private QueryArgs(JSONObject searchKeys, Map<String, String> regMap, Sort sort, Integer page, Integer size) {
            this.searchKeys = searchKeys;
            this.regMap = regMap;
            this.sort = sort;
            this.page = page;
            this.size = size;
        }
    }
}
