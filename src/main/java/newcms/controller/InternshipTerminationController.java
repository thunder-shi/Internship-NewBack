package newcms.controller;

import com.alibaba.fastjson.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import newcms.annotation.PathRestController;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.base.Constant;
import newcms.controller.commonCtrl.ControllerQueryArgs;
import newcms.service.IInternshipService;
import newcms.service.IInternshipTerminationService;
import newcms.utils.LogUtil;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
import java.util.Set;

@Tag(name = "终止学生实习")
@PathRestController("internshipTermination")
public class InternshipTerminationController {

    /** 终止实习候选/审核列表特有的元 key（合并 searchKey 时排除）。 */
    private static final Set<String> EXTRA_RESERVED_KEYS = Set.of("onlyMine");

    @Resource
    private IInternshipTerminationService internshipTerminationService;
    @Resource
    private IInternshipService internshipService;

    @Operation(summary = "终止学生实习候选列表")
    @PostMapping(value = "/listCandidates", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object listCandidates(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("internshipTermination.listCandidates", requestJson);
        ControllerQueryArgs args = ControllerQueryArgs.parse(requestJson, true, EXTRA_RESERVED_KEYS);
        normalizeCandidateSearch(args);
        Integer currentUserId = Base.getLoginUserId();
        if (currentUserId == null) {
            throw BaseResponse.lackPermissions.error("current user cannot be empty");
        }
        args.searchKeys().put("studentId", currentUserId);
        args.regMap().remove("studentId");
        return BaseResponse.ok(internshipTerminationService.listCandidates(
                args.searchKeys(), args.regMap(), args.sort(), args.page(), args.size()));
    }

    @Operation(summary = "终止学生实习审核列表")
    @PostMapping(value = "/listAudits", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object listAudits(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("internshipTermination.listAudits", requestJson);
        ControllerQueryArgs args = ControllerQueryArgs.parse(requestJson, true, EXTRA_RESERVED_KEYS);
        JSONObject node = requestJson == null ? null : requestJson.getJSONObject("node");
        boolean onlyMine = getBoolean(node, "onlyMine") || getBoolean(requestJson, "onlyMine");
        if (onlyMine) {
            args.searchKeys().put("verifyUserId", String.valueOf(Base.getLoginUserId()));
            args.regMap().put("verifyUserId", Constant.FIND_IN);
        }
        return BaseResponse.ok(internshipTerminationService.listAudits(
                args.searchKeys(), args.regMap(), args.sort(), args.page(), args.size()));
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

    private void normalizeCandidateSearch(ControllerQueryArgs args) {
        normalizeCandidateInternshipMode(args);
        normalizeCandidateStatus(args);
    }

    private void normalizeCandidateInternshipMode(ControllerQueryArgs args) {
        JSONObject searchKeys = args.searchKeys();
        Map<String, String> regMap = args.regMap();
        Object raw = firstPresent(searchKeys,
                "internshipMode", "internshipType", "type", "intTypeId", "relationTable");
        removeSearchAliases(searchKeys, regMap, "internshipType", "type", "intTypeId");
        if (raw == null) {
            return;
        }
        String mode = normalizeInternshipMode(raw);
        if (mode == null) {
            searchKeys.remove("internshipMode");
            searchKeys.remove("relationTable");
            regMap.remove("internshipMode");
            regMap.remove("relationTable");
            return;
        }
        searchKeys.put("internshipMode", mode);
        searchKeys.remove("relationTable");
        regMap.remove("internshipMode");
        regMap.remove("relationTable");
    }

    private void normalizeCandidateStatus(ControllerQueryArgs args) {
        JSONObject searchKeys = args.searchKeys();
        Map<String, String> regMap = args.regMap();
        Object raw = firstPresent(searchKeys,
                "internshipStatus", "relationStatus", "status", "terminationStatus");
        removeSearchAliases(searchKeys, regMap, "relationStatus", "status", "terminationStatus");
        if (raw == null) {
            return;
        }
        Integer status = normalizeInternshipStatus(raw);
        if (status == null) {
            searchKeys.remove("internshipStatus");
            regMap.remove("internshipStatus");
            return;
        }
        searchKeys.put("internshipStatus", status);
        regMap.remove("internshipStatus");
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

    private void removeSearchAliases(JSONObject searchKeys, Map<String, String> regMap, String... keys) {
        for (String key : keys) {
            searchKeys.remove(key);
            regMap.remove(key);
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

    private boolean getBoolean(JSONObject json, String key) {
        return json != null && Boolean.TRUE.equals(json.getBoolean(key));
    }
}
