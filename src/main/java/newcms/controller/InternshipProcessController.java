package newcms.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import newcms.annotation.PathRestController;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.base.Constant;
import newcms.service.IInternshipService;
import newcms.service.IVerifyProcessService;
import newcms.utils.EncryptUtil;
import newcms.utils.LogUtil;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.data.domain.Sort;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Tag(name = "实习项目管理")
@PathRestController("internshipProcess")
public class InternshipProcessController {

    @Resource
    private IInternshipService iInternshipService;

    @Resource
    private EncryptUtil encryptUtil;

    @Resource
    private IVerifyProcessService iVerifyProcessService;

    // ==================== 实习项目管理（无需审核） ====================

    @Operation(summary = "新增实习项目", description = "创建实习项目并自动配置流程")
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
        // 使用 encryptUtil 解密并解析逗号分隔的id字符串
        List<String> idsList = Arrays.asList(encryptUtil.getKeyWord(idsStr).split(Constant.SPLIT_OPERATOR.COMMA));
        List<Integer> ids = idsList.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            throw BaseResponse.parameterInvalid.error("ids 参数不能为空");
        }
        return BaseResponse.ok(iInternshipService.deleteNewInternship(ids));
    }

    @Operation(summary = "幂等创建自主实习虚拟岗位",
            description = "入参 {internshipId}。若已存在则返回 {postId, created:false}；否则创建 "
                    + "MainInternshipPost(code='SELF_INTERNSHIP', allPersonNum=-1, companyId=null) 并在项目存在 "
                    + "EXTERNAL_ENTERPRISE_POST_DECLARATION 流程时追加一条自动通过审核。")
    @PostMapping(value = "/createSelfInternshipPost", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object createSelfInternshipPost(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("createSelfInternshipPost", requestJson);
        JSONObject node = requestJson.getJSONObject("node");
        Integer internshipId = node != null ? node.getInteger("internshipId") : requestJson.getInteger("internshipId");
        if (internshipId == null) {
            throw BaseResponse.parameterInvalid.error("internshipId 不能为空");
        }
        return BaseResponse.ok(iInternshipService.createSelfInternshipPost(internshipId));
    }

    @Operation(summary = "学生申请自主实习",
            description = "入参 {internshipId, selfCompanyName, selfPostName, selfAddress, selfRemarks}。"
                    + "同一学生同项目下只能 1 条自主实习记录：SAVE/SUBMIT/PASS/BACK 状态下拒绝；"
                    + "NOTPASS 时复用原记录 id 重投（清空旧审核、清空附件、重置 isAudit=SUBMIT）。"
                    + "不与企业岗位报名互斥，通过后不删除其他企业岗位报名。")
    @PostMapping(value = "/applySelfInternship", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object applySelfInternship(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("applySelfInternship", requestJson);
        JSONObject node = requestJson.getJSONObject("node");
        if (node == null) {
            node = requestJson;
        }
        Integer internshipId = node.getInteger("internshipId");
        String selfCompanyName = node.getString("selfCompanyName");
        String selfPostName = node.getString("selfPostName");
        String selfAddress = node.getString("selfAddress");
        String selfRemarks = node.getString("selfRemarks");
        Integer studentId = Base.getLoginUserId();
        return BaseResponse.ok(iInternshipService.applySelfInternship(internshipId, studentId,
                selfCompanyName, selfPostName, selfAddress, selfRemarks));
    }


    @PostMapping(value = "/auditProcess", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object auditProcess(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("auditProcess", requestJson);
        Object node = requestJson.get("node");
        if (node == null) {
            throw BaseResponse.parameterInvalid.error("node 不能为空");
        }
        return BaseResponse.ok(iInternshipService.auditProcess(node));
    }

    @PostMapping(value = "/confirmStudentTopicSelection", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object confirmStudentTopicSelection(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("confirmStudentTopicSelection", requestJson);
        JSONObject node = requestJson.getJSONObject("node");
        if (node == null) {
            node = requestJson;
        }
        return BaseResponse.ok(iInternshipService.confirmStudentTopicSelection(node));
    }

    @Operation(
            summary = "学生端-查询最近一条选题审核不通过记录",
            description = "按 stuId 查询 view_verify_process_rel_title_student_merge 中最近一条 isAudit=NOTPASS 的记录，返回不通过理由 topicReasons。"
    )
    @PostMapping(value = "/getLatestRejectedTitleSelection", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object getLatestRejectedTitleSelection(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("getLatestRejectedTitleSelection", requestJson);
        JSONObject node = requestJson.getJSONObject("node");
        Integer stuId = node != null ? node.getInteger("stuId") : requestJson.getInteger("stuId");
        return BaseResponse.ok(iInternshipService.getLatestRejectedTitleSelection(stuId));
    }

    @Operation(
            summary = "学生端-确认已知晓不通过并删除选题记录",
            description = "学生点击确认后，删除 RelTitleStudent 对应记录及其 MainVerifyProcess 审核记录，便于重新选题。"
    )
    @PostMapping(value = "/acknowledgeRejectedTitleSelection", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object acknowledgeRejectedTitleSelection(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("acknowledgeRejectedTitleSelection", requestJson);
        JSONObject node = requestJson.getJSONObject("node");
        Integer relationId = node != null ? node.getInteger("relationId") : requestJson.getInteger("relationId");
        Integer stuId = node != null ? node.getInteger("stuId") : requestJson.getInteger("stuId");
        return BaseResponse.ok(iInternshipService.acknowledgeRejectedTitleSelection(relationId, stuId));
    }

    @PostMapping(value = "/activateProcess")
    public Object activateProcess (@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("activateProcess", requestJson);
        JSONObject node = requestJson.getJSONObject("node");
        return BaseResponse.ok(iVerifyProcessService.activateProcess(node));
    }

     @Operation(summary = "根据角色和创建人获取审核人ID串", description = "返回同校、指定审核角色下的所有审核人ID，使用竖线分隔，如：12|14|17。企业用户等无学校归属的用户需传 internshipId 以定位对应学校。")
     @PostMapping(value = "/getVerifyUserIds", consumes = MediaType.APPLICATION_JSON_VALUE)
     public Object getVerifyUserIds(@RequestBody JSONObject requestJson) {
         LogUtil.loggerRecord("getVerifyUserIds", requestJson);
         if (requestJson == null) {
             throw BaseResponse.parameterInvalid.error("请求参数不能为空");
         }
         JSONObject node = requestJson.getJSONObject("node");
         Integer verifyRoleId = node != null ? node.getInteger("verifyRoleId") : requestJson.getInteger("verifyRoleId");
         Integer createUserId = node != null ? node.getInteger("createUserId") : requestJson.getInteger("createUserId");
         Integer internshipId = node != null ? node.getInteger("internshipId") : requestJson.getInteger("internshipId");
         if (createUserId == null) {
             throw BaseResponse.parameterInvalid.error("createUserId 不能为空");
         }
         // verifyRoleId 允许为 null/0，此时服务方法会按约定返回空字符串
         // internshipId 可选，企业用户等无 schoolId 的用户需传此参数以回落到实习项目所属学校
         String verifyUserIds = iVerifyProcessService.GetVerifyUserId(verifyRoleId, createUserId, internshipId);
         return BaseResponse.ok(verifyUserIds);
     }

     @Operation(
             summary = "获取实习项目可选用户列表",
            description = "根据 internshipId、jobCode（可选 departmentId）查询 viewBaseUser 中尚未在 RelIntershipUser 中关联的用户"
     )
     @PostMapping(value = "/getAvailableUsersForInternship", consumes = MediaType.APPLICATION_JSON_VALUE)
     public Object getAvailableUsersForInternship(@RequestBody JSONObject requestJson) {
         LogUtil.loggerRecord("getAvailableUsersForInternship", requestJson);
         if (requestJson == null) {
             throw BaseResponse.parameterInvalid.error("请求参数不能为空");
         }

         // 仿照 getSomeRecords 的参数形式，从 node.searchKey 或顶层 searchKey 里取值
         JSONObject node = requestJson.getJSONObject("node");
         JSONObject searchKey = node != null ? node.getJSONObject("searchKey") : requestJson.getJSONObject("searchKey");
         if (searchKey == null) {
             throw BaseResponse.parameterInvalid.error("searchKey 不能为空");
         }

        Integer internshipId = searchKey.getInteger("internshipId");
        String jobCode = searchKey.getString("jobCode");
        List<Integer> departmentIds = parseDepartmentIds(searchKey, "departmentId");

         if (internshipId == null) {
             throw BaseResponse.parameterInvalid.error("internshipId 不能为空");
         }
        if (jobCode == null || jobCode.trim().isEmpty()) {
            throw BaseResponse.parameterInvalid.error("jobCode 不能为空");
         }

         // 分页信息：优先从 node.pageInfo 取值
         JSONObject pageInfo = node != null ? node.getJSONObject("pageInfo") : requestJson.getJSONObject("pageInfo");
         int page = pageInfo != null && pageInfo.getInteger("page") != null
                 ? pageInfo.getInteger("page")
                 : Constant.DEFAULT_PAGE;
         int size = pageInfo != null && pageInfo.getInteger("size") != null
                 ? pageInfo.getInteger("size")
                 : Constant.DEFAULT_SIZE;

         // 排序信息：优先从 node.sort 取值
         JSONObject sortJson = node != null ? node.getJSONObject("sort") : requestJson.getJSONObject("sort");
         Sort sort = Sort.unsorted();
         if (sortJson != null) {
             String properties = sortJson.getString("properties");
             String directionStr = sortJson.getString("direction");
             if (properties != null && !properties.trim().isEmpty()) {
                 Sort.Direction direction;
                 try {
                     direction = directionStr != null
                             ? Sort.Direction.fromString(directionStr)
                             : Sort.Direction.ASC;
                 } catch (IllegalArgumentException e) {
                     direction = Sort.Direction.ASC;
                 }

                 // 支持逗号分隔的多字段排序
                 String[] props = properties.split(Constant.SPLIT_OPERATOR.COMMA);
                 sort = Sort.by(direction, props);
             }
         }

         return BaseResponse.ok(
               iInternshipService.getAvailableUsersForInternship(internshipId, jobCode, departmentIds, page, size, sort)
         );
     }

    @Operation(
            summary = "按可选用户口径批量初始化实习用户与审核记录",
            description = "departmentId 传末级部门 id 数组（与树勾选叶子一致）；服务端只按这些 id 做部门 IN，不展开子树、不推断父子。"
                    + "分页拉齐可选用户后批量创建 RelIntershipUser（currentVerifyTypeId 使用传参）与 MainVerifyProcess（isAudit=SAVE，tableName=RelIntershipUser）。"
                    + "verifyUserId 由 verifyRoleId、createUserId、internshipId 自动计算。"
    )
    @PostMapping(value = "/batchInitRelIntershipUserFromAvailable", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object batchInitRelIntershipUserFromAvailable(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("batchInitRelIntershipUserFromAvailable", requestJson);
        if (requestJson == null) {
            throw BaseResponse.parameterInvalid.error("请求参数不能为空");
        }
        JSONObject node = requestJson.getJSONObject("node");
        Integer internshipId = node != null ? node.getInteger("internshipId") : requestJson.getInteger("internshipId");
        String jobCode = node != null ? node.getString("jobCode") : requestJson.getString("jobCode");
        JSONObject source = node != null ? node : requestJson;
        List<Integer> departmentIds = parseDepartmentIds(source, "departmentId");
        Integer currentVerifyTypeId = node != null ? node.getInteger("currentVerifyTypeId") : requestJson.getInteger("currentVerifyTypeId");
        Integer processId = node != null ? node.getInteger("processId") : requestJson.getInteger("processId");
        Integer createUserId = node != null ? node.getInteger("createUserId") : requestJson.getInteger("createUserId");
        Integer verifyRoleId = node != null ? node.getInteger("verifyRoleId") : requestJson.getInteger("verifyRoleId");

        return BaseResponse.ok(iInternshipService.batchInitRelIntershipUserFromAvailable(
                internshipId, jobCode, departmentIds, processId, createUserId, verifyRoleId, currentVerifyTypeId
        ));
    }

    private List<Integer> parseDepartmentIds(JSONObject source, String fieldName) {
        if (source == null || fieldName == null || fieldName.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        Object raw = source.get(fieldName);
        if (raw == null) {
            return java.util.Collections.emptyList();
        }
        if (raw instanceof JSONArray) {
            JSONArray arr = (JSONArray) raw;
            return arr.stream()
                    .map(this::parseToIntegerOrNull)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        }
        if (raw instanceof java.util.Collection<?>) {
            java.util.Collection<?> coll = (java.util.Collection<?>) raw;
            return coll.stream()
                    .map(this::parseToIntegerOrNull)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        }
        if (raw.getClass().isArray()) {
            Object[] arr = (Object[]) raw;
            return Arrays.stream(arr)
                    .map(this::parseToIntegerOrNull)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        }
        if (raw instanceof String) {
            String s = String.valueOf(raw).trim();
            if (s.startsWith("[") && s.endsWith("]")) {
                try {
                    JSONArray arr = JSON.parseArray(s);
                    return arr.stream()
                            .map(this::parseToIntegerOrNull)
                            .filter(Objects::nonNull)
                            .distinct()
                            .collect(Collectors.toList());
                } catch (Exception ignore) {
                    // 继续按单值/逗号串解析
                }
            }
            if (s.contains(Constant.SPLIT_OPERATOR.COMMA)) {
                return Arrays.stream(s.split(Constant.SPLIT_OPERATOR.COMMA))
                        .map(String::trim)
                        .filter(v -> !v.isEmpty())
                        .map(this::parseToIntegerOrNull)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList());
            }
        }
        Integer single = parseToIntegerOrNull(raw);
        if (single != null) {
            return java.util.Collections.singletonList(single);
        }
        throw BaseResponse.parameterInvalid.error(fieldName + " 参数格式错误，应为整数或整数数组，实际类型="
                + raw.getClass().getName() + "，值=" + String.valueOf(raw));
    }

    private Integer parseToIntegerOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Operation(
            summary = "查询可分配老师列表",
            description = "根据 internshipId、departmentId、jobCode 查询当前实习项目下审核已通过的老师。"
                    + "jobCode：SCHOOL_TEACHER（校内导师）或 COMPANY_TUTOR（企业导师）。"
    )
    @PostMapping(value = "/listAssignableTeachers", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object listAssignableTeachers(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("listAssignableTeachers", requestJson);
        if (requestJson == null) {
            throw BaseResponse.parameterInvalid.error("请求参数不能为空");
        }
        JSONObject node = requestJson.getJSONObject("node");
        Integer internshipId = node != null ? node.getInteger("internshipId") : requestJson.getInteger("internshipId");
        Integer departmentId = node != null ? node.getInteger("departmentId") : requestJson.getInteger("departmentId");
        String jobCode = node != null ? node.getString("jobCode") : requestJson.getString("jobCode");
        if (internshipId == null || departmentId == null) {
            throw BaseResponse.parameterInvalid.error("internshipId、departmentId 不能为空");
        }
        if (jobCode == null || jobCode.trim().isEmpty()) {
            throw BaseResponse.parameterInvalid.error("jobCode 不能为空");
        }
        jobCode = jobCode.trim();
        if (!Constant.USER_JOB_CODE.SCHOOL_TEACHER.equals(jobCode)
                && !Constant.USER_JOB_CODE.COMPANY_TUTOR.equals(jobCode)) {
            throw BaseResponse.parameterInvalid.error("jobCode 仅支持 SCHOOL_TEACHER 或 COMPANY_TUTOR");
        }
        return BaseResponse.ok(iInternshipService.listAssignableTeachers(internshipId, departmentId, jobCode));
    }

    @Operation(
            summary = "查询可分配学生列表",
            description = "根据 internshipId 和 departmentId 查询当前实习项目下岗位审核通过且选岗审核通过的学生，口径与系统自动分配一致。"
    )
    @PostMapping(value = "/listAssignableStudents", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object listAssignableStudents(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("listAssignableStudents", requestJson);
        if (requestJson == null) {
            throw BaseResponse.parameterInvalid.error("请求参数不能为空");
        }
        JSONObject node = requestJson.getJSONObject("node");
        Integer internshipId = node != null ? node.getInteger("internshipId") : requestJson.getInteger("internshipId");
        Integer departmentId = node != null ? node.getInteger("departmentId") : requestJson.getInteger("departmentId");
        if (internshipId == null || departmentId == null) {
            throw BaseResponse.parameterInvalid.error("internshipId、departmentId 不能为空");
        }
        return BaseResponse.ok(iInternshipService.listAssignableStudents(internshipId, departmentId));
    }

    @Operation(
            summary = "根据实习项目初始化校内导师分配",
            description = "查询 ViewVerifyProcessRelIntTeacherStudentMerge（processTypeCode=EXTERNAL_ASSIGN_INTERNAL_TUTOR）中该实习项目下"
                    + "待提交（isAudit=SAVE）的师生记录（含已暂存 teacherId 的草稿）；已提交的记录不在查询范围内、不会被改写。"
                    + "从 ViewVerifyProcessRelIntershipUserMerge 取同实习项目、jobCode=SCHOOL_TEACHER、审核通过（PASS）的教师 userId；"
                    + "查 ViewVerifyProcessRelIntTeacherStudentMerge 时不使用 jobCode 条件。"
                    + "在剥离本批 SAVE 行旧分配后的负载上按均衡策略写入 teacherId（再次点击可因新增导师而重算）；"
                    + "若已有 MainVerifyProcess（relationId+processId+RelTeacherStudent），"
                    + "则更新其 createUserId、verifyUserId 为请求传入值。createdVerifyProcessCount 为实际更新的审核行数。"
    )
    @PostMapping(value = "/initTeacherStudentByInternshipId", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object initTeacherStudentByInternshipId(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("initTeacherStudentByInternshipId", requestJson);
        if (requestJson == null) {
            throw BaseResponse.parameterInvalid.error("请求参数不能为空");
        }
        JSONObject node = requestJson.getJSONObject("node");
        Integer internshipId = node != null ? node.getInteger("internshipId") : requestJson.getInteger("internshipId");
        Integer processId = node != null ? node.getInteger("processId") : requestJson.getInteger("processId");
        Integer createUserId = node != null ? node.getInteger("createUserId") : requestJson.getInteger("createUserId");
        String verifyUserId = node != null ? node.getString("verifyUserId") : requestJson.getString("verifyUserId");
        Integer currentVerifyTypeId = node != null ? node.getInteger("currentVerifyTypeId") : requestJson.getInteger("currentVerifyTypeId");
        if (internshipId == null || processId == null || createUserId == null || verifyUserId == null) {
            throw BaseResponse.parameterInvalid.error("internshipId、processId、createUserId、verifyUserId 不能为空");
        }
        return BaseResponse.ok(
                iInternshipService.initTeacherStudentByInternshipId(internshipId, processId, createUserId, verifyUserId,
                        currentVerifyTypeId)
        );
    }

    @Operation(
            summary = "【校内导师】根据实习项目初始化师生关系",
            description = "与 initTeacherStudentByInternshipId 相同。"
    )
    @PostMapping(value = "/initInternalTutorByInternshipId", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object initInternalTutorByInternshipId(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("initInternalTutorByInternshipId", requestJson);
        if (requestJson == null) {
            throw BaseResponse.parameterInvalid.error("请求参数不能为空");
        }
        JSONObject node = requestJson.getJSONObject("node");
        Integer internshipId = node != null ? node.getInteger("internshipId") : requestJson.getInteger("internshipId");
        Integer processId = node != null ? node.getInteger("processId") : requestJson.getInteger("processId");
        Integer createUserId = node != null ? node.getInteger("createUserId") : requestJson.getInteger("createUserId");
        String verifyUserId = node != null ? node.getString("verifyUserId") : requestJson.getString("verifyUserId");
        Integer currentVerifyTypeId = node != null ? node.getInteger("currentVerifyTypeId") : requestJson.getInteger("currentVerifyTypeId");
        if (internshipId == null || processId == null || createUserId == null || verifyUserId == null) {
            throw BaseResponse.parameterInvalid.error("internshipId、processId、createUserId、verifyUserId 不能为空");
        }
        return BaseResponse.ok(
                iInternshipService.initInternalTutorByInternshipId(internshipId, processId, createUserId, verifyUserId, currentVerifyTypeId)
        );
    }

    @Operation(
            summary = "手动分配老师与学生",
            description = "按传入 teacherId 和 studentIds，批量创建 RelTeacherStudent 和 MainVerifyProcess。"
    )
    @PostMapping(value = "/manualAssignTeacherStudent", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object manualAssignTeacherStudent(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("manualAssignTeacherStudent", requestJson);
        if (requestJson == null) {
            throw BaseResponse.parameterInvalid.error("请求参数不能为空");
        }
        JSONObject node = requestJson.getJSONObject("node");
        Integer internshipId = node != null ? node.getInteger("internshipId") : requestJson.getInteger("internshipId");
        Integer processId = node != null ? node.getInteger("processId") : requestJson.getInteger("processId");
        Integer createUserId = node != null ? node.getInteger("createUserId") : requestJson.getInteger("createUserId");
        String verifyUserId = node != null ? node.getString("verifyUserId") : requestJson.getString("verifyUserId");
        Integer currentVerifyTypeId = node != null ? node.getInteger("currentVerifyTypeId") : requestJson.getInteger("currentVerifyTypeId");
        Integer teacherId = node != null ? node.getInteger("teacherId") : requestJson.getInteger("teacherId");
        JSONArray studentIdsArray = node != null ? node.getJSONArray("studentIds") : requestJson.getJSONArray("studentIds");
        List<Integer> studentIds = studentIdsArray == null ? null : studentIdsArray.toJavaList(Integer.class);
        if (internshipId == null || processId == null || createUserId == null || verifyUserId == null) {
            throw BaseResponse.parameterInvalid.error("internshipId、processId、createUserId、verifyUserId 不能为空");
        }
        if (teacherId == null) {
            throw BaseResponse.parameterInvalid.error("teacherId 不能为空");
        }
        if (studentIds == null || studentIds.isEmpty()) {
            throw BaseResponse.parameterInvalid.error("studentIds 不能为空");
        }
        return BaseResponse.ok(
                iInternshipService.manualAssignTeacherStudent(internshipId, processId, createUserId, verifyUserId,
                        currentVerifyTypeId, teacherId, studentIds)
        );
    }

    // @Operation(summary = "获取当前进行中的实习项目", description = "根据流程类型代码查询当前时间范围内的实习项目")
    // @PostMapping(value = "/getNowInternship", consumes = MediaType.APPLICATION_JSON_VALUE)
    // public Object getNowInternship(@RequestBody JSONObject requestJson) {
    //     LogUtil.loggerRecord("getNowInternship", requestJson);
    //     if (requestJson == null) {
    //         throw BaseResponse.parameterInvalid.error("请求参数不能为空");
    //     }
    //     String processTypeCode = requestJson.getString("processTypeCode");
    //     return BaseResponse.ok(iInternshipService.getNowInternship(processTypeCode));
    // }

    @Operation(
            summary = "本学院校外实习项目报名汇总",
            description = "权限：超级管理员/学校管理员/教务处管理员可看全校（不传 departmentId 时按当前用户 schoolId 下全部学院；超级管理员无 schoolId 时看全部学院）；"
                    + "院系管理员仅看本学院，忽略请求中的 departmentId。"
                    + "列表按实习类型所属学院（base_internship_type.university_id）归属。"
                    + "返回各校外实习项目：signupStudentTotalCount（报名学生总数）、signupStudentCount（当前部门子树报名学生数）、"
                    + "报名校内导师数、岗位数、招聘总人数、pendingAuditPostCount（当前部门子树待审岗位数）、"
                    + "studentWithPostSelectionCount（当前部门子树已选岗学生数）等。"
    )
    @PostMapping(value = "/listExternalInternshipCollegeStats", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object listExternalInternshipCollegeStats(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("listExternalInternshipCollegeStats", requestJson);
        if (requestJson == null) {
            throw BaseResponse.parameterInvalid.error("请求参数不能为空");
        }
        JSONObject node = requestJson.getJSONObject("node");
        Integer departmentId = node != null ? node.getInteger("departmentId") : requestJson.getInteger("departmentId");
        JSONObject pageInfo = node != null ? node.getJSONObject("pageInfo") : requestJson.getJSONObject("pageInfo");
        int page = pageInfo != null && pageInfo.getInteger("page") != null
                ? pageInfo.getInteger("page")
                : Constant.DEFAULT_PAGE;
        int size = pageInfo != null && pageInfo.getInteger("size") != null
                ? pageInfo.getInteger("size")
                : Constant.DEFAULT_SIZE;
        return BaseResponse.ok(iInternshipService.listExternalInternshipCollegeStats(departmentId, page, size));
    }

    @Operation(
            summary = "校外实习项目-审核已通过岗位列表",
            description = "指定校外实习项目 internshipId，返回审核已通过（isAudit=PASS）的岗位及公司、招聘人数、岗位类型薪资（salary，来自 BasePostType）等；"
                    + "支持 pageInfo.page / pageInfo.size 分页（对去重后的岗位列表切片）。"
    )
    @PostMapping(value = "/listApprovedExternalInternshipPosts", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object listApprovedExternalInternshipPosts(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("listApprovedExternalInternshipPosts", requestJson);
        if (requestJson == null) {
            throw BaseResponse.parameterInvalid.error("请求参数不能为空");
        }
        JSONObject node = requestJson.getJSONObject("node");
        Integer internshipId = node != null ? node.getInteger("internshipId") : requestJson.getInteger("internshipId");
        JSONObject pageInfo = node != null ? node.getJSONObject("pageInfo") : requestJson.getJSONObject("pageInfo");
        int page = pageInfo != null && pageInfo.getInteger("page") != null
                ? pageInfo.getInteger("page")
                : Constant.DEFAULT_PAGE;
        int size = pageInfo != null && pageInfo.getInteger("size") != null
                ? pageInfo.getInteger("size")
                : Constant.DEFAULT_SIZE;
        return BaseResponse.ok(iInternshipService.listApprovedExternalInternshipPosts(internshipId, page, size));
    }

    @Operation(
            summary = "校外实习项目-学生选岗情况",
            description = "internshipId 必填；status 可选：all（全部学生一条列表，每条带 selectionStatus）、"
                    + "notSelected、selectedPendingAudit、postApproved（仅返回该状态分页 rows）。"
                    + "权限与 listExternalInternshipCollegeStats 一致：院系管理员固定本院子树；校级管理员不传 departmentId 为全校口径，传则下钻该节点子树。"
                    + "rows/counts 仅含当前部门子树内已报名学生。已有选岗记录时 rows 含 verifyProcessId（MainVerifyProcess.id）。"
                    + "分页：pageInfo.page、pageInfo.size。"
    )
    @PostMapping(value = "/getExternalInternshipStudentPostBreakdown", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object getExternalInternshipStudentPostBreakdown(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("getExternalInternshipStudentPostBreakdown", requestJson);
        if (requestJson == null) {
            throw BaseResponse.parameterInvalid.error("请求参数不能为空");
        }
        JSONObject node = requestJson.getJSONObject("node");
        Integer internshipId = node != null ? node.getInteger("internshipId") : requestJson.getInteger("internshipId");
        String status = node != null ? node.getString("status") : requestJson.getString("status");
        Integer departmentId = node != null ? node.getInteger("departmentId") : requestJson.getInteger("departmentId");
        JSONObject pageInfo = node != null ? node.getJSONObject("pageInfo") : requestJson.getJSONObject("pageInfo");
        int page = pageInfo != null && pageInfo.getInteger("page") != null
                ? pageInfo.getInteger("page")
                : Constant.DEFAULT_PAGE;
        int size = pageInfo != null && pageInfo.getInteger("size") != null
                ? pageInfo.getInteger("size")
                : Constant.DEFAULT_SIZE;
        return BaseResponse.ok(iInternshipService.getExternalInternshipStudentPostBreakdown(internshipId, page, size, status,
                departmentId));
    }

    @Operation(
            summary = "未选岗学生随机分配岗位",
            description = "根据 internshipId 取未选岗学生（与 getExternalInternshipStudentPostBreakdown 的 notSelected 口径一致），"
                    + "在 listApprovedExternalInternshipPosts 岗位池内随机分配，内部调用 stuSelPost(studentId,0,postId)。"
    )
    @PostMapping(value = "/randomAssignPostsForUnselectedStudents", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object randomAssignPostsForUnselectedStudents(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("randomAssignPostsForUnselectedStudents", requestJson);
        if (requestJson == null) {
            throw BaseResponse.parameterInvalid.error("请求参数不能为空");
        }
        JSONObject node = requestJson.getJSONObject("node");
        Integer internshipId = node != null ? node.getInteger("internshipId") : requestJson.getInteger("internshipId");
        if (internshipId == null) {
            throw BaseResponse.parameterInvalid.error("internshipId 不能为空");
        }
        return BaseResponse.ok(iInternshipService.randomAssignPostsForUnselectedStudents(internshipId));
    }

    @Operation(
            summary = "本学院校内实习项目报名与选题汇总",
            description = "权限：超级管理员/学校管理员/教务处管理员可看全校（不传 departmentId 时按 schoolId 下全部部门口径统计；超级管理员无 schoolId 时按全部部门）；"
                    + "院系管理员仅看本部门子树，忽略请求中的 departmentId。"
                    + "departmentId 对校级管理员可选，用于下钻某一学院子树；分页 ViewMainInternship（校内实习）。每行含：报名学生/老师数、题目审核通过数、"
                    + "未提交题目教师数、学生选题通过/审核中/尚未选题人数（本院已报名学生口径）。"
    )
    @PostMapping(value = "/listInternalInternshipCollegeStats", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object listInternalInternshipCollegeStats(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("listInternalInternshipCollegeStats", requestJson);
        if (requestJson == null) {
            throw BaseResponse.parameterInvalid.error("请求参数不能为空");
        }
        JSONObject node = requestJson.getJSONObject("node");
        Integer departmentId = node != null ? node.getInteger("departmentId") : requestJson.getInteger("departmentId");
        JSONObject pageInfo = node != null ? node.getJSONObject("pageInfo") : requestJson.getJSONObject("pageInfo");
        int page = pageInfo != null && pageInfo.getInteger("page") != null
                ? pageInfo.getInteger("page")
                : Constant.DEFAULT_PAGE;
        int size = pageInfo != null && pageInfo.getInteger("size") != null
                ? pageInfo.getInteger("size")
                : Constant.DEFAULT_SIZE;
        return BaseResponse.ok(iInternshipService.listInternalInternshipCollegeStats(departmentId, page, size));
    }

    @Operation(
            summary = "校内实习项目-学生选题情况",
            description = "internshipId 必填；status：all / notSubmitted / pendingAudit / titleApproved。"
                    + "departmentId 与学院汇总下钻一致（与 listInternalInternshipCollegeStats 同一节点）；不传则按登录角色全校/本院口径。"
                    + "counts 为当前部门范围内三类人数；分页 pageInfo。"
    )
    @PostMapping(value = "/getInternalInternshipTitleSelectionBreakdown", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object getInternalInternshipTitleSelectionBreakdown(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("getInternalInternshipTitleSelectionBreakdown", requestJson);
        if (requestJson == null) {
            throw BaseResponse.parameterInvalid.error("请求参数不能为空");
        }
        JSONObject node = requestJson.getJSONObject("node");
        Integer internshipId = node != null ? node.getInteger("internshipId") : requestJson.getInteger("internshipId");
        String status = node != null ? node.getString("status") : requestJson.getString("status");
        Integer departmentId = node != null ? node.getInteger("departmentId") : requestJson.getInteger("departmentId");
        JSONObject pageInfo = node != null ? node.getJSONObject("pageInfo") : requestJson.getJSONObject("pageInfo");
        int page = pageInfo != null && pageInfo.getInteger("page") != null
                ? pageInfo.getInteger("page")
                : Constant.DEFAULT_PAGE;
        int size = pageInfo != null && pageInfo.getInteger("size") != null
                ? pageInfo.getInteger("size")
                : Constant.DEFAULT_SIZE;
        return BaseResponse.ok(iInternshipService.getInternalInternshipTitleSelectionBreakdown(internshipId, page, size, status,
                departmentId));
    }

    @Operation(
            summary = "校内实习项目-未提交申报题目的教师",
            description = "internshipId 必填；departmentId 与学院汇总/选题详情下钻一致（含子部门内教师；权限同 listInternalInternshipCollegeStats）。"
                    + "未提交：无任何 isAudit≠保存(-1) 的申报题目审核记录。分页 pageInfo。"
    )
    @PostMapping(value = "/listInternalInternshipTeachersNotSubmittedTopic", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object listInternalInternshipTeachersNotSubmittedTopic(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("listInternalInternshipTeachersNotSubmittedTopic", requestJson);
        if (requestJson == null) {
            throw BaseResponse.parameterInvalid.error("请求参数不能为空");
        }
        JSONObject node = requestJson.getJSONObject("node");
        Integer internshipId = node != null ? node.getInteger("internshipId") : requestJson.getInteger("internshipId");
        Integer departmentId = node != null ? node.getInteger("departmentId") : requestJson.getInteger("departmentId");
        JSONObject pageInfo = node != null ? node.getJSONObject("pageInfo") : requestJson.getJSONObject("pageInfo");
        int page = pageInfo != null && pageInfo.getInteger("page") != null
                ? pageInfo.getInteger("page")
                : Constant.DEFAULT_PAGE;
        int size = pageInfo != null && pageInfo.getInteger("size") != null
                ? pageInfo.getInteger("size")
                : Constant.DEFAULT_SIZE;
        return BaseResponse.ok(iInternshipService.listInternalInternshipTeachersNotSubmittedTopic(internshipId, departmentId, page, size));
    }

}
