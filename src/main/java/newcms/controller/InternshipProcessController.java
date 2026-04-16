package newcms.controller;

import com.alibaba.fastjson.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import newcms.annotation.PathRestController;
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


    @PostMapping(value = "/auditProcess", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object auditProcess(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("auditProcess", requestJson);
        Object node = requestJson.get("node");
        if (node == null) {
            throw BaseResponse.parameterInvalid.error("node 不能为空");
        }
        return BaseResponse.ok(iInternshipService.auditProcess(node));
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
        Integer departmentId = searchKey.getInteger("departmentId");

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
                iInternshipService.getAvailableUsersForInternship(internshipId, jobCode, departmentId, page, size, sort)
         );
     }

    @Operation(
            summary = "根据实习项目初始化师生关系和审核记录",
            description = "按 internshipId 关联岗位和学生选择记录，创建 RelTeacherStudent，并同步创建 MainVerifyProcess。"
                    + "tutorAssignKind：1=校内导师（自动均衡分配 teacherId，支持待审核重分配）；"
                    + "2=企业导师（teacherId 留空，后续手动分配）。缺省为 1。"
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
        Integer tutorAssignKind = node != null ? node.getInteger("tutorAssignKind") : requestJson.getInteger("tutorAssignKind");
        Integer currentVerifyTypeId = node != null ? node.getInteger("currentVerifyTypeId") : requestJson.getInteger("currentVerifyTypeId");
        if (internshipId == null || processId == null || createUserId == null || verifyUserId == null) {
            throw BaseResponse.parameterInvalid.error("internshipId、processId、createUserId、verifyUserId 不能为空");
        }
        return BaseResponse.ok(
                iInternshipService.initTeacherStudentByInternshipId(internshipId, processId, createUserId, verifyUserId,
                        tutorAssignKind, currentVerifyTypeId)
        );
    }

    @Operation(
            summary = "【校内导师】根据实习项目初始化师生关系和审核记录",
            description = "校内导师初始化：支持待审核重分配 + 新增学生增量补建（teacherId 自动均衡分配）。"
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
            summary = "【企业导师】根据实习项目初始化师生关系和审核记录",
            description = "企业导师初始化：可反复调用，每次自动识别新增学生并增量补建（teacherId=0 占位，后续手动分配）。"
    )
    @PostMapping(value = "/initEnterpriseTutorByInternshipId", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object initEnterpriseTutorByInternshipId(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("initEnterpriseTutorByInternshipId", requestJson);
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
                iInternshipService.initEnterpriseTutorByInternshipId(internshipId, processId, createUserId, verifyUserId, currentVerifyTypeId)
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
            description = "按学院部门树（departmentId 含其全部子部门）统计各校外实习项目：报名学生数、报名校内导师数、岗位数、招聘总人数、待审核岗位数、已选岗学生数等。"
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
                    + "departmentId 可选：传则只含所属部门为该节点或其下级（BaseDepartment 子树）的用户；不传则与原先一致，不按部门过滤。"
                    + "counts 为当前过滤范围内三类人数。分页：pageInfo.page、pageInfo.size。"
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
            summary = "本学院校内实习项目报名与选题汇总",
            description = "departmentId 必填；分页 ViewMainInternship（校内实习）。每行含：报名学生/老师数、题目审核通过数、"
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
                    + "counts 为三类全量人数；分页 pageInfo。"
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
        JSONObject pageInfo = node != null ? node.getJSONObject("pageInfo") : requestJson.getJSONObject("pageInfo");
        int page = pageInfo != null && pageInfo.getInteger("page") != null
                ? pageInfo.getInteger("page")
                : Constant.DEFAULT_PAGE;
        int size = pageInfo != null && pageInfo.getInteger("size") != null
                ? pageInfo.getInteger("size")
                : Constant.DEFAULT_SIZE;
        return BaseResponse.ok(iInternshipService.getInternalInternshipTitleSelectionBreakdown(internshipId, page, size, status));
    }

    @Operation(
            summary = "校内实习项目-未提交申报题目的教师",
            description = "internshipId 必填；departmentId 可选（不传则本项目全部报名教师中未提交者）。"
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
