package newcms.service.impl;

import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.base.Constant;
import newcms.service.ICommonService;
import newcms.service.IInternshipService;
import newcms.service.IVerifyProcessService;
import newcms.utils.FastJsonUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class InternshipServiceImpl extends Base implements IInternshipService {
    private static final int TEACHER_JOB_ID = 4;
    private static final int LARGE_PAGE_SIZE = 100000;
    private static final int POST_PAGE_SIZE = 10000;

    @Resource
    private ICommonService iCommonService;

    @Resource
    private IVerifyProcessService iVerifyProcessService;

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
            verifyUserId = iVerifyProcessService.GetVerifyUserId(verifyFirstRoleId, createUserId);
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
    public Object getAvailableUsersForInternship(Integer internshipId, Integer jobId, Integer page, Integer size, Sort sort) {
        if (internshipId == null || jobId == null) {
            throw BaseResponse.parameterInvalid.error("internshipId 和 jobId 不能为空");
        }

        int pageNum = (page == null || page < 1) ? Constant.DEFAULT_PAGE : page;
        int pageSize = (size == null || size < 1) ? Constant.DEFAULT_SIZE : size;

        // 1. 查出该实习项目下已经在 RelIntershipUser 中关联过的 userId 列表（只看未删除的关联）
        JSONObject relSearchKeys = new JSONObject();
        relSearchKeys.put("internshipId", internshipId);
        relSearchKeys.put("isDeleted", 0);

        @SuppressWarnings("unchecked")
        Page<Object> relPage = (Page<Object>) iCommonService.getSomeRecords(
                "RelIntershipUser", relSearchKeys, null, Sort.unsorted()
        );

        List<Object> relList = relPage.getContent();
        List<Integer> usedUserIds = relList.stream()
                .map(FastJsonUtil::toJson)
                .map(json -> json.getInteger("userId"))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 2. 组装 BaseUser 的查询条件：
        //    - jobId = 前端传入 jobId
        //    - id NOT IN (已关联且未删除的 userId 列表)
        JSONObject userSearchKeys = new JSONObject();
        userSearchKeys.put("jobId", jobId);

        Map<String, String> repMap = new HashMap<>();

        if (!usedUserIds.isEmpty()) {
            String idStr = usedUserIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(Constant.SPLIT_OPERATOR.COMMA));
            userSearchKeys.put("id", idStr);
            // 使用 Constant.NOT_IN 实现 “id NOT IN (...)”
            repMap.put("id", Constant.NOT_IN);
        }

        // 3. 通过通用的 getSomeRecords 分页查询 BaseUser（带排序）
        Sort finalSort = (sort == null) ? Sort.unsorted() : sort;
        return iCommonService.getSomeRecords(
                "BaseUser",
                userSearchKeys,
                repMap,
                finalSort,
                pageNum,
                pageSize
        );
    }

    @Override
    public Object initTeacherStudentByInternshipId(Integer internshipId, Integer processId, Integer createUserId, String verifyUserId) {
        validateInitTeacherStudentParams(internshipId, processId, createUserId, verifyUserId);
        List<Integer> teacherIds = getTeacherIdsForAssignment();

        if (hasVerifyTeacherStudentMergeData(internshipId)) {
            int updatedPendingTeacherCount = reassignTeachersForPendingRecords(internshipId, teacherIds);
            return buildInitTeacherStudentResult(updatedPendingTeacherCount, 0, 0);
        }

        List<Object> relStuList = getStudentInternshipSelections(internshipId);
        Map<Integer, Integer> teacherLoadMap = buildTeacherLoadMap(internshipId, teacherIds);
        int[] createdCounts = createTeacherStudentAndVerifyRecords(
                internshipId, processId, createUserId, verifyUserId, relStuList, teacherLoadMap
        );
        return buildInitTeacherStudentResult(0, createdCounts[0], createdCounts[1]);
    }

    private void validateInitTeacherStudentParams(Integer internshipId, Integer processId, Integer createUserId, String verifyUserId) {
        if (internshipId == null || processId == null || createUserId == null || verifyUserId == null) {
            throw BaseResponse.parameterInvalid.error("internshipId、processId、createUserId、verifyUserId 不能为空");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Integer> getTeacherIdsForAssignment() {
        JSONObject teacherSearchKeys = new JSONObject();
        teacherSearchKeys.put("jobId", TEACHER_JOB_ID);
        Page<Object> teacherPage = (Page<Object>) iCommonService.getSomeRecords(
                "BaseUser", teacherSearchKeys, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Integer> teacherIds = teacherPage.getContent().stream()
                .map(FastJsonUtil::toJson)
                .map(teacherJson -> teacherJson.getInteger("id"))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (teacherIds.isEmpty()) {
            throw BaseResponse.moreInfoError.error("未找到可分配教师（BaseUser.jobId=4）");
        }
        return teacherIds;
    }

    @SuppressWarnings("unchecked")
    private boolean hasVerifyTeacherStudentMergeData(Integer internshipId) {
        JSONObject viewSearchKeys = new JSONObject();
        viewSearchKeys.put("internshipId", internshipId);
        Page<Object> verifyMergePage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewVerifyProcessRelTeacherStudentMerge", viewSearchKeys, null, Sort.unsorted(), 1, 1);
        return verifyMergePage.getContent() != null && !verifyMergePage.getContent().isEmpty();
    }

    @SuppressWarnings("unchecked")
    private int reassignTeachersForPendingRecords(Integer internshipId, List<Integer> teacherIds) {
        JSONObject pendingVerifySearchKeys = new JSONObject();
        pendingVerifySearchKeys.put("internshipId", internshipId);
        pendingVerifySearchKeys.put("isAudit", Constant.AUDIT_STATUS.SAVE);
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
        JSONObject postSearchKeys = new JSONObject();
        postSearchKeys.put("internshipId", internshipId);
        Page<Object> postPage = (Page<Object>) iCommonService.getSomeRecords(
                "MainInternshipPost", postSearchKeys, null, Sort.unsorted(), 1, POST_PAGE_SIZE);
        List<Object> postList = postPage.getContent();
        if (postList == null || postList.isEmpty()) {
            throw BaseResponse.moreInfoError.error("未找到实习岗位记录");
        }

        List<Integer> postIds = postList.stream()
                .map(FastJsonUtil::toJson)
                .map(postJson -> postJson.getInteger("id"))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (postIds.isEmpty()) {
            throw BaseResponse.moreInfoError.error("未找到有效岗位ID");
        }

        JSONObject relStuSearchKeys = new JSONObject();
        relStuSearchKeys.put("internshipPostId", postIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        Map<String, String> relStuRepMap = new HashMap<>();
        relStuRepMap.put("internshipPostId", Constant.IN);
        Page<Object> relStuPage = (Page<Object>) iCommonService.getSomeRecords(
                "RelStuInternship", relStuSearchKeys, relStuRepMap, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> relStuList = relStuPage.getContent();
        if (relStuList == null || relStuList.isEmpty()) {
            throw BaseResponse.moreInfoError.error("未找到学生岗位选择记录");
        }
        return relStuList;
    }

    @SuppressWarnings("unchecked")
    private List<Object> getRelTeacherStudentRecords(Integer internshipId) {
        JSONObject relTeacherSearchKeys = new JSONObject();
        relTeacherSearchKeys.put("internshipId", internshipId);
        Page<Object> relTeacherPage = (Page<Object>) iCommonService.getSomeRecords(
                "RelTeacherStudent", relTeacherSearchKeys, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        return relTeacherPage.getContent();
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
                                                       Map<Integer, Integer> teacherLoadMap) {
        int createdRelTeacherStudentCount = 0;
        int createdVerifyProcessCount = 0;
        for (Object relStuObj : relStuList) {
            JSONObject relStuJson = FastJsonUtil.toJson(relStuObj);
            Integer relInternshipId = relStuJson.getInteger("id");
            if (relInternshipId == null) {
                continue;
            }

            Integer selectedTeacherId = chooseBalancedTeacherId(teacherLoadMap);
            teacherLoadMap.put(selectedTeacherId, teacherLoadMap.get(selectedTeacherId) + 1);

            JSONObject relTeacherStudentJson = new JSONObject();
            relTeacherStudentJson.put("teacherId", selectedTeacherId);
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

    @Override
    public Object auditProcess(JSONObject node) {
        Integer isAudit = node.getInteger("isAudit");
        Integer Id = node.getInteger("id");
        if (isAudit != null && isAudit == 1 && Id != null) {
            // 审核通过：推进到下一级
            iVerifyProcessService.onVerifyProcessApproved(Id);
        }

        // 保存当前审核记录（无论通过/退回，本条记录状态固化为历史）
        Object saved = iCommonService.saveOneRecord("MainVerifyProcess", node);

        if (isAudit != null && (isAudit == 2 || isAudit == 3) && Id != null) {
            // 退回：立即在同一审核级别新建一条 isAudit=-1（保存未提交）的记录，
            // 原退回记录保留作为历史，前端可查看完整退回原因链
            createPendingRecordAfterBack(Id);
        }

        return saved;
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
        String verifyUserId = iVerifyProcessService.GetVerifyUserId(verifyRoleId, createUserId);

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
        return switch (verifyLevel) {
            case 2 -> relJson.getInteger("verifyFirstRoleId");
            case 3 -> relJson.getInteger("verifySecondRoleId");
            case 4 -> relJson.getInteger("verifyThirdRoleId");
            case 5 -> relJson.getInteger("verifyFourthRoleId");
            case 6 -> relJson.getInteger("verifyFifthRoleId");
            default -> null;
        };
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
        } else if (tableName != null && !TABLE_REL_TEACHER_STUDENT.equals(tableName)) {
            logger.warn("createFirstVerifyProcessForRelTeacherStudent 未识别 tableName={}, 使用默认 {}", tableName, TABLE_REL_TEACHER_STUDENT);
        }
        // 1. 获取「老师申报题目」流程配置（ViewRelProcessInternship 的 id 即 RelProcessInternship.id）
        Object processObj = iVerifyProcessService.GetInternshipProcess(internshipId, Constant.PROCESS_TYPE.INTERNAL_TEACHER_DECLARE_TOPIC);
        if (processObj == null) {
            logger.warn("未找到老师申报题目流程配置, internshipId={}", internshipId);
            return;
        }
        JSONObject processJson = FastJsonUtil.toJson(processObj);
        Integer processId = processJson.getInteger("id");
        if (processId == null) {
            return;
        }
        Integer verifyTypeId = processJson.getInteger("verifyTypeId");
        boolean needsVerify = verifyTypeId != null && verifyTypeId >= Constant.VERIFY_LEVEL.ONE_VERIFY;

        String verifyUserId;
        int isAudit;
        if (needsVerify) {
            Integer verifyFirstRoleId = processJson.getInteger("verifyFirstRoleId");
            verifyUserId = iVerifyProcessService.GetVerifyUserId(verifyFirstRoleId, createUserId);
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
