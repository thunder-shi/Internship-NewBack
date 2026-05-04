package newcms.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.base.Constant;
import newcms.entity.db.RelTitleStudent;
import newcms.entity.db.RelTitleTeacher;
import newcms.entity.db.ViewBaseUser;
import newcms.repository.db.MainInternshipPostDao;
import newcms.repository.db.RelTitleStudentDao;
import newcms.repository.db.RelTitleTeacherDao;
import newcms.repository.db.ViewExternalInternshipCollegeStatsDao;
import newcms.service.ICommonService;
import newcms.service.IDataTreeService;
import newcms.service.IInternshipService;
import newcms.service.IVerifyProcessService;
import newcms.utils.FastJsonUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class InternshipServiceImpl extends Base implements IInternshipService {
    private static final String TEACHER_JOB_CODE = Constant.USER_JOB_CODE.SCHOOL_TEACHER;
    private static final String ENTERPRISE_TUTOR_JOB_CODE = Constant.USER_JOB_CODE.COMPANY_TUTOR;
    private static final int LARGE_PAGE_SIZE = 100000;
    private static final int POST_PAGE_SIZE = 10000;
    /** 与库中 base_int_type / view_main_internship.int_type_name 一致，用于识别校外实习 */
    private static final String EXTERNAL_INT_TYPE_NAME = "校外实习";
    /** 与库中 view_main_internship.int_type_name 一致，用于识别校内实习 */
    private static final String INTERNAL_INT_TYPE_NAME = "校内实习";
    private static final String STU_POST_STATUS_ALL = "all";
    private static final String STU_POST_STATUS_NOT_SELECTED = "notSelected";
    private static final String STU_POST_STATUS_SELECTED_PENDING = "selectedPendingAudit";
    private static final String STU_POST_STATUS_POST_APPROVED = "postApproved";

    private static final String TITLE_SEL_STATUS_ALL = "all";
    private static final String TITLE_SEL_STATUS_NOT_SUBMITTED = "notSubmitted";
    private static final String TITLE_SEL_STATUS_PENDING = "pendingAudit";
    private static final String TITLE_SEL_STATUS_APPROVED = "titleApproved";
    private static final String TITLE_SOURCE_STUDENT_CANDIDATE = "STUDENT_CANDIDATE";
    private static final String TITLE_SOURCE_TEACHER_ASSIGN = "TEACHER_ASSIGN";
    private static final ConcurrentHashMap<String, Object> TITLE_STUDENT_LOCKS = new ConcurrentHashMap<>();

    @Resource
    private ICommonService iCommonService;

    @Resource
    private IVerifyProcessService iVerifyProcessService;

    @Resource
    private ViewExternalInternshipCollegeStatsDao viewExternalInternshipCollegeStatsDao;

    @Resource
    private IDataTreeService iDataTreeService;

    @Resource
    private MainInternshipPostDao mainInternshipPostDao;

    @Resource
    private RelTitleStudentDao relTitleStudentDao;

    @Resource
    private RelTitleTeacherDao relTitleTeacherDao;

    // ==================== 实习项目管理====================

    @Override
    public Object addNewInternship(JSONObject node) {
        // (1) 在 MainInternship 实体增加一条记录
        Object savedInternship = iCommonService.saveOneRecord("MainInternship", node);
        JSONObject savedInternshipJson = FastJsonUtil.toJson(savedInternship);
        Integer internshipId = savedInternshipJson.getInteger("id");
        Integer internshipTypeId = savedInternshipJson.getInteger("internshipTypeId");
        // 参数校验
        if (internshipId == null || internshipTypeId == null) {
            throw BaseResponse.moreInfoError.error("实习项目ID或实习类型ID不能为空");
        }
        // (2) 创建专业范围关联记录
        createInternshipMajorRecords(internshipId, internshipTypeId);
        // (3) 创建流程关联记录
        createProcessInternshipRecords(internshipId, internshipTypeId);
        // (4) 根据流程配置设置 MainInternship 的初始 currentVerifyTypeId
        initInternshipVerifyLevel(internshipId);
        // (5) 创建"实习计划制定"流程的第一条 MainVerifyProcess 记录
        createFirstVerifyProcessRecord(internshipId);
        return savedInternship;
    }



    /**
     * 创建实习项目的流程关联记录
     * @param internshipId 实习项目ID
     * @param internshipTypeId 实习类型ID
     */
    private void createProcessInternshipRecords(Integer internshipId, Integer internshipTypeId) {
        // (1) 查找 RelProcessInternshipType 所有 internshipTypeId 和新增实体的 internshipTypeId 值相等的记录
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("internshipTypeId", internshipTypeId);
        @SuppressWarnings("unchecked")
        Page<Object> processTypePage = (Page<Object>) iCommonService.getSomeRecords("ViewRelProcessInternshipType", searchKeys, null, Sort.unsorted());
        List<Object> processTypeList = processTypePage.getContent();
        // (2) 在 RelProcessInternship 实体中增加若干条记录
        List<Object> processInternshipList = new ArrayList<>();
        for (Object processTypeObj : processTypeList) {
            JSONObject processTypeJson = FastJsonUtil.toJson(processTypeObj);
            JSONObject processInternshipJson = new JSONObject();
            // internshipId 是当前实习项目的id
            processInternshipJson.put("internshipId", internshipId);
            // 其他属性直接带入 RelProcessInternshipType 中的对应值
            processInternshipJson.put("processTypeId", processTypeJson.getInteger("processTypeId"));
            processInternshipJson.put("verifyTypeId", processTypeJson.getInteger("verifyTypeId"));
            processInternshipJson.put("verifyFirstRoleId", processTypeJson.getInteger("verifyFirstRoleId"));
            processInternshipJson.put("verifySecondRoleId", processTypeJson.getInteger("verifySecondRoleId"));
            processInternshipJson.put("verifyThirdRoleId", processTypeJson.getInteger("verifyThirdRoleId"));
            processInternshipJson.put("verifyFourthRoleId", processTypeJson.getInteger("verifyFourthRoleId"));
            processInternshipJson.put("verifyFifthRoleId", processTypeJson.getInteger("verifyFifthRoleId"));
            processInternshipList.add(processInternshipJson);
        }
        // 批量保存 RelProcessInternship 记录
        if (!processInternshipList.isEmpty()) {
            iCommonService.saveSomeRecords("RelProcessInternship", processInternshipList);
        }
    }

    /**
     * 根据流程配置设置 MainInternship 的初始 currentVerifyTypeId
     * @param internshipId 实习项目ID
     */
    private void initInternshipVerifyLevel(Integer internshipId) {
        Object foundProcess = iVerifyProcessService.GetInternshipProcess(internshipId, null);
        JSONObject relJson = FastJsonUtil.toJson(foundProcess);
        String verifyTypeCode = relJson.getString("verifyTypeCode");
        int initialLevel;
        if ("NO_VERIFY".equals(verifyTypeCode)) {
            initialLevel = Constant.VERIFY_LEVEL.NO_VERIFY;
        } else {
            initialLevel = Constant.VERIFY_LEVEL.ONE_VERIFY;
        }
        JSONObject updateJson = new JSONObject();
        updateJson.put("id", internshipId);
        updateJson.put("currentVerifyTypeId", initialLevel);
        iCommonService.saveOneRecord("MainInternship", updateJson);
    }

    /**
     * 创建"实习计划制定"流程的第一条 MainVerifyProcess 记录
     * @param internshipId 实习项目ID
     */
    private void createFirstVerifyProcessRecord(Integer internshipId) {
        // 1. 查找流程关联记录（取第一条，对应计划制定流程）
        Object foundProcess = iVerifyProcessService.GetInternshipProcess(internshipId, null);
        JSONObject relJson = FastJsonUtil.toJson(foundProcess);
        Integer relProcessId = relJson.getInteger("id");
        Integer verifyFirstRoleId = relJson.getInteger("verifyFirstRoleId");
        // 2. 从 MainInternship 获取 createUserId 和 currentVerifyTypeId
        Object internshipObj = iCommonService.getOneRecordById("MainInternship", internshipId);
        if (internshipObj == null) {
            throw BaseResponse.moreInfoError.error("未找到实习项目记录");
        }
        JSONObject internshipJson = FastJsonUtil.toJson(internshipObj);
        Integer createUserId = internshipJson.getInteger("creatorId");
        if (createUserId == null) {
            throw BaseResponse.parameterInvalid.error("creatorId 参数不能为空");
        }
        Integer currentVerifyTypeId = internshipJson.getInteger("currentVerifyTypeId");

        // 判断是否无需审核（currentVerifyTypeId == 1 即 NO_VERIFY）
        boolean noVerify = currentVerifyTypeId != null
                && currentVerifyTypeId == Constant.VERIFY_LEVEL.NO_VERIFY;

        String verifyUserId;
        int isAudit;
        if (noVerify) {
            // 无需审核：直接标记为通过
            verifyUserId = "系统自动通过";
            isAudit = 1;
        } else {
            // 需要审核：获取审核用户ID字符串，状态为未提交
            verifyUserId = iVerifyProcessService.GetVerifyUserId(verifyFirstRoleId, createUserId, internshipId);
            isAudit = -1;
        }

        // 创建审核记录
        JSONObject verifyJson = new JSONObject();
        verifyJson.put("relationId", internshipId);
        verifyJson.put("processId", relProcessId);
        verifyJson.put("createUserId", createUserId);
        verifyJson.put("isAudit", isAudit);
        verifyJson.put("reason", "");
        verifyJson.put("tableName", "MainInternship");
        verifyJson.put("verifyUserId", verifyUserId);
        iCommonService.saveOneRecord("MainVerifyProcess", verifyJson);
    }

    /**
     * 创建实习项目的专业范围关联记录
     * @param internshipId 实习项目ID
     * @param internshipTypeId 实习类型ID
     */
    private void createInternshipMajorRecords(Integer internshipId, Integer internshipTypeId) {
        // (1) 查找当前新增实习项目的模板 internshipTypeId 在 RelInterTypeMajor 中所有 internshipTypeId 相等的记录，得到它们的 majorId
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("internshipTypeId", internshipTypeId);
        @SuppressWarnings("unchecked")
        Page<Object> interTypeMajorPage = (Page<Object>) iCommonService.getSomeRecords("RelInterTypeMajor", searchKeys, null, Sort.unsorted());
        List<Object> interTypeMajorList = interTypeMajorPage.getContent();

        // (2) 在 RelInterMajor 实体中创建数量相同的若干记录
        List<Object> interMajorList = new ArrayList<>();
        for (Object interTypeMajorObj : interTypeMajorList) {
            JSONObject interTypeMajorJson = FastJsonUtil.toJson(interTypeMajorObj);
            JSONObject interMajorJson = new JSONObject();
            
            // internshipId 都是当前的 internshipId
            interMajorJson.put("internshipId", internshipId);
            // majorId 就是查找到的 majorId
            interMajorJson.put("majorId", interTypeMajorJson.getInteger("majorId"));
            interMajorList.add(interMajorJson);
        }
        // 批量保存 RelInterMajor 记录
        if (!interMajorList.isEmpty()) {
            iCommonService.saveSomeRecords("RelInterMajor", interMajorList);
        }
    }

    @Override
    public Object getAvailableUsersForInternship(Integer internshipId, String jobCode, List<Integer> departmentIds, Integer page, Integer size, Sort sort) {
        if (internshipId == null || jobCode == null || jobCode.trim().isEmpty()) {
            throw BaseResponse.parameterInvalid.error("internshipId 和 jobCode 不能为空");
        }

        int pageNum = (page == null || page < 1) ? Constant.DEFAULT_PAGE : page;
        int pageSize = (size == null || size < 1) ? Constant.DEFAULT_SIZE : size;
        if (departmentIds == null || departmentIds.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), PageRequest.of(pageNum - 1, pageSize), 0);
        }

        // 1. 查出该实习项目下已经在 RelIntershipUser 中关联过的 userId 列表（只看未删除的关联）
        JSONObject relSearchKeys = new JSONObject();
        relSearchKeys.put("internshipId", internshipId);
        relSearchKeys.put("isDeleted", 0);

        @SuppressWarnings("unchecked")
        Page<Object> relPage = (Page<Object>) iCommonService.getSomeRecords(
                "RelIntershipUser", relSearchKeys, null, Sort.unsorted()
        );

        List<Object> relList = relPage.getContent();
        Set<Integer> usedUserIdSet = relList.stream()
                .map(FastJsonUtil::toJson)
                .map(json -> json.getInteger("userId"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 2. 组装 ViewBaseUser 的查询条件：
        //    - jobCode = 前端传入 jobCode
        //    - departmentId IN (传入部门及其全部子部门)
        //    - id NOT IN (已关联且未删除的 userId 列表)
        JSONObject userSearchKeys = new JSONObject();
        userSearchKeys.put("jobCode", jobCode);

        Map<String, String> repMap = new HashMap<>();
        Set<Integer> allowedDepartmentIds = new HashSet<>();
        for (Integer departmentId : departmentIds) {
            if (departmentId == null) {
                continue;
            }
            allowedDepartmentIds.addAll(collectDepartmentIdsInSubtree(departmentId));
        }
        if (allowedDepartmentIds.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), PageRequest.of(pageNum - 1, pageSize), 0);
        }
        if (allowedDepartmentIds.size() == 1) {
            userSearchKeys.put("departmentId", allowedDepartmentIds.iterator().next());
        } else {
            String departmentIdStr = allowedDepartmentIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(Constant.SPLIT_OPERATOR.COMMA));
            userSearchKeys.put("departmentId", departmentIdStr);
            repMap.put("departmentId", Constant.IN);
        }

        if (!usedUserIdSet.isEmpty()) {
            String idStr = usedUserIdSet.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(Constant.SPLIT_OPERATOR.COMMA));
            userSearchKeys.put("id", idStr);
            // 使用 Constant.NOT_IN 实现 “id NOT IN (...)”
            repMap.put("id", Constant.NOT_IN);
        }

        // 3. 通过通用的 getSomeRecords 分页查询 ViewBaseUser（带排序）
        Sort finalSort = (sort == null) ? Sort.unsorted() : sort;
        return iCommonService.getSomeRecords(
                "ViewBaseUser",
                userSearchKeys,
                repMap,
                finalSort,
                pageNum,
                pageSize);
    }

    @Override
    public Object batchInitRelIntershipUserFromAvailable(Integer internshipId, String jobCode, List<Integer> departmentIds,
                                                         Integer processId, Integer createUserId, Integer verifyRoleId,
                                                         Integer currentVerifyTypeId) {
        if (internshipId == null || processId == null || createUserId == null) {
            throw BaseResponse.parameterInvalid.error("internshipId、processId、createUserId 不能为空");
        }
        if (jobCode == null || jobCode.trim().isEmpty()) {
            throw BaseResponse.parameterInvalid.error("jobCode 不能为空");
        }
        if (departmentIds == null || departmentIds.isEmpty()) {
            throw BaseResponse.parameterInvalid.error("departmentIds 不能为空");
        }
        int verifyType = (currentVerifyTypeId == null ? 1 : currentVerifyTypeId);
        if (verifyType <= 0) {
            throw BaseResponse.parameterInvalid.error("currentVerifyTypeId 无效，必须为正整数");
        }

        String verifyUserId = iVerifyProcessService.GetVerifyUserId(verifyRoleId, createUserId, internshipId);
        if (verifyUserId == null) {
            verifyUserId = "";
        }

        List<Integer> candidateUserIds = new ArrayList<>();
        int pageNum = 1;
        while (true) {
            Object pageObj = getAvailableUsersForInternship(
                    internshipId, jobCode, departmentIds, pageNum, LARGE_PAGE_SIZE, Sort.by(Sort.Direction.ASC, "id"));
            if (!(pageObj instanceof Page)) {
                throw BaseResponse.moreInfoError.error("查询可选用户结果异常");
            }
            Page<?> userPage = (Page<?>) pageObj;
            List<?> rows = userPage.getContent();
            if (rows == null || rows.isEmpty()) {
                break;
            }
            for (Object rowObj : rows) {
                JSONObject row = FastJsonUtil.toJson(rowObj);
                Integer userId = row.getInteger("id");
                if (userId != null) {
                    candidateUserIds.add(userId);
                }
            }
            if (pageNum >= userPage.getTotalPages()) {
                break;
            }
            pageNum++;
        }

        int createdRelIntershipUserCount = 0;
        int createdVerifyProcessCount = 0;
        for (Integer userId : candidateUserIds) {
            if (userId == null) {
                continue;
            }
            JSONObject relIntershipUserJson = new JSONObject();
            relIntershipUserJson.put("internshipId", internshipId);
            relIntershipUserJson.put("userId", userId);
            relIntershipUserJson.put("currentVerifyTypeId", verifyType);
            Object savedRelIntershipUser = iCommonService.saveOneRecord("RelIntershipUser", relIntershipUserJson);
            Integer relationId = FastJsonUtil.toJson(savedRelIntershipUser).getInteger("id");
            if (relationId == null) {
                continue;
            }
            createdRelIntershipUserCount++;

            JSONObject verifyJson = new JSONObject();
            verifyJson.put("relationId", relationId);
            verifyJson.put("processId", processId);
            verifyJson.put("createUserId", createUserId);
            verifyJson.put("verifyUserId", verifyUserId);
            verifyJson.put("isAudit", Constant.AUDIT_STATUS.SAVE);
            verifyJson.put("reason", "");
            verifyJson.put("tableName", "RelIntershipUser");
            iCommonService.saveOneRecord("MainVerifyProcess", verifyJson);
            createdVerifyProcessCount++;
        }

        JSONObject result = new JSONObject();
        result.put("createdRelIntershipUserCount", createdRelIntershipUserCount);
        result.put("createdVerifyProcessCount", createdVerifyProcessCount);
        result.put("totalCandidateCount", candidateUserIds.size());
        result.put("verifyUserId", verifyUserId);
        return result;
    }

    @Override
    public Object listAssignableTeachers(Integer internshipId, Integer departmentId) {
        JSONObject teacherSearchKeys = new JSONObject();
        teacherSearchKeys.put("internshipId", internshipId);
        teacherSearchKeys.put("jobCode", TEACHER_JOB_CODE);
        teacherSearchKeys.put("isAudit", Constant.AUDIT_STATUS.PASS);
        @SuppressWarnings("unchecked")
        Page<Object> teacherPage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewVerifyProcessRelIntershipUserMerge", teacherSearchKeys, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);

        JSONArray rows = new JSONArray();
        Set<Integer> seenUserIds = new HashSet<>();
        List<Object> content = teacherPage.getContent();
        if (content == null) {
            content = Collections.emptyList();
        }
        for (Object obj : content) {
            JSONObject j = FastJsonUtil.toJson(obj);
            Integer userId = j.getInteger("userId");
            Integer rowDeptId = j.getInteger("departmentId");
            if (userId == null || !seenUserIds.add(userId)) {
                continue;
            }
            if (departmentId != null && !Objects.equals(rowDeptId, departmentId)) {
                continue;
            }
            JSONObject row = new JSONObject();
            row.put("userId", userId);
            row.put("userName", firstNonEmpty(j.getString("userName"), j.getString("name")));
            row.put("departmentId", rowDeptId);
            row.put("jobCode", j.getString("jobCode"));
            row.put("jobName", j.getString("jobName"));
            row.put("phone", j.getString("phone"));
            row.put("account", j.getString("account"));
            row.put("relIntershipUserId", j.getInteger("relIntershipUserId"));
            row.put("currentVerifyTypeId", j.getInteger("currentVerifyTypeId"));
            row.put("isAudit", j.getInteger("isAudit"));
            rows.add(row);
        }
        JSONObject result = new JSONObject();
        result.put("rows", rows);
        result.put("total", rows.size());
        return result;
    }

    @Override
    public Object listAssignableStudents(Integer internshipId, Integer departmentId) {
        List<Object> relStuList;
        try {
            relStuList = getStudentInternshipSelections(internshipId);
        } catch (RuntimeException e) {
            JSONObject emptyResult = new JSONObject();
            emptyResult.put("rows", new JSONArray());
            emptyResult.put("total", 0);
            return emptyResult;
        }
        Set<Integer> existingAssignedRelInternshipIds = loadTeacherStudentMergeRelInternshipIds(internshipId);
        Map<Integer, JSONObject> mergeSample = new LinkedHashMap<>();
        List<Integer> studentIds = new ArrayList<>();
        for (Object obj : relStuList) {
            JSONObject j = FastJsonUtil.toJson(obj);
            Integer userId = parseStudentUserIdFromStuPostMerge(j);
            Integer relInternshipId = j.getInteger("relationId");
            if (relInternshipId == null) {
                relInternshipId = j.getInteger("id");
            }
            Integer rowDeptId = j.getInteger("departmentId");
            if (userId == null || mergeSample.containsKey(userId)) {
                continue;
            }
            if (relInternshipId != null && existingAssignedRelInternshipIds.contains(relInternshipId)) {
                continue;
            }
            if (departmentId != null && !Objects.equals(rowDeptId, departmentId)) {
                continue;
            }
            mergeSample.put(userId, j);
            studentIds.add(userId);
        }
        JSONObject result = new JSONObject();
        result.put("rows", buildStudentBriefList(studentIds, mergeSample));
        result.put("total", studentIds.size());
        return result;
    }

    @Override
    public Object manualAssignTeacherStudent(Integer internshipId, Integer processId, Integer createUserId,
                                             String verifyUserId, Integer currentVerifyTypeId, Integer teacherId,
                                             List<Integer> studentIds) {
        validateInitTeacherStudentParams(internshipId, processId, createUserId, verifyUserId);
        if (teacherId == null) {
            throw BaseResponse.parameterInvalid.error("teacherId 不能为空");
        }
        if (studentIds == null || studentIds.isEmpty()) {
            throw BaseResponse.parameterInvalid.error("studentIds 不能为空");
        }
        int verifyType = (currentVerifyTypeId == null ? 1 : currentVerifyTypeId);
        if (verifyType <= 0) {
            throw BaseResponse.parameterInvalid.error("currentVerifyTypeId 无效，必须为正整数");
        }

        List<Object> relStuList = getStudentInternshipSelections(internshipId);
        Map<Integer, Integer> studentRelInternshipMap = new HashMap<>();
        for (Object relStuObj : relStuList) {
            JSONObject relStuJson = FastJsonUtil.toJson(relStuObj);
            Integer studentId = parseStudentUserIdFromStuPostMerge(relStuJson);
            Integer relInternshipId = relStuJson.getInteger("relationId");
            if (relInternshipId == null) {
                relInternshipId = relStuJson.getInteger("id");
            }
            if (studentId != null && relInternshipId != null) {
                studentRelInternshipMap.put(studentId, relInternshipId);
            }
        }

        Set<Integer> existingRelInternshipIds = getRelTeacherStudentRecords(internshipId).stream()
                .filter(Objects::nonNull)
                .map(FastJsonUtil::toJson)
                .map(json -> json.getInteger("relInternshipId"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        int createdRelTeacherStudentCount = 0;
        int createdVerifyProcessCount = 0;
        int skippedExistingCount = 0;

        for (Integer studentId : studentIds) {
            if (studentId == null) {
                continue;
            }

            Integer relInternshipId = studentRelInternshipMap.get(studentId);
            if (relInternshipId == null) {
                throw BaseResponse.moreInfoError.error("studentId=" + studentId + " 未找到对应的通过学生选岗记录");
            }
            if (existingRelInternshipIds.contains(relInternshipId)) {
                skippedExistingCount++;
                continue;
            }

            JSONObject relTeacherStudentJson = new JSONObject();
            relTeacherStudentJson.put("teacherId", teacherId);
            relTeacherStudentJson.put("currentVerifyTypeId", verifyType);
            relTeacherStudentJson.put("relInternshipId", relInternshipId);
            relTeacherStudentJson.put("internshipId", internshipId);
            Object savedRelTeacherStudent = iCommonService.saveOneRecord("RelTeacherStudent", relTeacherStudentJson);
            JSONObject savedRelTeacherStudentJson = FastJsonUtil.toJson(savedRelTeacherStudent);
            Integer relationId = savedRelTeacherStudentJson.getInteger("id");
            if (relationId == null) {
                continue;
            }
            createdRelTeacherStudentCount++;
            existingRelInternshipIds.add(relInternshipId);

            JSONObject verifyJson = new JSONObject();
            verifyJson.put("relationId", relationId);
            verifyJson.put("processId", processId);
            verifyJson.put("createUserId", createUserId);
            verifyJson.put("verifyUserId", verifyUserId);
            verifyJson.put("isAudit", Constant.AUDIT_STATUS.SAVE);
            verifyJson.put("reason", "");
            verifyJson.put("tableName", "RelTeacherStudent");
            iCommonService.saveOneRecord("MainVerifyProcess", verifyJson);
            createdVerifyProcessCount++;
        }

        JSONObject result = buildInitTeacherStudentResult(0, createdRelTeacherStudentCount, createdVerifyProcessCount);
        result.put("skippedExistingCount", skippedExistingCount);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Set<Integer> loadTeacherStudentMergeRelInternshipIds(Integer internshipId) {
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("internshipId", internshipId);
        Page<Object> mergePage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewVerifyProcessRelTeacherStudentMerge", searchKeys, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> rows = mergePage.getContent();
        if (rows == null || rows.isEmpty()) {
            return new HashSet<>();
        }
        return rows.stream()
                .filter(Objects::nonNull)
                .map(FastJsonUtil::toJson)
                .map(json -> json.getInteger("relInternshipId"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * 实习统计（校内/校外学院汇总）数据范围：校级管理员看全校；院系管理员固定本院系子树，忽略前端传入的 departmentId。
     */
    private static final class CollegeStatsUserScope {
        private final Integer reportDepartmentId;
        private final Set<Integer> studentUserIds;
        private final Set<Integer> schoolTeacherUserIds;

        private CollegeStatsUserScope(Integer reportDepartmentId, Set<Integer> studentUserIds, Set<Integer> schoolTeacherUserIds) {
            this.reportDepartmentId = reportDepartmentId;
            this.studentUserIds = studentUserIds;
            this.schoolTeacherUserIds = schoolTeacherUserIds;
        }
    }

    private ViewBaseUser requireLoginViewBaseUser() {
        Object uo = iCommonService.getOneRecordById("ViewBaseUser", getLoginUserId());
        if (uo == null) {
            throw BaseResponse.moreInfoError.error("用户信息不存在");
        }
        return (ViewBaseUser) uo;
    }

    private static boolean isSchoolWideCollegeStatsRole(String jobCode) {
        if (jobCode == null) {
            return false;
        }
        return Constant.USER_JOB_CODE.SUPER_ADMIN.equals(jobCode)
                || Constant.USER_JOB_CODE.SCHOOL_ADMIN.equals(jobCode)
                || Constant.USER_JOB_CODE.ACADEMIC_AFFAIRS_ADMIN.equals(jobCode);
    }

    private static boolean isDepartmentCollegeStatsRole(String jobCode) {
        return jobCode != null && Constant.USER_JOB_CODE.DEPARTMENT_ADMIN.equals(jobCode);
    }

    private void assertDepartmentAccessibleToStatsUser(Integer departmentId, ViewBaseUser u) {
        if (departmentId == null) {
            return;
        }
        if (Constant.USER_JOB_CODE.SUPER_ADMIN.equals(u.getJobCode())) {
            return;
        }
        Object dObj = iCommonService.getOneRecordById("ViewBaseDepartment", departmentId);
        if (dObj == null) {
            throw BaseResponse.parameterInvalid.error("部门不存在");
        }
        Integer deptSchoolId = FastJsonUtil.toJson(dObj).getInteger("schoolId");
        Integer userSchoolId = u.getSchoolId();
        if (userSchoolId == null || !userSchoolId.equals(deptSchoolId)) {
            throw BaseResponse.lackPermissions.error("仅能查看本校学院数据");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Integer> loadAllDepartmentIdsPaged(String tableName, JSONObject searchKeys) {
        List<Integer> out = new ArrayList<>();
        int p = 1;
        while (true) {
            Page<Object> pg = (Page<Object>) iCommonService.getSomeRecords(
                    tableName, searchKeys, null, Sort.unsorted(), p, POST_PAGE_SIZE);
            List<Object> c = pg.getContent();
            if (c == null || c.isEmpty()) {
                break;
            }
            for (Object o : c) {
                Integer id = FastJsonUtil.toJson(o).getInteger("id");
                if (id != null) {
                    out.add(id);
                }
            }
            if (c.size() < POST_PAGE_SIZE) {
                break;
            }
            p++;
        }
        return out.stream().distinct().collect(Collectors.toList());
    }

    private List<Integer> loadAllViewDepartmentIdsForSchool(Integer schoolId) {
        JSONObject sk = new JSONObject();
        sk.put("schoolId", schoolId);
        return loadAllDepartmentIdsPaged("ViewBaseDepartment", sk);
    }

    private List<Integer> loadAllViewDepartmentIdsGlobal() {
        return loadAllDepartmentIdsPaged("ViewBaseDepartment", new JSONObject());
    }

    private List<Integer> resolveExternalCollegeStatsDepartmentIds(Integer requestDepartmentId) {
        ViewBaseUser u = requireLoginViewBaseUser();
        String jc = u.getJobCode();
        if (isDepartmentCollegeStatsRole(jc)) {
            Integer anchor = u.getDepartmentId();
            if (anchor == null || anchor <= 0) {
                throw BaseResponse.parameterInvalid.error("院系管理员未关联部门");
            }
            List<Integer> ids = iDataTreeService.getAllChildIndex("BaseDepartment", anchor);
            if (ids == null || ids.isEmpty()) {
                throw BaseResponse.parameterInvalid.error("部门不存在或无效");
            }
            return ids;
        }
        if (isSchoolWideCollegeStatsRole(jc)) {
            if (requestDepartmentId != null) {
                assertDepartmentAccessibleToStatsUser(requestDepartmentId, u);
                List<Integer> ids = iDataTreeService.getAllChildIndex("BaseDepartment", requestDepartmentId);
                if (ids == null || ids.isEmpty()) {
                    throw BaseResponse.parameterInvalid.error("部门不存在或无效");
                }
                return ids;
            }
            if (Constant.USER_JOB_CODE.SUPER_ADMIN.equals(jc)) {
                List<Integer> all = loadAllViewDepartmentIdsGlobal();
                if (all.isEmpty()) {
                    throw BaseResponse.parameterInvalid.error("无部门数据");
                }
                return all;
            }
            Integer sid = u.getSchoolId();
            if (sid == null || sid <= 0) {
                throw BaseResponse.parameterInvalid.error("无法确定所属学校，请传入 departmentId");
            }
            List<Integer> schoolDepts = loadAllViewDepartmentIdsForSchool(sid);
            if (schoolDepts.isEmpty()) {
                throw BaseResponse.parameterInvalid.error("本校无部门数据");
            }
            return schoolDepts;
        }
        throw BaseResponse.lackPermissions.error("无权查看实习统计");
    }

    private CollegeStatsUserScope resolveInternalCollegeStatsUserScope(Integer requestDepartmentId) {
        ViewBaseUser u = requireLoginViewBaseUser();
        String jc = u.getJobCode();
        if (isDepartmentCollegeStatsRole(jc)) {
            Integer anchor = u.getDepartmentId();
            if (anchor == null || anchor <= 0) {
                throw BaseResponse.parameterInvalid.error("院系管理员未关联部门");
            }
            return new CollegeStatsUserScope(
                    anchor,
                    loadUserIdsByDepartmentSubtreeAndJobCode(anchor, Constant.USER_JOB_CODE.STUDENT),
                    loadUserIdsByDepartmentSubtreeAndJobCode(anchor, Constant.USER_JOB_CODE.SCHOOL_TEACHER));
        }
        if (isSchoolWideCollegeStatsRole(jc)) {
            if (requestDepartmentId != null) {
                assertDepartmentAccessibleToStatsUser(requestDepartmentId, u);
                return new CollegeStatsUserScope(
                        requestDepartmentId,
                        loadUserIdsByDepartmentSubtreeAndJobCode(requestDepartmentId, Constant.USER_JOB_CODE.STUDENT),
                        loadUserIdsByDepartmentSubtreeAndJobCode(requestDepartmentId, Constant.USER_JOB_CODE.SCHOOL_TEACHER));
            }
            if (Constant.USER_JOB_CODE.SUPER_ADMIN.equals(jc)) {
                List<Integer> allDeptIds = loadAllViewDepartmentIdsGlobal();
                if (allDeptIds.isEmpty()) {
                    throw BaseResponse.parameterInvalid.error("无部门数据");
                }
                return new CollegeStatsUserScope(
                        null,
                        loadUserIdsByDepartmentIdsAndJobCode(allDeptIds, Constant.USER_JOB_CODE.STUDENT),
                        loadUserIdsByDepartmentIdsAndJobCode(allDeptIds, Constant.USER_JOB_CODE.SCHOOL_TEACHER));
            }
            Integer sid = u.getSchoolId();
            if (sid == null || sid <= 0) {
                throw BaseResponse.parameterInvalid.error("无法确定所属学校，请传入 departmentId");
            }
            List<Integer> schoolDeptIds = loadAllViewDepartmentIdsForSchool(sid);
            if (schoolDeptIds.isEmpty()) {
                throw BaseResponse.parameterInvalid.error("本校无部门数据");
            }
            return new CollegeStatsUserScope(
                    null,
                    loadUserIdsByDepartmentIdsAndJobCode(schoolDeptIds, Constant.USER_JOB_CODE.STUDENT),
                    loadUserIdsByDepartmentIdsAndJobCode(schoolDeptIds, Constant.USER_JOB_CODE.SCHOOL_TEACHER));
        }
        throw BaseResponse.lackPermissions.error("无权查看实习统计");
    }

    // ==================== 校外实习统计（学院汇总 / 岗位 / 学生选岗） ====================

    @Override
    public Object listExternalInternshipCollegeStats(Integer departmentId, Integer page, Integer size) {
        int pageNum = (page == null || page < 1) ? Constant.DEFAULT_PAGE : page;
        int pageSize = (size == null || size < 1) ? Constant.DEFAULT_SIZE : size;
        List<Integer> deptIds = resolveExternalCollegeStatsDepartmentIds(departmentId);
        Page<Object[]> statsPage = viewExternalInternshipCollegeStatsDao.findAggregatedByDepartmentIds(
                deptIds, PageRequest.of(pageNum - 1, pageSize));
        JSONArray rows = new JSONArray();
        for (Object[] r : statsPage.getContent()) {
            if (r == null || r.length < 9) {
                continue;
            }
            JSONObject row = new JSONObject();
            row.put("internshipId", toInteger(r[0]));
            row.put("internshipName", r[1] != null ? String.valueOf(r[1]) : null);
            row.put("signupStudentCount", nzInt(toInteger(r[3])));
            row.put("signupTeacherCount", nzInt(toInteger(r[4])));
            row.put("postSignupCount", nzInt(toInteger(r[5])));
            row.put("totalRecruitmentHeadcount", nzNumberInt(r[6]));
            row.put("pendingAuditPostCount", nzInt(toInteger(r[7])));
            row.put("studentWithPostSelectionCount", nzInt(toInteger(r[8])));
            rows.add(row);
        }
        JSONObject result = new JSONObject();
        result.put("rows", rows);
        result.put("totalElements", statsPage.getTotalElements());
        result.put("totalPages", statsPage.getTotalPages());
        result.put("page", pageNum);
        result.put("size", pageSize);
        return result;
    }

    private static Integer toInteger(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int nzNumberInt(Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof BigDecimal) {
            return nzBigDecimalInt((BigDecimal) o);
        }
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        try {
            return new BigDecimal(String.valueOf(o)).intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private static int nzBigDecimalInt(BigDecimal v) {
        return v == null ? 0 : v.intValue();
    }

    private static int nzInt(Integer v) {
        return v == null ? 0 : v;
    }

    // ==================== 校内实习统计（学院汇总 / 学生选题 / 教师申报题目） ====================

    @Override
    public Object listInternalInternshipCollegeStats(Integer departmentId, Integer page, Integer size) {
        int pageNum = (page == null || page < 1) ? Constant.DEFAULT_PAGE : page;
        int pageSize = (size == null || size < 1) ? Constant.DEFAULT_SIZE : size;

        CollegeStatsUserScope scope = resolveInternalCollegeStatsUserScope(departmentId);
        Set<Integer> deptStudentIds = scope.studentUserIds;
        Set<Integer> deptSchoolTeacherIds = scope.schoolTeacherUserIds;
        Integer reportDepartmentId = scope.reportDepartmentId;

        JSONObject internshipSk = new JSONObject();
        internshipSk.put("intTypeName", INTERNAL_INT_TYPE_NAME);
        Sort newestFirst = Sort.by(Sort.Direction.DESC, "createTime");
        @SuppressWarnings("unchecked")
        Page<Object> internshipPage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewMainInternship", internshipSk, null, newestFirst, pageNum, pageSize);
        List<Object> internshipList = internshipPage.getContent();
        if (internshipList == null || internshipList.isEmpty()) {
            JSONObject empty = new JSONObject();
            empty.put("departmentId", reportDepartmentId);
            empty.put("rows", new JSONArray());
            empty.put("totalElements", internshipPage.getTotalElements());
            empty.put("totalPages", internshipPage.getTotalPages());
            empty.put("page", pageNum);
            empty.put("size", pageSize);
            return empty;
        }

        JSONArray rows = new JSONArray();
        for (Object inv : internshipList) {
            JSONObject invJson = FastJsonUtil.toJson(inv);
            Integer internshipId = invJson.getInteger("id");
            if (internshipId == null) {
                continue;
            }
            String internshipName = invJson.getString("name");

            int signupStudentCount = countDistinctMergeUsersInSet(internshipId, Constant.USER_JOB_CODE.STUDENT, deptStudentIds);
            int signupTeacherCount = countDistinctMergeUsersInSet(internshipId, Constant.USER_JOB_CODE.SCHOOL_TEACHER, deptSchoolTeacherIds);

            List<Object> titleTeacherMerges = fetchAllTitleTeacherMergeByInternship(internshipId);
            int titleApprovedCount = countDistinctFullyApprovedTitleRelations(titleTeacherMerges);
            Set<Integer> teachersSubmittedTopic = collectTeacherIdsWithNonSaveTopicAudit(titleTeacherMerges);

            Set<Integer> deptTeachersEnrolled = mergeProjectUserIdsInAllowed(
                    internshipId, Constant.USER_JOB_CODE.SCHOOL_TEACHER, deptSchoolTeacherIds);
            int teachersNotSubmittedTopicCount = (int) deptTeachersEnrolled.stream()
                    .filter(t -> !teachersSubmittedTopic.contains(t))
                    .count();

            Set<Integer> deptStudentsEnrolled = mergeProjectUserIdsInAllowed(
                    internshipId, Constant.USER_JOB_CODE.STUDENT, deptStudentIds);
            Map<Integer, JSONObject> titleStuBest = buildStudentTitleBestMergeMap(internshipId);
            int studentsTitleApprovedCount = 0;
            int studentsTitlePendingCount = 0;
            int studentsNotSelectedTitleCount = 0;
            for (Integer sid : deptStudentsEnrolled) {
                String st = resolveInternalTitleSelectionStatusFromSample(titleStuBest.get(sid));
                if (TITLE_SEL_STATUS_APPROVED.equals(st)) {
                    studentsTitleApprovedCount++;
                } else if (TITLE_SEL_STATUS_PENDING.equals(st)) {
                    studentsTitlePendingCount++;
                } else {
                    studentsNotSelectedTitleCount++;
                }
            }

            JSONObject row = new JSONObject();
            row.put("internshipId", internshipId);
            row.put("internshipName", internshipName);
            row.put("signupStudentCount", signupStudentCount);
            row.put("signupTeacherCount", signupTeacherCount);
            row.put("titleApprovedCount", titleApprovedCount);
            row.put("teachersNotSubmittedTopicCount", teachersNotSubmittedTopicCount);
            row.put("studentsTitleApprovedCount", studentsTitleApprovedCount);
            row.put("studentsTitlePendingCount", studentsTitlePendingCount);
            row.put("studentsNotSelectedTitleCount", studentsNotSelectedTitleCount);
            rows.add(row);
        }

        JSONObject result = new JSONObject();
        result.put("departmentId", reportDepartmentId);
        result.put("rows", rows);
        result.put("totalElements", internshipPage.getTotalElements());
        result.put("totalPages", internshipPage.getTotalPages());
        result.put("page", pageNum);
        result.put("size", pageSize);
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object getInternalInternshipTitleSelectionBreakdown(Integer internshipId, Integer page, Integer size, String status,
                                                               Integer departmentId) {
        assertInternalInternship(internshipId);
        String st = normalizeTitleSelectionBreakdownStatus(status);
        int pageNum = (page == null || page < 1) ? Constant.DEFAULT_PAGE : page;
        int pageSize = (size == null || size < 1) ? Constant.DEFAULT_SIZE : size;

        CollegeStatsUserScope statsScope = resolveInternalCollegeStatsUserScope(departmentId);
        Set<Integer> projectStudentUserIds = mergeProjectUserIdsInAllowed(
                internshipId, Constant.USER_JOB_CODE.STUDENT, statsScope.studentUserIds);
        List<Object> mergeAll = fetchAllTitleStudentMergeByInternship(internshipId);

        Map<Integer, JSONObject> stuToBestMerge = new HashMap<>();
        for (Object o : mergeAll) {
            JSONObject j = FastJsonUtil.toJson(o);
            Integer stuId = j.getInteger("stuId");
            if (stuId == null || !projectStudentUserIds.contains(stuId)) {
                continue;
            }
            JSONObject prev = stuToBestMerge.get(stuId);
            stuToBestMerge.put(stuId, prev == null ? j : pickBetterTitleMergeRow(prev, j));
        }

        Map<Integer, JSONObject> mergeForDisplay = new HashMap<>(stuToBestMerge);

        Set<Integer> approvedSet = new HashSet<>();
        Set<Integer> pendingSet = new HashSet<>();
        Set<Integer> notSubmittedSet = new HashSet<>();
        for (Integer uid : projectStudentUserIds) {
            JSONObject best = stuToBestMerge.get(uid);
            String sel = resolveInternalTitleSelectionStatusFromSample(best);
            if (TITLE_SEL_STATUS_APPROVED.equals(sel)) {
                approvedSet.add(uid);
            } else if (TITLE_SEL_STATUS_PENDING.equals(sel)) {
                pendingSet.add(uid);
            } else {
                notSubmittedSet.add(uid);
            }
        }

        JSONObject counts = new JSONObject();
        counts.put(TITLE_SEL_STATUS_NOT_SUBMITTED, notSubmittedSet.size());
        counts.put(TITLE_SEL_STATUS_PENDING, pendingSet.size());
        counts.put(TITLE_SEL_STATUS_APPROVED, approvedSet.size());

        Object invObj = iCommonService.getOneRecordById("ViewMainInternship", internshipId);
        JSONObject invJ = invObj != null ? FastJsonUtil.toJson(invObj) : new JSONObject();

        if (TITLE_SEL_STATUS_ALL.equals(st)) {
            List<Integer> allOrdered = projectStudentUserIds.stream().sorted().collect(Collectors.toList());
            int total = allOrdered.size();
            int totalPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) total / (double) pageSize);
            int from = Math.max(0, (pageNum - 1) * pageSize);
            int to = Math.min(from + pageSize, total);
            List<Integer> slice = from >= total ? Collections.emptyList() : allOrdered.subList(from, to);
            JSONArray rows = new JSONArray();
            for (Integer uid : slice) {
                JSONObject row = buildOneInternalTitleRow(uid, mergeForDisplay.get(uid));
                row.put("selectionStatus", resolveInternalTitleSelectionStatusFromSample(mergeForDisplay.get(uid)));
                rows.add(row);
            }
            JSONObject result = new JSONObject();
            result.put("internshipId", internshipId);
            result.put("internshipName", invJ.getString("name"));
            result.put("status", TITLE_SEL_STATUS_ALL);
            result.put("page", pageNum);
            result.put("size", pageSize);
            result.put("counts", counts);
            result.put("totalElements", total);
            result.put("totalPages", totalPages);
            result.put("rows", rows);
            result.put("departmentId", statsScope.reportDepartmentId);
            return result;
        }

        List<Integer> targetList;
        Map<Integer, JSONObject> mergeForRows;
        if (TITLE_SEL_STATUS_NOT_SUBMITTED.equals(st)) {
            targetList = notSubmittedSet.stream().sorted().collect(Collectors.toList());
            mergeForRows = mergeForDisplay;
        } else if (TITLE_SEL_STATUS_PENDING.equals(st)) {
            targetList = pendingSet.stream().sorted().collect(Collectors.toList());
            mergeForRows = mergeForDisplay;
        } else {
            targetList = approvedSet.stream().sorted().collect(Collectors.toList());
            mergeForRows = mergeForDisplay;
        }

        JSONObject paged = buildPagedInternalTitleCategory(targetList, mergeForRows, pageNum, pageSize);
        JSONObject result = new JSONObject();
        result.put("internshipId", internshipId);
        result.put("internshipName", invJ.getString("name"));
        result.put("status", st);
        result.put("page", paged.getInteger("page"));
        result.put("size", paged.getInteger("size"));
        result.put("counts", counts);
        result.put("totalElements", paged.getInteger("totalElements"));
        result.put("totalPages", paged.getInteger("totalPages"));
        result.put("rows", paged.get("rows"));
        result.put("departmentId", statsScope.reportDepartmentId);
        return result;
    }

    @Override
    public Object listInternalInternshipTeachersNotSubmittedTopic(Integer internshipId, Integer departmentId, Integer page, Integer size) {
        assertInternalInternship(internshipId);
        int pageNum = (page == null || page < 1) ? Constant.DEFAULT_PAGE : page;
        int pageSize = (size == null || size < 1) ? Constant.DEFAULT_SIZE : size;

        CollegeStatsUserScope statsScope = resolveInternalCollegeStatsUserScope(departmentId);
        Set<Integer> enrolledTeachers = mergeProjectUserIdsInAllowed(
                internshipId, Constant.USER_JOB_CODE.SCHOOL_TEACHER, statsScope.schoolTeacherUserIds);

        List<Object> titleMerges = fetchAllTitleTeacherMergeByInternship(internshipId);
        Set<Integer> submitted = collectTeacherIdsWithNonSaveTopicAudit(titleMerges);

        List<Integer> notSubmittedIds = enrolledTeachers.stream()
                .filter(t -> !submitted.contains(t))
                .sorted()
                .collect(Collectors.toList());

        int total = notSubmittedIds.size();
        int totalPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) total / (double) pageSize);
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(from + pageSize, total);
        List<Integer> slice = from >= total ? Collections.emptyList() : notSubmittedIds.subList(from, to);

        JSONArray rows = new JSONArray();
        for (Integer tid : slice) {
            rows.add(buildTeacherNotSubmittedTopicRow(tid));
        }

        Object invObj = iCommonService.getOneRecordById("ViewMainInternship", internshipId);
        JSONObject invJ = invObj != null ? FastJsonUtil.toJson(invObj) : new JSONObject();

        JSONObject result = new JSONObject();
        result.put("internshipId", internshipId);
        result.put("internshipName", invJ.getString("name"));
        result.put("departmentId", statsScope.reportDepartmentId);
        result.put("notSubmittedCount", total);
        result.put("page", pageNum);
        result.put("size", pageSize);
        result.put("totalElements", total);
        result.put("totalPages", totalPages);
        result.put("rows", rows);
        return result;
    }

    @Override
    public Object listApprovedExternalInternshipPosts(Integer internshipId, Integer page, Integer size) {
        assertExternalInternship(internshipId);
        int pageNum = (page == null || page < 1) ? Constant.DEFAULT_PAGE : page;
        int pageSize = (size == null || size < 1) ? Constant.DEFAULT_SIZE : size;

        List<Object> list = fetchAllPagesInternshipPostMergePass(internshipId);
        List<JSONObject> allItems = new ArrayList<>();
        if (list != null) {
            Set<Integer> seenPostIds = new HashSet<>();
            for (Object o : list) {
                JSONObject j = FastJsonUtil.toJson(o);
                Integer postId = parseInternshipPostIdFromInternshipPostMergeRow(j);
                if (postId == null || seenPostIds.contains(postId)) {
                    continue;
                }
                seenPostIds.add(postId);
                JSONObject item = new JSONObject();
                item.put("internshipPostId", postId);
                item.put("internshipPostName", firstNonEmpty(j.getString("internshipPostName"), j.getString("name")));
                item.put("companyName", j.getString("companyName"));
                item.put("companyId", j.getInteger("companyId"));
                item.put("allPersonNum", j.getInteger("allPersonNum"));
                item.put("nowPersonNum", j.getInteger("nowPersonNum"));
                item.put("internshipPostCode", j.getString("internshipPostCode"));
                item.put("processTypeCode", j.getString("processTypeCode"));
                Object postEntity = iCommonService.getOneRecordById("MainInternshipPost", postId);
                if (postEntity != null) {
                    JSONObject pe = FastJsonUtil.toJson(postEntity);
                    item.put("postTypeId", pe.getInteger("postTypeId"));
                    item.put("remarks", pe.getString("remarks"));
                    Integer postTypeId = pe.getInteger("postTypeId");
                    if (postTypeId != null) {
                        Object postTypeObj = iCommonService.getOneRecordById("BasePostType", postTypeId);
                        if (postTypeObj != null) {
                            item.put("salary", FastJsonUtil.toJson(postTypeObj).getInteger("salary"));
                        }
                    }
                }
                allItems.add(item);
            }
        }

        int total = allItems.size();
        int totalPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) total / (double) pageSize);
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(from + pageSize, total);
        JSONArray posts = new JSONArray();
        if (from < total) {
            for (int i = from; i < to; i++) {
                posts.add(allItems.get(i));
            }
        }

        JSONObject out = new JSONObject();
        out.put("internshipId", internshipId);
        out.put("posts", posts);
        out.put("page", pageNum);
        out.put("size", pageSize);
        out.put("totalElements", total);
        out.put("totalPages", totalPages);
        return out;
    }

    /**
     * 拉取某项目下岗位合并视图中 isAudit=PASS 的全部行（分页拼接），避免仅取第一页导致去重后 total 偏少。
     */
    @SuppressWarnings("unchecked")
    private List<Object> fetchAllPagesInternshipPostMergePass(Integer internshipId) {
        JSONObject sk = new JSONObject();
        sk.put("internshipId", internshipId);
        sk.put("isAudit", Constant.AUDIT_STATUS.PASS);
        List<Object> all = new ArrayList<>();
        int p = 1;
        while (true) {
            Page<Object> dbPage = (Page<Object>) iCommonService.getSomeRecords(
                    "ViewVerifyProcessInternshipPostMerge", sk, null, Sort.unsorted(), p, POST_PAGE_SIZE);
            List<Object> content = dbPage.getContent();
            if (content == null || content.isEmpty()) {
                break;
            }
            all.addAll(content);
            if (content.size() < POST_PAGE_SIZE) {
                break;
            }
            p++;
        }
        return all;
    }

    @Override
    public Object getExternalInternshipStudentPostBreakdown(Integer internshipId, Integer page, Integer size, String status,
                                                            Integer departmentId) {
        assertExternalInternship(internshipId);
        String st = normalizeStudentPostBreakdownStatus(status);
        int pageNum = (page == null || page < 1) ? Constant.DEFAULT_PAGE : page;
        int pageSize = (size == null || size < 1) ? Constant.DEFAULT_SIZE : size;
        Set<Integer> projectStudentUserIds = loadInternshipProjectStudentUserIds(internshipId);
        JSONObject stuSk = new JSONObject();
        stuSk.put("internshipId", internshipId);
        @SuppressWarnings("unchecked")
        Page<Object> mergePage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewVerifyProcessRelStuInternshipPostMerge", stuSk, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> mergeList = mergePage.getContent() == null ? Collections.emptyList() : mergePage.getContent();

        Set<Integer> anyStuPostUser = new HashSet<>();
        Set<Integer> passUser = new HashSet<>();
        Map<Integer, JSONObject> userRowSample = new HashMap<>();
        for (Object o : mergeList) {
            JSONObject j = FastJsonUtil.toJson(o);
            Integer uid = parseStudentUserIdFromStuPostMerge(j);
            if (uid == null) {
                continue;
            }
            anyStuPostUser.add(uid);
            Integer isAudit = j.getInteger("isAudit");
            if (isAudit != null && isAudit == Constant.AUDIT_STATUS.PASS) {
                passUser.add(uid);
            }
            JSONObject prev = userRowSample.get(uid);
            if (prev == null) {
                userRowSample.put(uid, j);
            } else if (isAudit != null && isAudit == Constant.AUDIT_STATUS.PASS) {
                userRowSample.put(uid, j);
            }
        }

        List<Integer> postApprovedList = passUser.stream().sorted().collect(Collectors.toList());
        List<Integer> selectedPending = anyStuPostUser.stream()
                .filter(u -> !passUser.contains(u))
                .sorted()
                .collect(Collectors.toList());
        List<Integer> notSelected = projectStudentUserIds.stream()
                .filter(u -> !anyStuPostUser.contains(u))
                .sorted()
                .collect(Collectors.toList());

        JSONObject counts = new JSONObject();
        counts.put(STU_POST_STATUS_NOT_SELECTED, notSelected.size());
        counts.put(STU_POST_STATUS_SELECTED_PENDING, selectedPending.size());
        counts.put(STU_POST_STATUS_POST_APPROVED, postApprovedList.size());

        Object invObj = iCommonService.getOneRecordById("ViewMainInternship", internshipId);
        JSONObject invJ = invObj != null ? FastJsonUtil.toJson(invObj) : new JSONObject();

        if (STU_POST_STATUS_ALL.equals(st)) {
            List<Integer> allOrdered = projectStudentUserIds.stream().sorted().collect(Collectors.toList());
            int total = allOrdered.size();
            int totalPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) total / (double) pageSize);
            int from = Math.max(0, (pageNum - 1) * pageSize);
            int to = Math.min(from + pageSize, total);
            List<Integer> slice = from >= total ? Collections.emptyList() : allOrdered.subList(from, to);
            JSONArray rows = new JSONArray();
            for (Integer uid : slice) {
                JSONObject row = buildOneStudentBriefRow(uid, userRowSample);
                row.put("selectionStatus", resolveStudentSelectionStatus(uid, anyStuPostUser, passUser));
                rows.add(row);
            }
            JSONObject result = new JSONObject();
            result.put("internshipId", internshipId);
            result.put("internshipName", invJ.getString("name"));
            result.put("status", STU_POST_STATUS_ALL);
            result.put("page", pageNum);
            result.put("size", pageSize);
            result.put("counts", counts);
            result.put("totalElements", total);
            result.put("totalPages", totalPages);
            result.put("rows", rows);
            return result;
        }

        List<Integer> targetList;
        Map<Integer, JSONObject> mergeForRows;
        if (STU_POST_STATUS_NOT_SELECTED.equals(st)) {
            targetList = notSelected;
            mergeForRows = null;
        } else if (STU_POST_STATUS_SELECTED_PENDING.equals(st)) {
            targetList = selectedPending;
            mergeForRows = userRowSample;
        } else {
            targetList = postApprovedList;
            mergeForRows = userRowSample;
        }

        JSONObject paged = buildPagedStudentCategory(targetList, mergeForRows, pageNum, pageSize);
        JSONObject result = new JSONObject();
        result.put("internshipId", internshipId);
        result.put("internshipName", invJ.getString("name"));
        result.put("status", st);
        result.put("page", paged.getInteger("page"));
        result.put("size", paged.getInteger("size"));
        result.put("counts", counts);
        result.put("totalElements", paged.getInteger("totalElements"));
        result.put("totalPages", paged.getInteger("totalPages"));
        result.put("rows", paged.get("rows"));
        return result;
    }

    private static String normalizeStudentPostBreakdownStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return STU_POST_STATUS_ALL;
        }
        String s = status.trim();
        if (STU_POST_STATUS_ALL.equals(s)
                || STU_POST_STATUS_NOT_SELECTED.equals(s)
                || STU_POST_STATUS_SELECTED_PENDING.equals(s)
                || STU_POST_STATUS_POST_APPROVED.equals(s)) {
            return s;
        }
        throw BaseResponse.parameterInvalid.error("status 无效，可选：all、notSelected、selectedPendingAudit、postApproved");
    }

    private static String resolveStudentSelectionStatus(Integer uid, Set<Integer> anyStuPostUser, Set<Integer> passUser) {
        if (passUser.contains(uid)) {
            return STU_POST_STATUS_POST_APPROVED;
        }
        if (anyStuPostUser.contains(uid)) {
            return STU_POST_STATUS_SELECTED_PENDING;
        }
        return STU_POST_STATUS_NOT_SELECTED;
    }

    private void assertExternalInternship(Integer internshipId) {
        if (internshipId == null) {
            throw BaseResponse.parameterInvalid.error("internshipId 不能为空");
        }
        Object v = iCommonService.getOneRecordById("ViewMainInternship", internshipId);
        if (v == null) {
            throw BaseResponse.parameterInvalid.error("实习项目不存在");
        }
        JSONObject j = FastJsonUtil.toJson(v);
        String intTypeName = j.getString("intTypeName");
        if (intTypeName == null || !EXTERNAL_INT_TYPE_NAME.equals(intTypeName.trim())) {
            throw BaseResponse.parameterInvalid.error("仅支持校外实习项目");
        }
    }

    private void assertInternalInternship(Integer internshipId) {
        if (internshipId == null) {
            throw BaseResponse.parameterInvalid.error("internshipId 不能为空");
        }
        Object v = iCommonService.getOneRecordById("ViewMainInternship", internshipId);
        if (v == null) {
            throw BaseResponse.parameterInvalid.error("实习项目不存在");
        }
        JSONObject j = FastJsonUtil.toJson(v);
        String intTypeName = j.getString("intTypeName");
        if (intTypeName == null || !INTERNAL_INT_TYPE_NAME.equals(intTypeName.trim())) {
            throw BaseResponse.parameterInvalid.error("仅支持校内实习项目");
        }
    }

    private Set<Integer> mergeProjectUserIdsInAllowed(Integer internshipId, String jobCode, Set<Integer> allowedUserIds) {
        if (allowedUserIds == null || allowedUserIds.isEmpty()) {
            return new HashSet<>();
        }
        JSONObject sk = new JSONObject();
        sk.put("internshipId", internshipId);
        sk.put("jobCode", jobCode);
        @SuppressWarnings("unchecked")
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                "ViewVerifyProcessRelIntershipUserMerge", sk, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> list = page.getContent();
        if (list == null || list.isEmpty()) {
            return new HashSet<>();
        }
        return list.stream()
                .map(FastJsonUtil::toJson)
                .map(json -> json.getInteger("userId"))
                .filter(Objects::nonNull)
                .filter(allowedUserIds::contains)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static int countDistinctFullyApprovedTitleRelations(List<Object> titleTeacherMerges) {
        if (titleTeacherMerges == null || titleTeacherMerges.isEmpty()) {
            return 0;
        }
        Set<Integer> ids = new HashSet<>();
        for (Object o : titleTeacherMerges) {
            JSONObject j = FastJsonUtil.toJson(o);
            if (Boolean.TRUE.equals(j.getBoolean("isAllVerified"))) {
                Integer rid = j.getInteger("relationId");
                if (rid != null) {
                    ids.add(rid);
                }
            }
        }
        return ids.size();
    }

    private static Set<Integer> collectTeacherIdsWithNonSaveTopicAudit(List<Object> titleTeacherMerges) {
        Set<Integer> set = new HashSet<>();
        if (titleTeacherMerges == null) {
            return set;
        }
        for (Object o : titleTeacherMerges) {
            JSONObject j = FastJsonUtil.toJson(o);
            Integer tid = j.getInteger("teacherId");
            Integer audit = j.getInteger("isAudit");
            if (tid != null && audit != null && audit != Constant.AUDIT_STATUS.SAVE) {
                set.add(tid);
            }
        }
        return set;
    }

    private Map<Integer, JSONObject> buildStudentTitleBestMergeMap(Integer internshipId) {
        List<Object> all = fetchAllTitleStudentMergeByInternship(internshipId);
        Map<Integer, JSONObject> map = new HashMap<>();
        for (Object o : all) {
            JSONObject j = FastJsonUtil.toJson(o);
            Integer stuId = j.getInteger("stuId");
            if (stuId == null) {
                continue;
            }
            JSONObject prev = map.get(stuId);
            map.put(stuId, prev == null ? j : pickBetterTitleMergeRow(prev, j));
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<Object> fetchAllTitleTeacherMergeByInternship(Integer internshipId) {
        JSONObject sk = new JSONObject();
        sk.put("internshipId", internshipId);
        List<Object> all = new ArrayList<>();
        int p = 1;
        while (true) {
            Page<Object> dbPage = (Page<Object>) iCommonService.getSomeRecords(
                    "ViewVerifyProcessRelTitleTeacherMerge", sk, null, Sort.unsorted(), p, POST_PAGE_SIZE);
            List<Object> content = dbPage.getContent();
            if (content == null || content.isEmpty()) {
                break;
            }
            all.addAll(content);
            if (content.size() < POST_PAGE_SIZE) {
                break;
            }
            p++;
        }
        return all;
    }

    private static String normalizeTitleSelectionBreakdownStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return TITLE_SEL_STATUS_ALL;
        }
        String s = status.trim();
        if (TITLE_SEL_STATUS_ALL.equals(s)
                || TITLE_SEL_STATUS_NOT_SUBMITTED.equals(s)
                || TITLE_SEL_STATUS_PENDING.equals(s)
                || TITLE_SEL_STATUS_APPROVED.equals(s)) {
            return s;
        }
        throw BaseResponse.parameterInvalid.error("status 无效，可选：all、notSubmitted、pendingAudit、titleApproved");
    }

    private static String resolveInternalTitleSelectionStatusFromSample(JSONObject best) {
        if (best == null) {
            return TITLE_SEL_STATUS_NOT_SUBMITTED;
        }
        int rank = internalTitleCategoryRank(best);
        if (rank == 2) {
            return TITLE_SEL_STATUS_APPROVED;
        }
        if (rank == 1) {
            return TITLE_SEL_STATUS_PENDING;
        }
        return TITLE_SEL_STATUS_NOT_SUBMITTED;
    }

    private static int internalTitleCategoryRank(JSONObject j) {
        if (j == null) {
            return 0;
        }
        Boolean allV = j.getBoolean("isAllVerified");
        Integer isAudit = j.getInteger("isAudit");
        if (Boolean.TRUE.equals(allV)) {
            return 2;
        }
        if (isAudit != null && isAudit == Constant.AUDIT_STATUS.SUBMIT) {
            return 1;
        }
        if (isAudit != null && isAudit == Constant.AUDIT_STATUS.PASS && !Boolean.TRUE.equals(allV)) {
            return 1;
        }
        return 0;
    }

    private static JSONObject pickBetterTitleMergeRow(JSONObject a, JSONObject b) {
        int ra = internalTitleCategoryRank(a);
        int rb = internalTitleCategoryRank(b);
        if (rb != ra) {
            return rb > ra ? b : a;
        }
        long ta = vpUpdateTimeMillis(a);
        long tb = vpUpdateTimeMillis(b);
        if (tb != ta) {
            return tb > ta ? b : a;
        }
        Integer ida = a.getInteger("id");
        Integer idb = b.getInteger("id");
        if (ida != null && idb != null && !idb.equals(ida)) {
            return idb > ida ? b : a;
        }
        return b;
    }

    private static long vpUpdateTimeMillis(JSONObject j) {
        if (j == null) {
            return 0L;
        }
        Date d = j.getDate("vpUpdateTime");
        return d != null ? d.getTime() : 0L;
    }

    @SuppressWarnings("unchecked")
    private List<Object> fetchAllTitleStudentMergeByInternship(Integer internshipId) {
        JSONObject sk = new JSONObject();
        sk.put("internshipId", internshipId);
        List<Object> all = new ArrayList<>();
        int p = 1;
        while (true) {
            Page<Object> dbPage = (Page<Object>) iCommonService.getSomeRecords(
                    "ViewVerifyProcessRelTitleStudentMerge", sk, null, Sort.unsorted(), p, POST_PAGE_SIZE);
            List<Object> content = dbPage.getContent();
            if (content == null || content.isEmpty()) {
                break;
            }
            all.addAll(content);
            if (content.size() < POST_PAGE_SIZE) {
                break;
            }
            p++;
        }
        return all;
    }

    private static boolean lacksTitleForDisplay(Integer titleId) {
        return titleId == null || titleId <= 0;
    }

    private static boolean lacksTeacherForDisplay(Integer teacherId) {
        return teacherId == null || teacherId <= 0;
    }

    private JSONObject buildOneInternalTitleRow(Integer stuId, JSONObject mergeSample) {
        JSONObject row = new JSONObject();
        row.put("stuId", stuId);
        String studentName = null;
        String titleName = "未选";
        String teacherName = "未选";
        if (mergeSample != null) {
            studentName = mergeSample.getString("studentName");
            if (!lacksTitleForDisplay(mergeSample.getInteger("titleId"))) {
                String n = mergeSample.getString("name");
                if (n != null && !n.isBlank()) {
                    titleName = n;
                }
            }
            if (!lacksTeacherForDisplay(mergeSample.getInteger("teacherId"))) {
                String tn = mergeSample.getString("teacherName");
                if (tn != null && !tn.isBlank()) {
                    teacherName = tn;
                }
            }
        }
        if (studentName == null || studentName.isBlank()) {
            Object uo = iCommonService.getOneRecordById("ViewBaseUser", stuId);
            if (uo != null) {
                studentName = FastJsonUtil.toJson(uo).getString("name");
            }
        }
        row.put("studentName", studentName);
        row.put("titleName", titleName);
        row.put("teacherName", teacherName);
        row.put("relTitleStudentId", mergeSample != null ? mergeSample.getInteger("relTitleStudentId") : null);
        return row;
    }

    private JSONObject buildPagedInternalTitleCategory(List<Integer> fullUserIds, Map<Integer, JSONObject> mergeMap,
                                                       int pageNum, int pageSize) {
        List<Integer> ids = fullUserIds == null ? Collections.emptyList() : fullUserIds;
        int total = ids.size();
        int totalPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) total / (double) pageSize);
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(from + pageSize, total);
        List<Integer> slice = from >= total ? Collections.emptyList() : ids.subList(from, to);
        JSONArray rows = new JSONArray();
        for (Integer uid : slice) {
            JSONObject mergeSample = mergeMap != null ? mergeMap.get(uid) : null;
            JSONObject row = buildOneInternalTitleRow(uid, mergeSample);
            row.put("selectionStatus", resolveInternalTitleSelectionStatusFromSample(mergeSample));
            rows.add(row);
        }
        JSONObject block = new JSONObject();
        block.put("rows", rows);
        block.put("page", pageNum);
        block.put("size", pageSize);
        block.put("totalElements", total);
        block.put("totalPages", totalPages);
        return block;
    }

    private JSONObject buildTeacherNotSubmittedTopicRow(Integer teacherUserId) {
        JSONObject row = new JSONObject();
        row.put("teacherId", teacherUserId);
        String name = "";
        String deptName = "";
        String phone = "";
        Object uo = iCommonService.getOneRecordById("ViewBaseUser", teacherUserId);
        if (uo != null) {
            JSONObject uj = FastJsonUtil.toJson(uo);
            name = uj.getString("name");
            deptName = uj.getString("departmentName");
            phone = uj.getString("phone");
        }
        if (name == null) {
            name = "";
        }
        if (deptName == null) {
            deptName = "";
        }
        if (phone == null) {
            phone = "";
        }
        row.put("teacherName", name);
        row.put("departmentName", deptName);
        row.put("phone", phone);
        row.put("displayLine", name + " - " + deptName + " - " + phone);
        return row;
    }

    @SuppressWarnings("unchecked")
    private Set<Integer> loadInternshipProjectUserIds(Integer internshipId, String jobCode) {
        JSONObject sk = new JSONObject();
        sk.put("internshipId", internshipId);
        sk.put("jobCode", jobCode);
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                "ViewVerifyProcessRelIntershipUserMerge", sk, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> list = page.getContent();
        if (list == null || list.isEmpty()) {
            return new HashSet<>();
        }
        return list.stream()
                .map(FastJsonUtil::toJson)
                .map(json -> json.getInteger("userId"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Set<Integer> loadUserIdsByDepartmentAndJobCode(Integer departmentId, String jobCode) {
        JSONObject sk = new JSONObject();
        sk.put("departmentId", departmentId);
        sk.put("jobCode", jobCode);
        @SuppressWarnings("unchecked")
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                "ViewBaseUser", sk, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> list = page.getContent();
        if (list == null || list.isEmpty()) {
            return new HashSet<>();
        }
        return list.stream()
                .map(FastJsonUtil::toJson)
                .map(json -> json.getInteger("id"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * 收集 {@link newcms.entity.db.BaseDepartment} 中某节点及其全部子孙节点 id（仅未删除节点参与构图）。
     */
    private Set<Integer> collectDepartmentIdsInSubtree(Integer rootDepartmentId) {
        if (rootDepartmentId == null) {
            return new HashSet<>();
        }
        JSONObject sk = new JSONObject();
        sk.put("isDeleted", 0);
        @SuppressWarnings("unchecked")
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                "BaseDepartment", sk, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> list = page.getContent();
        if (list == null || list.isEmpty()) {
            return new HashSet<>(Collections.singletonList(rootDepartmentId));
        }
        Map<Integer, List<Integer>> childrenByParent = new HashMap<>();
        Set<Integer> allDeptIds = new HashSet<>();
        for (Object o : list) {
            JSONObject j = FastJsonUtil.toJson(o);
            Integer id = j.getInteger("id");
            if (id == null) {
                continue;
            }
            allDeptIds.add(id);
            Integer pid = j.getInteger("parentId");
            childrenByParent.computeIfAbsent(pid != null ? pid : -1, k -> new ArrayList<>()).add(id);
        }
        if (!allDeptIds.contains(rootDepartmentId)) {
            return new HashSet<>(Collections.singletonList(rootDepartmentId));
        }
        Set<Integer> out = new HashSet<>();
        ArrayDeque<Integer> dq = new ArrayDeque<>();
        dq.add(rootDepartmentId);
        while (!dq.isEmpty()) {
            Integer cur = dq.poll();
            if (cur == null || out.contains(cur)) {
                continue;
            }
            out.add(cur);
            List<Integer> ch = childrenByParent.get(cur);
            if (ch != null) {
                for (Integer c : ch) {
                    if (c != null && !out.contains(c)) {
                        dq.add(c);
                    }
                }
            }
        }
        return out;
    }

    /**
     * 与 {@link #loadUserIdsByDepartmentAndJobCode} 相同语义，但 departmentId 视为树根，
     * 包含其下所有子部门（院系→专业/班级等）内的用户。
     */
    private Set<Integer> loadUserIdsByDepartmentSubtreeAndJobCode(Integer rootDepartmentId, String jobCode) {
        if (rootDepartmentId == null || jobCode == null || jobCode.trim().isEmpty()) {
            return new HashSet<>();
        }
        Set<Integer> deptIds = collectDepartmentIdsInSubtree(rootDepartmentId);
        if (deptIds.isEmpty()) {
            return new HashSet<>();
        }
        if (deptIds.size() == 1) {
            return loadUserIdsByDepartmentAndJobCode(rootDepartmentId, jobCode);
        }
        String idStr = deptIds.stream().map(String::valueOf).collect(Collectors.joining(Constant.SPLIT_OPERATOR.COMMA));
        JSONObject userSearchKeys = new JSONObject();
        userSearchKeys.put("departmentId", idStr);
        userSearchKeys.put("jobCode", jobCode);
        Map<String, String> repMap = new HashMap<>();
        repMap.put("departmentId", Constant.IN);
        @SuppressWarnings("unchecked")
        Page<Object> userPage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewBaseUser", userSearchKeys, repMap, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> userList = userPage.getContent();
        if (userList == null || userList.isEmpty()) {
            return new HashSet<>();
        }
        return userList.stream()
                .map(FastJsonUtil::toJson)
                .map(json -> json.getInteger("id"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Set<Integer> loadUserIdsByDepartmentIdsAndJobCode(Collection<Integer> departmentIds, String jobCode) {
        if (departmentIds == null || departmentIds.isEmpty() || jobCode == null || jobCode.trim().isEmpty()) {
            return new HashSet<>();
        }
        List<Integer> idList = departmentIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (idList.isEmpty()) {
            return new HashSet<>();
        }
        if (idList.size() == 1) {
            return loadUserIdsByDepartmentAndJobCode(idList.get(0), jobCode);
        }
        String idStr = idList.stream().map(String::valueOf).collect(Collectors.joining(Constant.SPLIT_OPERATOR.COMMA));
        JSONObject userSearchKeys = new JSONObject();
        userSearchKeys.put("departmentId", idStr);
        userSearchKeys.put("jobCode", jobCode);
        Map<String, String> repMap = new HashMap<>();
        repMap.put("departmentId", Constant.IN);
        @SuppressWarnings("unchecked")
        Page<Object> userPage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewBaseUser", userSearchKeys, repMap, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> userList = userPage.getContent();
        if (userList == null || userList.isEmpty()) {
            return new HashSet<>();
        }
        return userList.stream()
                .map(FastJsonUtil::toJson)
                .map(json -> json.getInteger("id"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private int countDistinctMergeUsersInSet(Integer internshipId, String jobCode, Set<Integer> allowedUserIds) {
        if (allowedUserIds == null || allowedUserIds.isEmpty()) {
            return 0;
        }
        JSONObject sk = new JSONObject();
        sk.put("internshipId", internshipId);
        sk.put("jobCode", jobCode);
        @SuppressWarnings("unchecked")
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                "ViewVerifyProcessRelIntershipUserMerge", sk, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> list = page.getContent();
        if (list == null || list.isEmpty()) {
            return 0;
        }
        return (int) list.stream()
                .map(FastJsonUtil::toJson)
                .map(json -> json.getInteger("userId"))
                .filter(Objects::nonNull)
                .filter(allowedUserIds::contains)
                .distinct()
                .count();
    }

    private int countPendingAuditInternshipPosts(Integer internshipId) {
        JSONObject sk = new JSONObject();
        sk.put("internshipId", internshipId);
        @SuppressWarnings("unchecked")
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                "ViewVerifyProcessInternshipPostMerge", sk, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> list = page.getContent();
        if (list == null || list.isEmpty()) {
            return 0;
        }
        Set<Integer> pendingIds = new HashSet<>();
        for (Object o : list) {
            JSONObject j = FastJsonUtil.toJson(o);
            Integer a = j.getInteger("isAudit");
            if (a != null && (a == Constant.AUDIT_STATUS.SAVE || a == Constant.AUDIT_STATUS.SUBMIT)) {
                Integer pid = parseInternshipPostIdFromInternshipPostMergeRow(j);
                if (pid != null) {
                    pendingIds.add(pid);
                }
            }
        }
        return pendingIds.size();
    }

    private int countDeptStudentsWithPostSelection(Integer internshipId, Integer departmentId) {
        JSONObject sk = new JSONObject();
        sk.put("internshipId", internshipId);
        sk.put("departmentId", departmentId);
        @SuppressWarnings("unchecked")
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                "ViewVerifyProcessRelStuInternshipPostMerge", sk, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> list = page.getContent();
        if (list == null || list.isEmpty()) {
            return 0;
        }
        Set<Integer> uids = new HashSet<>();
        for (Object o : list) {
            Integer uid = parseStudentUserIdFromStuPostMerge(FastJsonUtil.toJson(o));
            if (uid != null) {
                uids.add(uid);
            }
        }
        return uids.size();
    }

    private Set<Integer> loadInternshipProjectStudentUserIds(Integer internshipId) {
        return loadInternshipProjectUserIds(internshipId, Constant.USER_JOB_CODE.STUDENT);
    }

    private Integer parseStudentUserIdFromStuPostMerge(JSONObject j) {
        if (j == null) {
            return null;
        }
        Integer uid = j.getInteger("studentId");
        if (uid != null) {
            return uid;
        }
        String s = j.getString("studentId");
        if (s == null || s.isBlank()) {
            return j.getInteger("userId");
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private JSONObject buildPagedStudentCategory(List<Integer> fullUserIds, Map<Integer, JSONObject> mergeSample,
                                               int pageNum, int pageSize) {
        List<Integer> ids = fullUserIds == null ? Collections.emptyList() : fullUserIds;
        int total = ids.size();
        int totalPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) total / (double) pageSize);
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(from + pageSize, total);
        List<Integer> slice = from >= total ? Collections.emptyList() : ids.subList(from, to);
        JSONObject block = new JSONObject();
        block.put("rows", buildStudentBriefList(slice, mergeSample));
        block.put("page", pageNum);
        block.put("size", pageSize);
        block.put("totalElements", total);
        block.put("totalPages", totalPages);
        return block;
    }

    private JSONObject buildOneStudentBriefRow(Integer uid, Map<Integer, JSONObject> mergeSample) {
        JSONObject row = new JSONObject();
        row.put("userId", uid);
        if (mergeSample != null && mergeSample.containsKey(uid)) {
            JSONObject m = mergeSample.get(uid);
            row.put("userName", m.getString("studentName"));
            row.put("departmentId", m.getInteger("departmentId"));
            row.put("departmentName", m.getString("departmentName"));
            row.put("internshipPostName", m.getString("internshipPostName"));
            row.put("companyName", m.getString("companyName"));
            row.put("isAudit", m.getInteger("isAudit"));
        } else {
            Object uo = iCommonService.getOneRecordById("ViewBaseUser", uid);
            if (uo != null) {
                JSONObject uj = FastJsonUtil.toJson(uo);
                row.put("userName", uj.getString("name"));
                row.put("departmentId", uj.getInteger("departmentId"));
                row.put("departmentName", uj.getString("departmentName"));
            }
        }
        return row;
    }

    private JSONArray buildStudentBriefList(List<Integer> userIds, Map<Integer, JSONObject> mergeSample) {
        JSONArray arr = new JSONArray();
        if (userIds == null || userIds.isEmpty()) {
            return arr;
        }
        for (Integer uid : userIds) {
            arr.add(buildOneStudentBriefRow(uid, mergeSample));
        }
        return arr;
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return a;
    }

    @Override
    public Object initTeacherStudentByInternshipId(Integer internshipId, Integer processId, Integer createUserId, String verifyUserId,
                                                    Integer tutorAssignKind, Integer currentVerifyTypeId) {
        validateInitTeacherStudentParams(internshipId, processId, createUserId, verifyUserId);
        int kind = tutorAssignKind == null ? Constant.TUTOR_ASSIGN_KIND.INTERNAL : tutorAssignKind;
        if (kind != Constant.TUTOR_ASSIGN_KIND.INTERNAL && kind != Constant.TUTOR_ASSIGN_KIND.ENTERPRISE) {
            throw BaseResponse.parameterInvalid.error("tutorAssignKind 无效，可选：1=校内导师 2=企业导师");
        }
        int verifyType = (currentVerifyTypeId == null ? 1 : currentVerifyTypeId);
        if (verifyType <= 0) {
            throw BaseResponse.parameterInvalid.error("currentVerifyTypeId 无效，必须为正整数");
        }

        if (kind == Constant.TUTOR_ASSIGN_KIND.ENTERPRISE) {
            ensurePendingSubmitVerifyForRejectedTeacherStudentMerge(internshipId, ENTERPRISE_TUTOR_JOB_CODE);
            List<Object> relStuList = getStudentInternshipSelections(internshipId);
            int[] createdCounts = createTeacherStudentAndVerifyRecords(
                    internshipId, processId, createUserId, verifyUserId, relStuList, null, false, verifyType);
            return buildInitTeacherStudentResult(0, createdCounts[0], createdCounts[1]);
        }

        List<Integer> teacherIds = getTeacherIdsForAssignment(internshipId);

        if (hasVerifyTeacherStudentMergeData(internshipId)) {
            int updatedPendingTeacherCount = reassignTeachersForPendingRecords(internshipId, teacherIds);
            // 已有校内导师数据时，除了重分配待审核记录，还需要为新加入的学生补建记录（增量初始化）
            List<Object> relStuList = getStudentInternshipSelections(internshipId);
            Set<Integer> existingRelInternshipIds = getExistingInternalRelInternshipIdSet(internshipId, new HashSet<>(teacherIds));
            List<Object> newRelStuList = relStuList.stream()
                    .filter(Objects::nonNull)
                    .filter(obj -> {
                        JSONObject json = FastJsonUtil.toJson(obj);
                        Integer relInternshipId = json.getInteger("relationId");
                        if (relInternshipId == null) {
                            relInternshipId = json.getInteger("id");
                        }
                        return relInternshipId != null && !existingRelInternshipIds.contains(relInternshipId);
                    })
                    .collect(Collectors.toList());

            if (newRelStuList.isEmpty()) {
                return buildInitTeacherStudentResult(updatedPendingTeacherCount, 0, 0);
            }

            Map<Integer, Integer> teacherLoadMap = buildTeacherLoadMap(internshipId, teacherIds);
            int[] createdCounts = createTeacherStudentAndVerifyRecords(
                    internshipId, processId, createUserId, verifyUserId, newRelStuList, teacherLoadMap, true, verifyType);
            return buildInitTeacherStudentResult(updatedPendingTeacherCount, createdCounts[0], createdCounts[1]);
        }

        List<Object> relStuList = getStudentInternshipSelections(internshipId);
        Map<Integer, Integer> teacherLoadMap = buildTeacherLoadMap(internshipId, teacherIds);
        int[] createdCounts = createTeacherStudentAndVerifyRecords(
                internshipId, processId, createUserId, verifyUserId, relStuList, teacherLoadMap, true, verifyType);
        return buildInitTeacherStudentResult(0, createdCounts[0], createdCounts[1]);
    }

    @Override
    public Object initInternalTutorByInternshipId(Integer internshipId, Integer processId, Integer createUserId, String verifyUserId,
                                                  Integer currentVerifyTypeId) {
        // 复用原逻辑：校内导师=1
        return initTeacherStudentByInternshipId(internshipId, processId, createUserId, verifyUserId,
                Constant.TUTOR_ASSIGN_KIND.INTERNAL, currentVerifyTypeId);
    }

    @Override
    public Object initEnterpriseTutorByInternshipId(Integer internshipId, Integer processId, Integer createUserId, String verifyUserId,
                                                    Integer currentVerifyTypeId) {
        validateInitTeacherStudentParams(internshipId, processId, createUserId, verifyUserId);
        int verifyType = (currentVerifyTypeId == null ? 1 : currentVerifyTypeId);
        if (verifyType <= 0) {
            throw BaseResponse.parameterInvalid.error("currentVerifyTypeId 无效，必须为正整数");
        }

        ensurePendingSubmitVerifyForRejectedTeacherStudentMerge(internshipId, ENTERPRISE_TUTOR_JOB_CODE);

        // 1) 当前应覆盖的学生集合（岗位审核通过 + 学生选岗审核通过）
        List<Object> relStuList = getStudentInternshipSelections(internshipId);

        // 2) 已存在的企业导师记录：teacherId=0（未分配）或 teacherId 属于企业导师用户集合(jobCode=COMPANY_TUTOR)
        Set<Integer> enterpriseTutorIds = getEnterpriseTutorIdsForAssignment();
        Set<Integer> existingRelInternshipIds = getExistingEnterpriseRelInternshipIdSet(internshipId, enterpriseTutorIds);

        // 3) 差集：只为新增学生补建
        List<Object> newRelStuList = relStuList.stream()
                .filter(Objects::nonNull)
                .filter(obj -> {
                    JSONObject json = FastJsonUtil.toJson(obj);
                    Integer relInternshipId = json.getInteger("relationId");
                    if (relInternshipId == null) {
                        relInternshipId = json.getInteger("id");
                    }
                    return relInternshipId != null && !existingRelInternshipIds.contains(relInternshipId);
                })
                .collect(Collectors.toList());

        if (newRelStuList.isEmpty()) {
            return buildInitTeacherStudentResult(0, 0, 0);
        }

    int[] createdCounts = createTeacherStudentAndVerifyRecords(
        internshipId, processId, createUserId, verifyUserId, newRelStuList, null, false, verifyType);
    return buildInitTeacherStudentResult(0, createdCounts[0], createdCounts[1]);
  }

    private void validateInitTeacherStudentParams(Integer internshipId, Integer processId, Integer createUserId, String verifyUserId) {
        if (internshipId == null || processId == null || createUserId == null || verifyUserId == null) {
            throw BaseResponse.parameterInvalid.error("internshipId、processId、createUserId、verifyUserId 不能为空");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Integer> getTeacherIdsForAssignment(Integer internshipId) {
        // 师生合并视图中 isAudit=NOTPASS 时，按 relationId+processId+tableName 在 MainVerifyProcess 补建 SAVE（待提交）。
        // 退回由其他接口处理，此处不处理 BACK。
        ensurePendingSubmitVerifyForRejectedTeacherStudentMerge(internshipId, TEACHER_JOB_CODE);

        JSONObject teacherSearchKeys = new JSONObject();
        teacherSearchKeys.put("internshipId", internshipId);
        teacherSearchKeys.put("jobCode", TEACHER_JOB_CODE);
        teacherSearchKeys.put("isAudit", Constant.AUDIT_STATUS.PASS);
        Page<Object> teacherPage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewVerifyProcessRelIntershipUserMerge", teacherSearchKeys, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Integer> teacherIds = teacherPage.getContent().stream()
                .map(FastJsonUtil::toJson)
                .map(teacherJson -> teacherJson.getInteger("userId"))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (teacherIds.isEmpty()) {
            throw BaseResponse.moreInfoError.error("未找到审核通过的可分配校内导师（ViewVerifyProcessRelIntershipUserMerge.jobCode=SCHOOL_TEACHER, isAudit=PASS）");
        }
        return teacherIds;
    }

    /**
     * 从 ViewVerifyProcessRelTeacherStudentMerge 筛选本实习、指定导师类型（视图 jobCode，如校内=SCHOOL_TEACHER、企业=COMPANY_TUTOR）、审核未通过(NOTPASS) 的行，
     * 按 relationId、processId、tableName 去重后，若 MainVerifyProcess 仍需补单则插入 isAudit=SAVE。
     */
    @SuppressWarnings("unchecked")
    private void ensurePendingSubmitVerifyForRejectedTeacherStudentMerge(Integer internshipId, String mergeJobCode) {
        JSONObject sk = new JSONObject();
        sk.put("internshipId", internshipId);
        sk.put("isAudit", Constant.AUDIT_STATUS.NOTPASS);
        sk.put("jobCode", mergeJobCode);
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                "ViewVerifyProcessRelTeacherStudentMerge", sk, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> rows = page.getContent();
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (Object row : rows) {
            JSONObject j = FastJsonUtil.toJson(row);
            Integer relationId = j.getInteger("relationId");
            Integer processId = j.getInteger("processId");
            Integer createUserId = j.getInteger("createUserId");
            String tableName = j.getString("tableName");
            if (relationId == null || processId == null || createUserId == null) {
                continue;
            }
            if (tableName == null || tableName.isEmpty()) {
                tableName = TABLE_REL_TEACHER_STUDENT;
            }
            String dedupe = relationId + "_" + processId + "_" + tableName;
            if (!seen.add(dedupe)) {
                continue;
            }
            if (!needsNewPendingSubmitVerifyRecord(relationId, processId, tableName)) {
                continue;
            }
            createPendingSubmitVerifyRecord(internshipId, relationId, processId, createUserId, tableName);
        }
    }

    /**
     * 判断是否需要为「审核不通过」补建待提交记录：<br>
     * 若 MainVerifyProcess 最新一条已是 SAVE/SUBMIT/PASS/BACK，则不建（退回走其他接口）；<br>
     * 若最新一条为 NOTPASS（或无任何记录），则建。
     */
    @SuppressWarnings("unchecked")
    private boolean needsNewPendingSubmitVerifyRecord(Integer relationId, Integer processId, String tableName) {
        JSONObject sk = new JSONObject();
        sk.put("relationId", relationId);
        sk.put("processId", processId);
        sk.put("tableName", tableName);
        Page<Object> vpPage = (Page<Object>) iCommonService.getSomeRecords(
                "MainVerifyProcess", sk, null,
                Sort.by(Sort.Direction.DESC, "id"), 1, 1);
        List<Object> list = vpPage.getContent();
        if (list == null || list.isEmpty()) {
            return true;
        }
        Integer isAudit = FastJsonUtil.toJson(list.get(0)).getInteger("isAudit");
        if (isAudit == null) {
            return true;
        }
        if (isAudit.equals(Constant.AUDIT_STATUS.SAVE) || isAudit.equals(Constant.AUDIT_STATUS.SUBMIT)) {
            return false;
        }
        if (isAudit.equals(Constant.AUDIT_STATUS.PASS)) {
            return false;
        }
        if (isAudit.equals(Constant.AUDIT_STATUS.BACK)) {
            return false;
        }
        return isAudit.equals(Constant.AUDIT_STATUS.NOTPASS);
    }

    private void createPendingSubmitVerifyRecord(Integer internshipId, Integer relationId, Integer processId,
                                                Integer createUserId, String tableName) {
        Object relObj = iCommonService.getOneRecordById("RelProcessInternship", processId);
        if (relObj == null) {
            logger.warn("补建待提交审核失败：未找到流程配置 processId={}", processId);
            return;
        }
        JSONObject relJson = FastJsonUtil.toJson(relObj);
        Integer verifyRoleId = iVerifyProcessService.getVerifyRoleIdByLevel(relJson, 2);
        String verifyUserId = iVerifyProcessService.GetVerifyUserId(verifyRoleId, createUserId, internshipId);
        if (verifyUserId == null) {
            verifyUserId = "";
        }
        JSONObject verifyJson = new JSONObject();
        verifyJson.put("relationId", relationId);
        verifyJson.put("processId", processId);
        verifyJson.put("createUserId", createUserId);
        verifyJson.put("verifyUserId", verifyUserId);
        verifyJson.put("isAudit", Constant.AUDIT_STATUS.SAVE);
        verifyJson.put("reason", "");
        verifyJson.put("tableName", tableName);
        iCommonService.saveOneRecord("MainVerifyProcess", verifyJson);
        logger.info("已补建待提交审核记录 tableName={}, relationId={}, processId={}, createUserId={}",
                tableName, relationId, processId, createUserId);
    }

    @SuppressWarnings("unchecked")
    private Set<Integer> getEnterpriseTutorIdsForAssignment() {
        JSONObject tutorSearchKeys = new JSONObject();
        tutorSearchKeys.put("jobCode", ENTERPRISE_TUTOR_JOB_CODE);
        Page<Object> tutorPage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewBaseUser", tutorSearchKeys, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Integer> tutorIds = tutorPage.getContent().stream()
                .map(FastJsonUtil::toJson)
                .map(tutorJson -> tutorJson.getInteger("id"))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        return new HashSet<>(tutorIds);
    }

    @SuppressWarnings("unchecked")
    private boolean hasVerifyTeacherStudentMergeData(Integer internshipId) {
        JSONObject viewSearchKeys = new JSONObject();
        viewSearchKeys.put("internshipId", internshipId);
        // 只判断“校内导师”记录是否存在，避免企业导师初始化后导致校内流程误走重分配分支
        viewSearchKeys.put("jobCode", TEACHER_JOB_CODE);
        Page<Object> verifyMergePage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewVerifyProcessRelTeacherStudentMerge", viewSearchKeys, null, Sort.unsorted(), 1, 1);
        return verifyMergePage.getContent() != null && !verifyMergePage.getContent().isEmpty();
    }

    @SuppressWarnings("unchecked")
    private int reassignTeachersForPendingRecords(Integer internshipId, List<Integer> teacherIds) {
        JSONObject pendingVerifySearchKeys = new JSONObject();
        pendingVerifySearchKeys.put("internshipId", internshipId);
        pendingVerifySearchKeys.put("isAudit", Constant.AUDIT_STATUS.SAVE);
        // 只重分配“校内导师(jobCode=SCHOOL_TEACHER)”的待审核记录，避免把企业导师(teacherId=0/或jobCode=COMPANY_TUTOR)串改为校内导师
        pendingVerifySearchKeys.put("jobCode", TEACHER_JOB_CODE);
        Page<Object> pendingVerifyPage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewVerifyProcessRelTeacherStudentMerge", pendingVerifySearchKeys, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> pendingVerifyList = pendingVerifyPage.getContent();
        if (pendingVerifyList == null || pendingVerifyList.isEmpty()) {
            return 0;
        }

        List<Object> relTeacherList = getRelTeacherStudentRecords(internshipId);
        Map<Integer, Integer> teacherLoadMap = buildTeacherLoadMapFromRelList(relTeacherList, teacherIds);
        Set<Integer> pendingRelationIds = pendingVerifyList.stream()
                .map(FastJsonUtil::toJson)
                .map(json -> json.getInteger("relationId"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (relTeacherList != null) {
            for (Object relObj : relTeacherList) {
                JSONObject relJson = FastJsonUtil.toJson(relObj);
                Integer relationId = relJson.getInteger("id");
                Integer oldTeacherId = relJson.getInteger("teacherId");
                if (relationId != null && pendingRelationIds.contains(relationId)
                        && oldTeacherId != null && teacherLoadMap.containsKey(oldTeacherId)) {
                    teacherLoadMap.put(oldTeacherId, Math.max(0, teacherLoadMap.get(oldTeacherId) - 1));
                }
            }
        }

        int updatedPendingTeacherCount = 0;
        List<Integer> pendingRelationIdList = new ArrayList<>(pendingRelationIds);
        Collections.shuffle(pendingRelationIdList);
        for (Integer relationId : pendingRelationIdList) {
            Integer selectedTeacherId = chooseBalancedTeacherId(teacherLoadMap);
            JSONObject updateRelTeacherStudentJson = new JSONObject();
            updateRelTeacherStudentJson.put("id", relationId);
            updateRelTeacherStudentJson.put("teacherId", selectedTeacherId);
            iCommonService.saveOneRecord("RelTeacherStudent", updateRelTeacherStudentJson);
            teacherLoadMap.put(selectedTeacherId, teacherLoadMap.get(selectedTeacherId) + 1);
            updatedPendingTeacherCount++;
        }
        return updatedPendingTeacherCount;
    }

    @SuppressWarnings("unchecked")
    private List<Object> getStudentInternshipSelections(Integer internshipId) {
        JSONObject postMergeSearchKeys = new JSONObject();
        postMergeSearchKeys.put("internshipId", internshipId);
        postMergeSearchKeys.put("isAudit", Constant.AUDIT_STATUS.PASS);
        Page<Object> postMergePage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewVerifyProcessInternshipPostMerge", postMergeSearchKeys, null, Sort.unsorted(), 1, POST_PAGE_SIZE);
        List<Object> postMergeList = postMergePage.getContent();
        if (postMergeList == null || postMergeList.isEmpty()) {
            throw BaseResponse.moreInfoError.error("未找到审核已通过的实习岗位记录");
        }

        List<Integer> postIds = postMergeList.stream()
                .map(FastJsonUtil::toJson)
                .map(this::parseInternshipPostIdFromInternshipPostMergeRow)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (postIds.isEmpty()) {
            throw BaseResponse.moreInfoError.error("未找到有效岗位ID");
        }

        JSONObject mergeSearchKeys = new JSONObject();
        mergeSearchKeys.put("internshipPostId", postIds.stream().map(String::valueOf).collect(Collectors.joining(Constant.SPLIT_OPERATOR.COMMA)));
        mergeSearchKeys.put("isAudit", Constant.AUDIT_STATUS.PASS);
        Map<String, String> mergeRepMap = new HashMap<>();
        mergeRepMap.put("internshipPostId", Constant.IN);
        Page<Object> mergePage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewVerifyProcessRelStuInternshipPostMerge", mergeSearchKeys, mergeRepMap, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> mergeList = mergePage.getContent();
        if (mergeList == null || mergeList.isEmpty()) {
            throw BaseResponse.moreInfoError.error("未找到审核已通过的学生岗位选择记录");
        }
        return mergeList;
    }

    private Integer parseInternshipPostIdFromInternshipPostMergeRow(JSONObject row) {
        if (row == null) {
            return null;
        }
        Integer id = row.getInteger("internshipPostId");
        if (id != null) {
            return id;
        }
        String raw = row.getString("internshipPostId");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> getRelTeacherStudentRecords(Integer internshipId) {
        JSONObject relTeacherSearchKeys = new JSONObject();
        relTeacherSearchKeys.put("internshipId", internshipId);
        Page<Object> relTeacherPage = (Page<Object>) iCommonService.getSomeRecords(
                "RelTeacherStudent", relTeacherSearchKeys, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        return relTeacherPage.getContent();
    }

    /**
     * 获取某个实习项目下“校内导师记录”已存在的 relInternshipId 集合，用于增量补建新学生记录。
     * 由于 RelTeacherStudent 未显式区分校内/企业，这里用 teacherId 是否属于校内导师池（jobCode=SCHOOL_TEACHER 查询出的 teacherIds）作为区分依据。
     */
    private Set<Integer> getExistingInternalRelInternshipIdSet(Integer internshipId, Set<Integer> internalTeacherIds) {
        List<Object> relTeacherList = getRelTeacherStudentRecords(internshipId);
        if (relTeacherList == null || relTeacherList.isEmpty() || internalTeacherIds == null || internalTeacherIds.isEmpty()) {
            return new HashSet<>();
        }
        return relTeacherList.stream()
                .filter(Objects::nonNull)
                .map(FastJsonUtil::toJson)
                .filter(json -> {
                    Integer tid = json.getInteger("teacherId");
                    return tid != null && internalTeacherIds.contains(tid);
                })
                .map(json -> json.getInteger("relInternshipId"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Set<Integer> getExistingEnterpriseRelInternshipIdSet(Integer internshipId, Set<Integer> enterpriseTutorIds) {
        List<Object> relTeacherList = getRelTeacherStudentRecords(internshipId);
        if (relTeacherList == null || relTeacherList.isEmpty()) {
            return new HashSet<>();
        }
        return relTeacherList.stream()
                .filter(Objects::nonNull)
                .map(FastJsonUtil::toJson)
                .filter(json -> {
                    Integer tid = json.getInteger("teacherId");
                    // teacherId=0：企业导师未分配占位；或 teacherId 在企业导师用户集合内：已分配过
                    return tid != null && (tid == 0 || (enterpriseTutorIds != null && enterpriseTutorIds.contains(tid)));
                })
                .map(json -> json.getInteger("relInternshipId"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Map<Integer, Integer> buildTeacherLoadMap(Integer internshipId, List<Integer> teacherIds) {
        List<Object> relTeacherList = getRelTeacherStudentRecords(internshipId);
        return buildTeacherLoadMapFromRelList(relTeacherList, teacherIds);
    }

    private Map<Integer, Integer> buildTeacherLoadMapFromRelList(List<Object> relTeacherList, List<Integer> teacherIds) {
        Map<Integer, Integer> teacherLoadMap = new HashMap<>();
        for (Integer teacherId : teacherIds) {
            teacherLoadMap.put(teacherId, 0);
        }
        if (relTeacherList != null) {
            for (Object relObj : relTeacherList) {
                JSONObject relJson = FastJsonUtil.toJson(relObj);
                Integer teacherId = relJson.getInteger("teacherId");
                if (teacherId != null && teacherLoadMap.containsKey(teacherId)) {
                    teacherLoadMap.put(teacherId, teacherLoadMap.get(teacherId) + 1);
                }
            }
        }
        return teacherLoadMap;
    }

    private Integer chooseBalancedTeacherId(Map<Integer, Integer> teacherLoadMap) {
        int minLoad = teacherLoadMap.values().stream().min(Integer::compareTo).orElse(0);
        List<Integer> candidateTeacherIds = teacherLoadMap.entrySet().stream()
                .filter(entry -> entry.getValue() == minLoad)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        return candidateTeacherIds.get(ThreadLocalRandom.current().nextInt(candidateTeacherIds.size()));
    }

    private int[] createTeacherStudentAndVerifyRecords(Integer internshipId, Integer processId, Integer createUserId,
                                                       String verifyUserId, List<Object> relStuList,
                                                       Map<Integer, Integer> teacherLoadMap,
                                                       boolean assignInternalTeacher,
                                                       int currentVerifyTypeId) {
        int createdRelTeacherStudentCount = 0;
        int createdVerifyProcessCount = 0;
        for (Object relStuObj : relStuList) {
            JSONObject relStuJson = FastJsonUtil.toJson(relStuObj);
            Integer relInternshipId = relStuJson.getInteger("relationId");
            if (relInternshipId == null) {
                relInternshipId = relStuJson.getInteger("id");
            }
            if (relInternshipId == null) {
                continue;
            }

            JSONObject relTeacherStudentJson = new JSONObject();
            if (assignInternalTeacher) {
                Integer selectedTeacherId = chooseBalancedTeacherId(teacherLoadMap);
                teacherLoadMap.put(selectedTeacherId, teacherLoadMap.get(selectedTeacherId) + 1);
                relTeacherStudentJson.put("teacherId", selectedTeacherId);
            } else {
                // 企业导师初始化：teacherId 用 0 占位（DB 为 unsigned 时也安全），后续手动分配再更新
                relTeacherStudentJson.put("teacherId", 0);
            }
            relTeacherStudentJson.put("currentVerifyTypeId", currentVerifyTypeId);
            relTeacherStudentJson.put("relInternshipId", relInternshipId);
            relTeacherStudentJson.put("internshipId", internshipId);
            Object savedRelTeacherStudent = iCommonService.saveOneRecord("RelTeacherStudent", relTeacherStudentJson);
            JSONObject savedRelTeacherStudentJson = FastJsonUtil.toJson(savedRelTeacherStudent);
            Integer relationId = savedRelTeacherStudentJson.getInteger("id");
            if (relationId == null) {
                continue;
            }
            createdRelTeacherStudentCount++;

            JSONObject verifyJson = new JSONObject();
            verifyJson.put("relationId", relationId);
            verifyJson.put("processId", processId);
            verifyJson.put("createUserId", createUserId);
            verifyJson.put("verifyUserId", verifyUserId);
            verifyJson.put("isAudit", Constant.AUDIT_STATUS.SAVE);
            verifyJson.put("reason", "");
            verifyJson.put("tableName", "RelTeacherStudent");
            iCommonService.saveOneRecord("MainVerifyProcess", verifyJson);
            createdVerifyProcessCount++;
        }
        return new int[]{createdRelTeacherStudentCount, createdVerifyProcessCount};
    }

    private JSONObject buildInitTeacherStudentResult(int updatedPendingTeacherCount, int createdRelTeacherStudentCount,
                                                     int createdVerifyProcessCount) {
        JSONObject result = new JSONObject();
        result.put("updatedPendingTeacherCount", updatedPendingTeacherCount);
        result.put("createdRelTeacherStudentCount", createdRelTeacherStudentCount);
        result.put("createdVerifyProcessCount", createdVerifyProcessCount);
        return result;
    }



    // ==================== 实习计划流程（需要审核） ====================

    /**
     * 支持 node 为 Fastjson 对象/数组，或前端传来的 JSON 字符串（如 {@code "[{...},{...}]"} ）。
     */
    private Object unwrapAuditProcessNodeArg(Object node) {
        if (!(node instanceof String)) {
            return node;
        }
        String t = ((String) node).trim();
        if (t.isEmpty()) {
            throw BaseResponse.parameterInvalid.error("node 不能为空");
        }
        if (t.startsWith("[")) {
            return JSONArray.parseArray(t);
        }
        if (t.startsWith("{")) {
            return JSONObject.parseObject(t);
        }
        throw BaseResponse.parameterInvalid.error("node 字符串须为 JSON 对象或 JSON 数组");
    }

    @Override
    public Object auditProcess(Object node) {
        node = unwrapAuditProcessNodeArg(node);
        if (node instanceof JSONArray) {
            JSONArray nodesArr = (JSONArray) node;
            if (nodesArr.isEmpty()) {
                throw BaseResponse.parameterInvalid.error("node 数组不能为空");
            }
            List<Object> results = new ArrayList<>(nodesArr.size());
            for (int i = 0; i < nodesArr.size(); i++) {
                Object el = nodesArr.get(i);
                if (!(el instanceof JSONObject)) {
                    throw BaseResponse.parameterInvalid.error("node 数组元素须为对象");
                }
                results.add(auditProcessOne((JSONObject) el));
            }
            return results;
        }
        if (node instanceof JSONObject) {
            return auditProcessOne((JSONObject) node);
        }
        throw BaseResponse.parameterInvalid.error("node 须为 JSON 对象或 JSON 数组");
    }

    /**
     * 实习日志、实习打卡等多级审核：{@code MainVerifyProcess.relationId} 为业务主键。
     *
     * @param clearSubmitOnBack 为 true 时（MainDiary）退回同时 {@code submit=false}；MainSign 传 false，不写入 submit。
     */
    private Object auditProcessMultiLevelRelationBiz(JSONObject node, Object verifyObj, String bizTableName,
            Integer isAudit, boolean clearSubmitOnBack) {
        Object saved = iCommonService.saveOneRecord("MainVerifyProcess", node);
        JSONObject verifyJson = FastJsonUtil.toJson(verifyObj);
        Integer bizId = verifyJson.getInteger("relationId");
        Integer processId = verifyJson.getInteger("processId");
        Integer createUserId = verifyJson.getInteger("createUserId");
        String reason = node.getString("reason");

        if (isAudit != null && isAudit >= 1 && bizId != null
                && reason != null && !reason.isEmpty()) {
            JSONObject updateBiz = new JSONObject();
            updateBiz.put("id", bizId);
            updateBiz.put("remarks", reason);
            iCommonService.saveOneRecord(bizTableName, updateBiz);
        }

        if (isAudit != null && isAudit == Constant.AUDIT_STATUS.PASS && bizId != null) {
            JSONObject bizJson = FastJsonUtil.toJson(iCommonService.getOneRecordById(bizTableName, bizId));
            Integer currentLevel = bizJson.getInteger("currentVerifyTypeId");
            Integer verifyTypeId = bizJson.getInteger("verifyTypeId");
            if (currentLevel != null && verifyTypeId != null) {
                Integer nextLevel = currentLevel + 1;
                JSONObject levelUpdate = new JSONObject();
                levelUpdate.put("id", bizId);
                levelUpdate.put("currentVerifyTypeId", nextLevel);
                iCommonService.saveOneRecord(bizTableName, levelUpdate);
                if (nextLevel <= verifyTypeId) {
                    Integer nextRoleId = iVerifyProcessService.getVerifyRoleIdByLevel(bizJson, nextLevel);
                    if (nextRoleId != null && nextRoleId > 0) {
                        String nextVerifyUserId = iVerifyProcessService.GetVerifyUserId(nextRoleId, createUserId);
                        JSONObject newVerify = new JSONObject();
                        newVerify.put("relationId", bizId);
                        newVerify.put("processId", processId);
                        newVerify.put("createUserId", createUserId);
                        newVerify.put("verifyUserId", nextVerifyUserId);
                        newVerify.put("isAudit", Constant.AUDIT_STATUS.SUBMIT);
                        newVerify.put("tableName", bizTableName);
                        iCommonService.saveOneRecord("MainVerifyProcess", newVerify);
                    }
                }
            }
        } else if (isAudit != null && isAudit == Constant.AUDIT_STATUS.BACK && bizId != null) {
            JSONObject resetBiz = new JSONObject();
            resetBiz.put("id", bizId);
            if (clearSubmitOnBack) {
                resetBiz.put("submit", false);
            }
            resetBiz.put("currentVerifyTypeId", Constant.VERIFY_LEVEL.NO_VERIFY);
            iCommonService.saveOneRecord(bizTableName, resetBiz);
        }
        return saved;
    }

    /**
     * 单条审核推进（原 auditProcess 逻辑）。
     */
    private Object auditProcessOne(JSONObject node) {
        Integer isAudit = node.getInteger("isAudit");
        Integer Id = node.getInteger("id");

        // 提前读取记录以判断 tableName（save 不会改变 tableName，提前读不影响逻辑）
        Object verifyObj = Id != null ? iCommonService.getOneRecordById("MainVerifyProcess", Id) : null;
        String tableName = verifyObj != null ? FastJsonUtil.toJson(verifyObj).getString("tableName") : null;

        // 记录不存在（已被级联删除，如批量审核中岗位满额后 cancelPendingApplicationsIfPostFull 删除了后续项）
        if (Id != null && verifyObj == null) {
            logger.info("auditProcessOne: MainVerifyProcess id={} 不存在或已取消，跳过", Id);
            return null;
        }

        // 非限选题目：学生提交阶段不允许直接传审核通过，统一落到提交待审核。
        if (shouldForceSubmitForNonLimitedTitleSelection(isAudit, tableName, verifyObj)) {
            isAudit = Constant.AUDIT_STATUS.SUBMIT;
            node.put("isAudit", isAudit);
        }

        boolean limitedTitleAutoApproved = false;
        // 限选题目（isLimit=1）在学生提交时自动通过，避免继续进入人工审核链。
        if (shouldAutoApproveLimitedTitleSelectionSubmit(isAudit, tableName, verifyObj)) {
            isAudit = Constant.AUDIT_STATUS.PASS;
            node.put("isAudit", isAudit);
            node.put("verifyUserId", "系统自动通过");
            markLimitedTitleSelectionAsFullyApproved(verifyObj);
            limitedTitleAutoApproved = true;
        }

        // 日志 / 打卡：业务表自带审核配置的多级流程（relationId = 业务主键）；打卡无 submit，退回只重置审核级别
        if ("MainDiary".equals(tableName)) {
            return auditProcessMultiLevelRelationBiz(node, verifyObj, "MainDiary", isAudit, true);
        }
        if ("MainSign".equals(tableName)) {
            return auditProcessMultiLevelRelationBiz(node, verifyObj, "MainSign", isAudit, false);
        }

        if (isAudit != null && isAudit == 1 && Id != null && !limitedTitleAutoApproved) {
            // 审核通过：推进到下一级
            iVerifyProcessService.onVerifyProcessApproved(Id);
        }

        // 学生选题审核未通过：将原因落库到 RelTitleStudent.topicReasons
        if (isAudit != null && isAudit == Constant.AUDIT_STATUS.NOTPASS
                && TABLE_REL_TITLE_STUDENT.equals(tableName) && verifyObj != null) {
            Integer relationId = FastJsonUtil.toJson(verifyObj).getInteger("relationId");
            if (relationId != null) {
                JSONObject updateTopicReason = new JSONObject();
                updateTopicReason.put("id", relationId);
                updateTopicReason.put("topicReasons", node.getString("reason"));
                iCommonService.saveOneRecord(TABLE_REL_TITLE_STUDENT, updateTopicReason);
            }
        }

        // 保存当前审核记录（无论通过/退回，本条记录状态固化为历史）
        Object saved = iCommonService.saveOneRecord("MainVerifyProcess", node);

        if (isAudit != null && (isAudit == 2 || isAudit == 3) && Id != null) {
            boolean isStuPost = "RelStuInternshipPost".equals(tableName);

            if (isStuPost) {
                // 学生报名岗位：退回/未通过 → 软删除选岗记录及其全部关联审核记录
                // nowPersonNum 仅在审核全部通过时递增，拒绝待审中的报名无需递减
                // 这样学生可以干净地重新选择同一岗位，不产生重复活跃记录
                Integer relationId = FastJsonUtil.toJson(verifyObj).getInteger("relationId");
                deleteVerifyProcessByRelationIdAndTableName(relationId, "RelStuInternshipPost");
                iCommonService.deleteRecordByDelflag("RelStuInternshipPost", relationId);
            } else if (isAudit == 3) {
                // 其他类型：退回时新建 isAudit=-1 的记录，允许用户修改后重新提交
                createPendingRecordAfterBack(Id);
            }
        }

        return saved;
    }

    private boolean shouldAutoApproveLimitedTitleSelectionSubmit(Integer isAudit, String tableName, Object verifyObj) {
        // 学生端提交在不同入口可能传 0（提交待审）或 1（直接通过）；
        // 对限选题目统一按自动通过终态处理。
        if (isAudit == null || (isAudit != Constant.AUDIT_STATUS.SUBMIT && isAudit != Constant.AUDIT_STATUS.PASS)) {
            return false;
        }
        if (!TABLE_REL_TITLE_STUDENT.equals(tableName) || verifyObj == null) {
            return false;
        }
        Integer relationId = FastJsonUtil.toJson(verifyObj).getInteger("relationId");
        if (relationId == null) {
            return false;
        }
        Integer isLimit = resolveTitleLimitFlagByRelationId(relationId);
        return isLimit != null && isLimit == 1;
    }

    private Integer resolveTitleLimitFlagByRelationId(Integer relationId) {
        if (relationId == null) {
            return null;
        }
        Object relTitleStudentObj = iCommonService.getOneRecordById("RelTitleStudent", relationId);
        if (relTitleStudentObj == null) {
            return null;
        }
        Integer titleId = FastJsonUtil.toJson(relTitleStudentObj).getInteger("titleId");
        if (titleId == null) {
            return null;
        }
        Object relTitleTeacherObj = iCommonService.getOneRecordById("RelTitleTeacher", titleId);
        if (relTitleTeacherObj == null) {
            return null;
        }
        return FastJsonUtil.toJson(relTitleTeacherObj).getInteger("isLimit");
    }

    private boolean shouldForceSubmitForNonLimitedTitleSelection(Integer isAudit, String tableName, Object verifyObj) {
        if (isAudit == null || isAudit != Constant.AUDIT_STATUS.PASS) {
            return false;
        }
        if (!TABLE_REL_TITLE_STUDENT.equals(tableName) || verifyObj == null) {
            return false;
        }
        JSONObject verifyJson = FastJsonUtil.toJson(verifyObj);
        Integer beforeAudit = verifyJson.getInteger("isAudit");
        // 仅拦截“待提交(-1) -> 通过(1)”这类学生提交越权场景；
        // 老师审核通常是“待审核(0) -> 通过(1)”，不应被拦截。
        if (beforeAudit == null || beforeAudit != Constant.AUDIT_STATUS.SAVE) {
            return false;
        }
        Integer relationId = verifyJson.getInteger("relationId");
        Integer isLimit = resolveTitleLimitFlagByRelationId(relationId);
        return isLimit != null && isLimit != 1;
    }

    private void markLimitedTitleSelectionAsFullyApproved(Object verifyObj) {
        if (verifyObj == null) {
            return;
        }
        JSONObject verifyJson = FastJsonUtil.toJson(verifyObj);
        Integer relationId = verifyJson.getInteger("relationId");
        Integer processId = verifyJson.getInteger("processId");
        if (relationId == null || processId == null) {
            return;
        }

        Object processObj = iCommonService.getOneRecordById("RelProcessInternship", processId);
        if (processObj == null) {
            return;
        }
        Integer verifyTypeId = FastJsonUtil.toJson(processObj).getInteger("verifyTypeId");
        if (verifyTypeId == null) {
            verifyTypeId = Constant.VERIFY_LEVEL.ONE_VERIFY;
        }

        JSONObject updateRelTitleStudent = new JSONObject();
        updateRelTitleStudent.put("id", relationId);
        updateRelTitleStudent.put("currentVerifyTypeId", verifyTypeId + 1);
        RelTitleStudent current = relTitleStudentDao.getByIdAndIsDeletedFalse(relationId);
        Integer internshipId = resolveTitleStudentInternshipId(current);
        if (current != null && internshipId != null) {
            synchronized (titleStudentLock(internshipId)) {
                validateNoFinalTitleConflict(current.getStuId(), internshipId, current.getTitleId(), relationId);
                updateRelTitleStudent.put("internshipId", internshipId);
                updateRelTitleStudent.put("isFinal", 1);
                if (current.getSourceType() == null || current.getSourceType().isBlank()) {
                    updateRelTitleStudent.put("sourceType", TITLE_SOURCE_STUDENT_CANDIDATE);
                }
                updateRelTitleStudent.put("confirmedBy", resolveCurrentUserId());
                updateRelTitleStudent.put("confirmedTime", new Date());
                iCommonService.saveOneRecord(TABLE_REL_TITLE_STUDENT, updateRelTitleStudent);
                releaseOtherCandidatesOfStudent(current.getStuId(), internshipId, relationId);
                releaseOtherCandidatesOfTitle(current.getTitleId(), relationId);
                return;
            }
        }
        iCommonService.saveOneRecord(TABLE_REL_TITLE_STUDENT, updateRelTitleStudent);
    }

    @Override
    public Object createRelTitleStudent(JSONObject node) {
        if (node == null) {
            throw BaseResponse.parameterInvalid.error("node cannot be null");
        }
        Integer titleId = node.getInteger("titleId");
        Integer stuId = node.getInteger("stuId");
        if (titleId == null || stuId == null) {
            throw BaseResponse.parameterInvalid.error("titleId and stuId cannot be null");
        }
        RelTitleTeacher title = relTitleTeacherDao.getByIdAndIsDeletedFalse(titleId);
        if (title == null || title.getInternshipId() == null) {
            throw BaseResponse.parameterInvalid.error("title does not exist or has no internship");
        }
        Integer internshipId = title.getInternshipId();
        boolean teacherAssign = isTeacherAssignedTitleSelection(node);
        Object lock = titleStudentLock(internshipId);
        synchronized (lock) {
            validateNoFinalTitleConflict(stuId, internshipId, titleId, null);
            List<RelTitleStudent> sameRows = relTitleStudentDao.findByTitleIdAndStuIdAndIsDeletedFalse(titleId, stuId);
            if (sameRows != null && !sameRows.isEmpty()) {
                if (!teacherAssign) {
                    throw BaseResponse.moreInfoError.error("student already selected this title");
                }
                RelTitleStudent existing = sameRows.get(0);
                return confirmTeacherAssignedExistingCandidate(existing.getId(), internshipId);
            }
            if (!teacherAssign && hasActiveTitleCandidate(titleId)) {
                throw BaseResponse.moreInfoError.error("title is already held by another candidate");
            }

            if (!node.containsKey("topicReasons")) {
                node.put("topicReasons", null);
            }
            node.put("internshipId", internshipId);
            node.put("sourceType", teacherAssign ? TITLE_SOURCE_TEACHER_ASSIGN : TITLE_SOURCE_STUDENT_CANDIDATE);
            node.put("isFinal", teacherAssign ? 1 : 0);
            if (!node.containsKey("currentVerifyTypeId")) {
                node.put("currentVerifyTypeId", Constant.VERIFY_LEVEL.NO_VERIFY);
            }
            if (teacherAssign) {
                node.put("currentVerifyTypeId", Constant.VERIFY_LEVEL.NO_VERIFY);
                node.put("confirmedBy", resolveCurrentUserId());
                node.put("confirmedTime", new Date());
            }

            Object saved = iCommonService.saveOneRecord(TABLE_REL_TITLE_STUDENT, node);
            JSONObject savedJson = FastJsonUtil.toJson(saved);
            Integer relationId = savedJson.getInteger("id");
            if (teacherAssign) {
                releaseOtherCandidatesOfStudent(stuId, internshipId, relationId);
                releaseOtherCandidatesOfTitle(titleId, relationId);
            } else {
                createFirstVerifyProcessForRelTeacherStudent(relationId, internshipId, stuId, TABLE_REL_TITLE_STUDENT);
            }
            return saved;
        }
    }

    private Object confirmTeacherAssignedExistingCandidate(Integer relationId, Integer internshipId) {
        RelTitleStudent existing = relTitleStudentDao.getByIdAndIsDeletedFalse(relationId);
        if (existing == null) {
            throw BaseResponse.parameterInvalid.error("title selection does not exist");
        }
        validateNoFinalTitleConflict(existing.getStuId(), internshipId, existing.getTitleId(), relationId);
        JSONObject update = new JSONObject();
        update.put("id", relationId);
        update.put("sourceType", TITLE_SOURCE_TEACHER_ASSIGN);
        update.put("isFinal", 1);
        update.put("internshipId", internshipId);
        update.put("currentVerifyTypeId", Constant.VERIFY_LEVEL.NO_VERIFY);
        update.put("confirmedBy", resolveCurrentUserId());
        update.put("confirmedTime", new Date());
        Object saved = iCommonService.saveOneRecord(TABLE_REL_TITLE_STUDENT, update);
        deleteVerifyProcessByRelationIdAndTableName(relationId, TABLE_REL_TITLE_STUDENT);
        releaseOtherCandidatesOfStudent(existing.getStuId(), internshipId, relationId);
        releaseOtherCandidatesOfTitle(existing.getTitleId(), relationId);
        return saved;
    }

    private boolean isTeacherAssignedTitleSelection(JSONObject node) {
        String sourceType = normalizeTitleSourceType(node.getString("sourceType"));
        Integer isFinal = node.getInteger("isFinal");
        return TITLE_SOURCE_TEACHER_ASSIGN.equals(sourceType)
                || (isFinal != null && isFinal == 1);
    }

    private String normalizeTitleSourceType(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return TITLE_SOURCE_STUDENT_CANDIDATE;
        }
        String normalized = sourceType.trim().toUpperCase(Locale.ROOT);
        return TITLE_SOURCE_TEACHER_ASSIGN.equals(normalized)
                ? TITLE_SOURCE_TEACHER_ASSIGN
                : TITLE_SOURCE_STUDENT_CANDIDATE;
    }

    private Object titleStudentLock(Integer internshipId) {
        String key = "title-student:" + (internshipId == null ? "none" : internshipId);
        return TITLE_STUDENT_LOCKS.computeIfAbsent(key, k -> new Object());
    }

    private Integer resolveTitleStudentInternshipId(RelTitleStudent row) {
        if (row == null) {
            return null;
        }
        if (row.getInternshipId() != null) {
            return row.getInternshipId();
        }
        if (row.getTitleId() == null) {
            return null;
        }
        RelTitleTeacher title = relTitleTeacherDao.getByIdAndIsDeletedFalse(row.getTitleId());
        return title != null ? title.getInternshipId() : null;
    }

    private Integer resolveCurrentUserId() {
        try {
            Integer userId = Base.getLoginUserId();
            return userId != null ? userId : 0;
        } catch (Exception ex) {
            return 0;
        }
    }

    private void validateNoFinalTitleConflict(Integer stuId, Integer internshipId, Integer titleId, Integer keepRelationId) {
        if (stuId == null || internshipId == null || titleId == null) {
            throw BaseResponse.parameterInvalid.error("stuId, internshipId and titleId cannot be null");
        }
        boolean studentHasFinal = relTitleStudentDao
                .findByStuIdAndInternshipIdAndIsFinalAndIsDeletedFalse(stuId, internshipId, 1)
                .stream()
                .anyMatch(row -> !row.getId().equals(keepRelationId));
        if (studentHasFinal) {
            throw BaseResponse.moreInfoError.error("student already has a final title in this internship");
        }
        boolean titleHasFinal = relTitleStudentDao
                .findByTitleIdAndIsFinalAndIsDeletedFalse(titleId, 1)
                .stream()
                .anyMatch(row -> !row.getId().equals(keepRelationId));
        if (titleHasFinal) {
            throw BaseResponse.moreInfoError.error("title already has a final student");
        }
    }

    private boolean hasActiveTitleCandidate(Integer titleId) {
        return relTitleStudentDao.findByTitleIdAndIsDeletedFalse(titleId)
                .stream()
                .anyMatch(row -> row.getIsFinal() == null || row.getIsFinal() != 1);
    }

    private void releaseOtherCandidatesOfStudent(Integer stuId, Integer internshipId, Integer keepRelationId) {
        if (stuId == null || internshipId == null) {
            return;
        }
        for (RelTitleStudent row : relTitleStudentDao.findByStuIdAndInternshipIdAndIsDeletedFalse(stuId, internshipId)) {
            releaseCandidate(row, keepRelationId);
        }
    }

    private void releaseOtherCandidatesOfTitle(Integer titleId, Integer keepRelationId) {
        if (titleId == null) {
            return;
        }
        for (RelTitleStudent row : relTitleStudentDao.findByTitleIdAndIsDeletedFalse(titleId)) {
            releaseCandidate(row, keepRelationId);
        }
    }

    private void releaseCandidate(RelTitleStudent row, Integer keepRelationId) {
        if (row == null || row.getId() == null || row.getId().equals(keepRelationId)) {
            return;
        }
        if (row.getIsFinal() != null && row.getIsFinal() == 1) {
            return;
        }
        deleteVerifyProcessByRelationIdAndTableName(row.getId(), TABLE_REL_TITLE_STUDENT);
        iCommonService.deleteRecordByDelflag(TABLE_REL_TITLE_STUDENT, row.getId());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object confirmStudentTopicSelection(JSONObject node) {
        if (node == null) {
            throw BaseResponse.parameterInvalid.error("node cannot be null");
        }
        Integer relationId = node.getInteger("relationId");
        if (relationId == null) {
            relationId = node.getInteger("relTitleStudentId");
        }
        if (relationId == null) {
            throw BaseResponse.parameterInvalid.error("relationId cannot be null");
        }
        RelTitleStudent selection = relTitleStudentDao.getByIdAndIsDeletedFalse(relationId);
        if (selection == null) {
            throw BaseResponse.parameterInvalid.error("title selection does not exist");
        }
        Integer internshipId = resolveTitleStudentInternshipId(selection);
        assertOptionalEquals(node.getInteger("stuId"), selection.getStuId(), "stuId");
        assertOptionalEquals(node.getInteger("titleId"), selection.getTitleId(), "titleId");
        assertOptionalEquals(node.getInteger("internshipId"), internshipId, "internshipId");
        validateNoFinalTitleConflict(selection.getStuId(), internshipId, selection.getTitleId(), relationId);

        JSONObject searchKeys = new JSONObject();
        searchKeys.put("relationId", relationId);
        searchKeys.put("tableName", TABLE_REL_TITLE_STUDENT);
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                TABLE_MAIN_VERIFY_PROCESS, searchKeys, null, Sort.by(Sort.Direction.DESC, "id"), 1, 1);
        if (page == null || page.getContent() == null || page.getContent().isEmpty()) {
            if (selection.getIsFinal() != null && selection.getIsFinal() == 1) {
                return selection;
            }
            throw BaseResponse.moreInfoError.error("title selection has no audit process");
        }
        JSONObject verifyProcessJson = FastJsonUtil.toJson(page.getContent().get(0));
        Integer currentAudit = verifyProcessJson.getInteger("isAudit");
        if (currentAudit != null && currentAudit == Constant.AUDIT_STATUS.PASS
                && selection.getIsFinal() != null && selection.getIsFinal() == 1) {
            return selection;
        }
        if (currentAudit == null || currentAudit != Constant.AUDIT_STATUS.SUBMIT) {
            throw BaseResponse.parameterInvalid.error("only submitted title candidates can be confirmed");
        }
        JSONObject auditNode = new JSONObject();
        auditNode.put("id", verifyProcessJson.getInteger("id"));
        auditNode.put("isAudit", Constant.AUDIT_STATUS.PASS);
        String reason = node.getString("reason");
        if (reason != null) {
            auditNode.put("reason", reason);
        }
        String verifyUserId = node.getString("verifyUserId");
        if (verifyUserId != null && !verifyUserId.isBlank()) {
            auditNode.put("verifyUserId", verifyUserId);
        }
        return auditProcess(auditNode);
    }

    private void assertOptionalEquals(Integer expected, Integer actual, String fieldName) {
        if (expected != null && actual != null && !expected.equals(actual)) {
            throw BaseResponse.parameterInvalid.error(fieldName + " does not match title selection");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object getLatestRejectedTitleSelection(Integer stuId) {
        if (stuId == null) {
            throw BaseResponse.parameterInvalid.error("stuId 不能为空");
        }
        JSONObject sk = new JSONObject();
        sk.put("stuId", stuId);
        sk.put("isAudit", Constant.AUDIT_STATUS.NOTPASS);
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                "ViewVerifyProcessRelTitleStudentMerge", sk, null,
                Sort.by(Sort.Direction.DESC, "vpUpdateTime").and(Sort.by(Sort.Direction.DESC, "id")),
                1, 1);
        List<Object> list = page.getContent();
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object acknowledgeRejectedTitleSelection(Integer relationId, Integer stuId) {
        if (relationId == null || stuId == null) {
            throw BaseResponse.parameterInvalid.error("relationId、stuId 不能为空");
        }
        Object relObj = iCommonService.getOneRecordById(TABLE_REL_TITLE_STUDENT, relationId);
        if (relObj == null) {
            throw BaseResponse.parameterInvalid.error("选题记录不存在");
        }
        JSONObject relJson = FastJsonUtil.toJson(relObj);
        Integer relStuId = relJson.getInteger("stuId");
        if (relStuId == null || !relStuId.equals(stuId)) {
            throw BaseResponse.parameterInvalid.error("该记录不属于当前学生");
        }

        JSONObject sk = new JSONObject();
        sk.put("relationId", relationId);
        sk.put("stuId", stuId);
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                "ViewVerifyProcessRelTitleStudentMerge", sk, null,
                Sort.by(Sort.Direction.DESC, "vpUpdateTime").and(Sort.by(Sort.Direction.DESC, "id")),
                1, 1);
        List<Object> list = page.getContent();
        if (list == null || list.isEmpty()) {
            throw BaseResponse.parameterInvalid.error("未找到该选题审核记录");
        }
        Integer isAudit = FastJsonUtil.toJson(list.get(0)).getInteger("isAudit");
        if (isAudit == null || isAudit != Constant.AUDIT_STATUS.NOTPASS) {
            throw BaseResponse.parameterInvalid.error("仅审核不通过的选题记录允许确认并删除");
        }

        deleteVerifyProcessByRelationIdAndTableName(relationId, TABLE_REL_TITLE_STUDENT);
        iCommonService.deleteRecordByDelflag(TABLE_REL_TITLE_STUDENT, relationId);
        return "确认成功，已清理该条不通过选题记录";
    }

    /**
     * 审核退回后，回退 currentVerifyTypeId 并新建一条 isAudit=-1 的记录，等待用户重新提交。
     * 用户重新提交时只需将该记录的 isAudit 改为 0（通过 auditProcess 接口即可），
     * 无需专门的 resubmit 接口。
     */
    private void createPendingRecordAfterBack(Integer rejectedProcessId) {
        // 1. 读取退回记录（此时 save 已完成，isAudit 已更新为退回状态）
        Object verifyProcessObj = iCommonService.getOneRecordById("MainVerifyProcess", rejectedProcessId);
        if (verifyProcessObj == null) {
            logger.warn("退回后新建记录失败：未找到审核记录 {}", rejectedProcessId);
            return;
        }
        JSONObject verifyProcessJson = FastJsonUtil.toJson(verifyProcessObj);

        Integer relationId  = verifyProcessJson.getInteger("relationId");
        Integer createUserId = verifyProcessJson.getInteger("createUserId");
        Integer processId   = verifyProcessJson.getInteger("processId");
        String  tableName   = verifyProcessJson.getString("tableName");

        // 2. 读取流程配置（使用 processId 查 RelProcessInternship）
        Object relObj = iCommonService.getOneRecordById("RelProcessInternship", processId);
        if (relObj == null) {
            logger.warn("退回后新建记录失败：未找到流程配置 {}", processId);
            return;
        }
        JSONObject relJson = FastJsonUtil.toJson(relObj);

        // 3. 从对应的业务实体表获取 currentVerifyTypeId（每个审核条目独立跟踪审核级别）
        Object entityObj = iCommonService.getOneRecordById(tableName, relationId);
        if (entityObj == null) {
            logger.warn("退回后新建记录失败：未找到业务实体 {} id={}", tableName, relationId);
            return;
        }
        JSONObject entityJson = FastJsonUtil.toJson(entityObj);
        Integer currentVerifyTypeId = entityJson.getInteger("currentVerifyTypeId");

        // 4. 退回时 currentVerifyTypeId - 1，回退到上一级审核
        //    但不低于 2（第一级审核的初始值），第一级退回时保持原级别
        if (currentVerifyTypeId != null && currentVerifyTypeId > 2) {
            currentVerifyTypeId -= 1;
            JSONObject updateJson = new JSONObject();
            updateJson.put("id", relationId);
            updateJson.put("currentVerifyTypeId", currentVerifyTypeId);
            iCommonService.saveOneRecord(tableName, updateJson);
        }

        // 5. 重新计算回退后级别的审核人
        Integer verifyRoleId = getVerifyRoleIdByLevel(relJson, currentVerifyTypeId);
        Integer internshipIdForBack = relJson.getInteger("internshipId");
        String verifyUserId = iVerifyProcessService.GetVerifyUserId(verifyRoleId, createUserId, internshipIdForBack);

        // 5. 新建待提交记录（isAudit=-1）
        JSONObject newVerifyJson = new JSONObject();
        newVerifyJson.put("relationId", relationId);
        newVerifyJson.put("processId", processId);
        newVerifyJson.put("createUserId", createUserId);
        newVerifyJson.put("isAudit", -1);
        newVerifyJson.put("reason", "");
        newVerifyJson.put("tableName", tableName);
        newVerifyJson.put("verifyUserId", verifyUserId);
        iCommonService.saveOneRecord("MainVerifyProcess", newVerifyJson);
    }

    /**
     * 根据审核级别从流程记录JSON中获取对应的审核角色ID
     */
    private Integer getVerifyRoleIdByLevel(JSONObject relJson, Integer verifyLevel) {
        if (relJson == null || verifyLevel == null) {
            return null;
        }
        switch (verifyLevel) {
            case 2:
                return relJson.getInteger("verifyFirstRoleId");
            case 3:
                return relJson.getInteger("verifySecondRoleId");
            case 4:
                return relJson.getInteger("verifyThirdRoleId");
            case 5:
                return relJson.getInteger("verifyFourthRoleId");
            case 6:
                return relJson.getInteger("verifyFifthRoleId");
            default:
                return null;
        }
    }

    @Override
    public Object deleteNewInternship(List<Integer> internshipIds) {
        if (internshipIds == null || internshipIds.isEmpty()) {
            throw BaseResponse.parameterInvalid.error("实习项目ID列表不能为空");
        }

        // // (1) 检查所有id是否在审核流程中，这个不检查了，因为前端已经检查过了。
        // for (Integer internshipId : internshipIds) {
        //     if (internshipId == null) {
        //         continue;
        //     }
        //     JSONObject searchKeys = new JSONObject();
        //     searchKeys.put("internshipId", internshipId);
        //     // 查询时需要考虑 isDeleted，所以使用默认的 getSomeRecords（会自动过滤 isDeleted=false）
        //     @SuppressWarnings("unchecked")
        //     Page<Object> verifyProcessPage = (Page<Object>) iCommonService.getSomeRecords(
        //             "ViewVerifyProcessInternship", searchKeys, null, Sort.unsorted());
        //     List<Object> verifyProcessList = verifyProcessPage.getContent();

        //     // 如果存在审核流程记录，返回错误信息
        //     if (verifyProcessList != null && !verifyProcessList.isEmpty()) {
        //         throw BaseResponse.parameterInvalid.error("当前项目已经进入审核流程，无法删除");
        //     }
        // }
        // (2) 批量删除 MainInternship 中对应的记录（伪删除）
        // 过滤掉 null 值
        List<Integer> validIds = internshipIds.stream()
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toList());
        if (!validIds.isEmpty()) {
            iCommonService.deleteSomeRecords("MainInternship", validIds);
        }

        // (3) 批量删除 RelProcessInternship 中所有 internshipId 是对应 Id 的记录（伪删除）
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("internshipId", String.join(",", validIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList())));
        Map<String, String> regMap = new HashMap<>();
        regMap.put("internshipId", Constant.IN); // internshipId IN (列表)
        iCommonService.deleteSomeRecords("RelProcessInternship", searchKeys, regMap, null);

        // (4) 批量删除 MainVerifyProcess 中 relationId 在 validIds 集合且 tableName 是 "MainInternship" 的所有记录（伪删除）
        JSONObject verifySearchKeys = new JSONObject();
        verifySearchKeys.put("relationId", String.join(",", validIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList())));
        verifySearchKeys.put("tableName", "MainInternship");
        Map<String, String> verifyRegMap = new HashMap<>();
        verifyRegMap.put("relationId", Constant.IN); // relationId IN (列表)
        iCommonService.deleteSomeRecords("MainVerifyProcess", verifySearchKeys, verifyRegMap, null);

        return "删除成功";
    }

    // ==================== 老师申报题目（保存/提交、需审核与无需审核） ====================

    private static final String TABLE_REL_TEACHER_STUDENT = "RelTeacherStudent";
    private static final String TABLE_REL_TITLE_TEACHER = "RelTitleTeacher";
    private static final String TABLE_REL_TITLE_STUDENT = "RelTitleStudent";
    private static final String TABLE_MAIN_VERIFY_PROCESS = "MainVerifyProcess";

    @Override
    public void createFirstVerifyProcessForRelTeacherStudent(Integer relationId, Integer internshipId, Integer createUserId, String tableName) {
        if (relationId == null || internshipId == null || createUserId == null) {
            logger.warn("createFirstVerifyProcessForRelTeacherStudent 参数不完整: relationId={}, internshipId={}, createUserId={}", relationId, internshipId, createUserId);
            return;
        }
        String targetTableName = TABLE_REL_TEACHER_STUDENT;
        if (TABLE_REL_TITLE_TEACHER.equals(tableName)) {
            targetTableName = TABLE_REL_TITLE_TEACHER;
        } else if (TABLE_REL_TITLE_STUDENT.equals(tableName)) {
            targetTableName = TABLE_REL_TITLE_STUDENT;
        } else if (tableName != null && !TABLE_REL_TEACHER_STUDENT.equals(tableName)) {
            logger.warn("createFirstVerifyProcessForRelTeacherStudent 未识别 tableName={}, 使用默认 {}", tableName, TABLE_REL_TEACHER_STUDENT);
        }
        String processTypeCode = Constant.PROCESS_TYPE.INTERNAL_TEACHER_DECLARE_TOPIC;
        if (TABLE_REL_TITLE_STUDENT.equals(targetTableName)) {
            processTypeCode = Constant.PROCESS_TYPE.INTERNAL_STUDENT_TEACHER_MATCH;
        }
        // 1. 获取流程配置（ViewRelProcessInternship 的 id 即 RelProcessInternship.id）
        Object processObj = iVerifyProcessService.GetInternshipProcess(internshipId, processTypeCode);
        if (processObj == null) {
            logger.warn("未找到实习流程配置, internshipId={}, processTypeCode={}", internshipId, processTypeCode);
            return;
        }
        JSONObject processJson = FastJsonUtil.toJson(processObj);
        Integer processId = processJson.getInteger("id");
        if (processId == null) {
            return;
        }
        Integer verifyTypeId = processJson.getInteger("verifyTypeId");
        boolean needsVerify = verifyTypeId != null && verifyTypeId >= Constant.VERIFY_LEVEL.ONE_VERIFY;

        // Keep the business row aligned with the process definition so the first approval
        // advances from the real starting level instead of from NO_VERIFY.
        JSONObject levelUpdate = new JSONObject();
        levelUpdate.put("id", relationId);
        levelUpdate.put("currentVerifyTypeId",
                needsVerify ? Constant.VERIFY_LEVEL.ONE_VERIFY : Constant.VERIFY_LEVEL.NO_VERIFY);
        iCommonService.saveOneRecord(targetTableName, levelUpdate);

        String verifyUserId;
        int isAudit;
        if (needsVerify) {
            Integer verifyFirstRoleId = processJson.getInteger("verifyFirstRoleId");
            verifyUserId = iVerifyProcessService.GetVerifyUserId(verifyFirstRoleId, createUserId, internshipId);
            isAudit = Constant.AUDIT_STATUS.SAVE; // -1 保存未提交
        } else {
            verifyUserId = "系统自动通过";
            isAudit = Constant.AUDIT_STATUS.PASS; // 1 直接通过
        }

        JSONObject verifyJson = new JSONObject();
        verifyJson.put("relationId", relationId);
        verifyJson.put("processId", processId);
        verifyJson.put("createUserId", createUserId);
        verifyJson.put("verifyUserId", verifyUserId);
        verifyJson.put("isAudit", isAudit);
        verifyJson.put("reason", "");
        verifyJson.put("tableName", targetTableName);
        iCommonService.saveOneRecord(TABLE_MAIN_VERIFY_PROCESS, verifyJson);
    }

    @Override
    public void deleteVerifyProcessByRelationIdAndTableName(Integer relationId, String tableName) {
        if (relationId == null || tableName == null || tableName.isEmpty()) {
            return;
        }
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("relationId", relationId);
        searchKeys.put("tableName", tableName);
        @SuppressWarnings("unchecked")
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                TABLE_MAIN_VERIFY_PROCESS, searchKeys, null, Sort.unsorted(), 1, 100);
        List<Object> content = page.getContent();
        if (content != null) {
            for (Object obj : content) {
                JSONObject verifyProcessJson = FastJsonUtil.toJson(obj);
                Integer id = verifyProcessJson.getInteger("id");
                if (id != null) {
                    iCommonService.deleteRecordByDelflag(TABLE_MAIN_VERIFY_PROCESS, id);
                }
            }
        }
    }
}
