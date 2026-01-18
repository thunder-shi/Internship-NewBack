package newcms.service.impl;

import jakarta.annotation.Resource;
import newcms.base.BaseResponse;
import newcms.entity.db.BaseInternshipType;
import newcms.entity.db.MainVerifyProcess;
import newcms.entity.db.RelProcessInternship;
import newcms.entity.db.RelProcessInternshipType;
import newcms.entity.db.ViewRelProcessInternship;
import newcms.repository.db.BaseInternshipTypeDao;
import newcms.repository.db.BaseUserDao;
import newcms.repository.db.MainVerifyProcessDao;
import newcms.repository.db.RelProcessInternshipDao;
import newcms.repository.db.RelProcessInternshipTypeDao;
import newcms.repository.db.ViewRelProcessInternshipDao;
import newcms.service.IInternshipService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class InternshipServiceImpl implements IInternshipService {

    @Resource
    private RelProcessInternshipTypeDao relProcessInternshipTypeDao;

    @Resource
    private RelProcessInternshipDao relProcessInternshipDao;

    @Resource
    private ViewRelProcessInternshipDao viewRelProcessInternshipDao;

    @Resource
    private MainVerifyProcessDao mainVerifyProcessDao;

    @Resource
    private BaseInternshipTypeDao baseInternshipTypeDao;

    @Resource
    private BaseUserDao baseUserDao;

    private static final String PROCESS_TYPE_NAME_PLAN = "实习计划制定";

    // 默认占位角色ID，表示该级审核未配置（角色name='--'）
    private static final Integer DEFAULT_PLACEHOLDER_ROLE_ID = 17;

    @Override
    public void copyProcessFromTemplate(Integer internshipId, Integer internshipTypeId) {
        if (internshipId == null || internshipTypeId == null) {
            return;
        }

        // 1. 根据实习类型ID查找对应的流程模板配置
        List<RelProcessInternshipType> processTypeList =
            relProcessInternshipTypeDao.findByInternshipTypeIdAndIsDeletedFalse(internshipTypeId);

        // 2. 将流程模板复制到实习项目流程关联表
        for (RelProcessInternshipType processType : processTypeList) {
            RelProcessInternship processInternship = new RelProcessInternship();
            processInternship.setInternshipId(internshipId);
            processInternship.setProcessTypeId(processType.getProcessTypeId());
            processInternship.setVerifyTypeId(processType.getVerifyTypeId());
            processInternship.setVerifyFirstRoleId(processType.getVerifyFirstRoleId());
            processInternship.setVerifySecondRoleId(processType.getVerifySecondRoleId());
            processInternship.setVerifyThirdRoleId(processType.getVerifyThirdRoleId());
            processInternship.setVerifyFourthRoleId(processType.getVerifyFourthRoleId());
            processInternship.setVerifyFifthRoleId(processType.getVerifyFifthRoleId());
            // 复制流程排序号
            processInternship.setTheOrder(processType.getTheOrder());

            relProcessInternshipDao.save(processInternship);
        }
    }

    @Override
    public void updateProcessFromTemplate(Integer internshipId, Integer newInternshipTypeId) {
        if (internshipId == null) {
            return;
        }

        // 1. 删除该实习项目的旧流程配置（逻辑删除）
        relProcessInternshipDao.deleteByInternshipId(internshipId);

        // 2. 如果有新模板，复制新模板的流程配置
        if (newInternshipTypeId != null) {
            copyProcessFromTemplate(internshipId, newInternshipTypeId);
        }
    }

    @Override
    public void createVerifyProcessIfNeeded(Integer internshipId, Integer internshipTypeId,
                                             Integer createUserId, Integer isAudit) {
        if (internshipId == null || internshipTypeId == null || createUserId == null || isAudit == null) {
            return;
        }

        // 1. 查找该实习项目的"实习计划制定"流程
        Optional<ViewRelProcessInternship> processOpt = viewRelProcessInternshipDao
                .findByInternshipIdAndProcessTypeNameAndIsDeletedFalse(internshipId, PROCESS_TYPE_NAME_PLAN);

        if (processOpt.isEmpty()) {
            // 没有"实习计划制定"流程，不需要创建审核记录
            return;
        }

        ViewRelProcessInternship process = processOpt.get();

        // 2. 检查是否需要审核（一级审核角色有效）
        Integer verifyFirstRoleId = process.getVerifyFirstRoleId();
        if (!isValidRoleId(verifyFirstRoleId)) {
            // 不需要审核（角色无效或为默认占位角色）
            return;
        }

        // 3. 获取实习类型模板的所属院系
        Optional<BaseInternshipType> internshipTypeOpt = baseInternshipTypeDao.findById(internshipTypeId);
        if (internshipTypeOpt.isEmpty()) {
            return;
        }
        Integer universityId = internshipTypeOpt.get().getUniversityId();

        // 4. 根据一级审核角色和院系查询有资格审核的用户
        List<Integer> verifyUserIds = baseUserDao.findUserIdsByRoleIdAndDepartmentId(verifyFirstRoleId, universityId);
        String verifyUserIdStr = verifyUserIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining("|"));

        // 5. 创建审核记录
        MainVerifyProcess verifyProcess = new MainVerifyProcess();
        verifyProcess.setRelationId(process.getId());
        verifyProcess.setTableName("RelProcessInternship");
        verifyProcess.setCreateUserId(createUserId);
        verifyProcess.setVerifyUserId(verifyUserIdStr);
        verifyProcess.setIsAudit(isAudit);

        mainVerifyProcessDao.save(verifyProcess);
    }

    @Override
    public void doVerify(Integer mainVerifyProcessId, Integer action, Integer operatorId, String reason) {
        if (mainVerifyProcessId == null || action == null || operatorId == null) {
            throw BaseResponse.moreInfoError.error("参数不能为空");
        }

        // 1. 查询当前审核记录
        Optional<MainVerifyProcess> recordOpt = mainVerifyProcessDao.findById(mainVerifyProcessId);
        if (recordOpt.isEmpty()) {
            throw BaseResponse.moreInfoError.error("审核记录不存在");
        }
        MainVerifyProcess currentRecord = recordOpt.get();

        // 2. 验证状态：只有待审核状态才能操作
        if (currentRecord.getIsAudit() != 0) {
            throw BaseResponse.moreInfoError.error("当前状态不允许审核操作");
        }

        // 3. 验证权限：操作人必须在审核人列表中
        String verifyUserIdStr = currentRecord.getVerifyUserId();
        List<Integer> verifyUserIds = parseVerifyUserIds(verifyUserIdStr);
        if (!verifyUserIds.contains(operatorId)) {
            throw BaseResponse.moreInfoError.error("您没有审核权限");
        }

        // 4. 获取关联的流程配置
        Optional<ViewRelProcessInternship> processOpt = viewRelProcessInternshipDao.findById(currentRecord.getRelationId());
        if (processOpt.isEmpty()) {
            throw BaseResponse.moreInfoError.error("关联的流程配置不存在");
        }
        ViewRelProcessInternship relProcess = processOpt.get();

        // 5. 确定当前审核级别
        int currentStep = determineCurrentStep(currentRecord.getRelationId());

        // 6. 根据操作类型执行不同逻辑
        switch (action) {
            case 1: // 通过
                handleApprove(currentRecord, relProcess, operatorId, reason, currentStep);
                break;
            case 2: // 不通过
                handleReject(currentRecord, operatorId, reason);
                break;
            case 3: // 退回
                handleReturn(currentRecord, operatorId, reason);
                break;
            default:
                throw BaseResponse.moreInfoError.error("无效的操作类型");
        }
    }

    @Override
    public void resubmit(Integer mainVerifyProcessId, Integer operatorId) {
        if (mainVerifyProcessId == null || operatorId == null) {
            throw BaseResponse.moreInfoError.error("参数不能为空");
        }

        // 1. 查询审核记录
        Optional<MainVerifyProcess> recordOpt = mainVerifyProcessDao.findById(mainVerifyProcessId);
        if (recordOpt.isEmpty()) {
            throw BaseResponse.moreInfoError.error("审核记录不存在");
        }
        MainVerifyProcess record = recordOpt.get();

        // 2. 验证是最新记录
        Optional<MainVerifyProcess> latestOpt = mainVerifyProcessDao
                .findFirstByRelationIdAndIsDeletedFalseOrderByIdDesc(record.getRelationId());
        if (latestOpt.isEmpty() || !latestOpt.get().getId().equals(record.getId())) {
            throw BaseResponse.moreInfoError.error("只能操作最新的审核记录");
        }

        // 3. 验证状态：只有退回状态才能重新提交
        if (record.getIsAudit() != 3) {
            throw BaseResponse.moreInfoError.error("当前状态不允许重新提交");
        }

        // 4. 验证权限：只有原流程创建人可以重新提交
        // 需要找到第一条审核记录的 createUserId
        List<MainVerifyProcess> allRecords = mainVerifyProcessDao
                .findByRelationIdAndTableNameAndIsDeletedFalseOrderByIdAsc(record.getRelationId(), "RelProcessInternship");
        if (allRecords.isEmpty()) {
            throw BaseResponse.moreInfoError.error("未找到原始审核记录");
        }
        Integer originalCreatorId = allRecords.get(0).getCreateUserId();
        if (!originalCreatorId.equals(operatorId)) {
            throw BaseResponse.moreInfoError.error("只有流程创建人可以重新提交");
        }

        // 5. 获取关联的流程配置
        Optional<ViewRelProcessInternship> processOpt = viewRelProcessInternshipDao.findById(record.getRelationId());
        if (processOpt.isEmpty()) {
            throw BaseResponse.moreInfoError.error("关联的流程配置不存在");
        }
        ViewRelProcessInternship relProcess = processOpt.get();

        // 6. 确定当前应该回到哪一级审核
        int currentStep = determineCurrentStep(record.getRelationId());
        Integer currentRoleId = getRoleIdByStep(relProcess, currentStep);

        if (!isValidRoleId(currentRoleId)) {
            throw BaseResponse.moreInfoError.error("无法确定当前审核角色");
        }

        // 7. 获取实习类型的所属院系
        Integer universityId = getUniversityIdByInternshipTypeId(relProcess.getInternshipTypeId());

        // 8. 获取有资格审核的用户列表
        List<Integer> newVerifyUserIds = baseUserDao.findUserIdsByRoleIdAndDepartmentId(currentRoleId, universityId);
        String newVerifyUserIdStr = newVerifyUserIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining("|"));

        // 9. 更新当前记录，回到待审核状态
        record.setIsAudit(0);
        record.setVerifyUserId(newVerifyUserIdStr);
        mainVerifyProcessDao.save(record);
    }

    @Override
    public Map<String, Object> getVerifyProgress(Integer internshipId) {
        Map<String, Object> result = new HashMap<>();

        if (internshipId == null) {
            return result;
        }

        // 1. 查找该项目的"实习计划制定"流程
        Optional<ViewRelProcessInternship> processOpt = viewRelProcessInternshipDao
                .findByInternshipIdAndProcessTypeNameAndIsDeletedFalse(internshipId, PROCESS_TYPE_NAME_PLAN);

        if (processOpt.isEmpty()) {
            result.put("hasProcess", false);
            return result;
        }

        ViewRelProcessInternship process = processOpt.get();
        result.put("hasProcess", true);
        result.put("relProcessInternshipId", process.getId());
        result.put("verifyTypeName", process.getVerifyTypeName());

        // 2. 获取所有审核记录
        List<MainVerifyProcess> records = mainVerifyProcessDao
                .findByRelationIdAndTableNameAndIsDeletedFalseOrderByIdAsc(process.getId(), "RelProcessInternship");

        if (records.isEmpty()) {
            result.put("isAudit", -1);
            result.put("currentStep", 0);
            result.put("records", Collections.emptyList());
            return result;
        }

        // 3. 获取最新审核状态
        MainVerifyProcess latestRecord = records.get(records.size() - 1);
        result.put("isAudit", latestRecord.getIsAudit());

        // 4. 确定当前审核级别
        int currentStep = determineCurrentStep(process.getId());
        result.put("currentStep", currentStep);

        // 5. 获取当前审核角色名称
        String currentVerifyRole = getRoleNameByStep(process, currentStep);
        result.put("currentVerifyRole", currentVerifyRole);

        // 6. 构建审核记录详情列表
        List<Map<String, Object>> recordDetails = new ArrayList<>();
        for (MainVerifyProcess record : records) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("id", record.getId());
            detail.put("isAudit", record.getIsAudit());
            detail.put("reason", record.getReason());
            detail.put("createTime", record.getCreateTime());
            detail.put("updateTime", record.getUpdateTime());

            // 获取创建人姓名
            String createUserName = baseUserDao.findNameById(record.getCreateUserId());
            detail.put("createUserName", createUserName);

            // 根据状态获取审核人信息
            Integer isAudit = record.getIsAudit();
            if (isAudit != null && (isAudit == 1 || isAudit == 2 || isAudit == 3)) {
                // 已审核，verifyUserId 是单个用户ID
                try {
                    Integer verifyUserId = Integer.parseInt(record.getVerifyUserId());
                    String verifyUserName = baseUserDao.findNameById(verifyUserId);
                    detail.put("verifyUserName", verifyUserName);
                } catch (NumberFormatException e) {
                    detail.put("verifyUserName", null);
                }
            } else {
                // 待审核，verifyUserId 是多个用户ID
                List<Integer> verifyUserIds = parseVerifyUserIds(record.getVerifyUserId());
                if (!verifyUserIds.isEmpty()) {
                    List<String> verifyUserNames = baseUserDao.findNamesByIds(verifyUserIds);
                    detail.put("verifyUserNames", String.join(",", verifyUserNames));
                }
            }

            recordDetails.add(detail);
        }
        result.put("records", recordDetails);

        // 7. 添加各级审核角色信息
        result.put("verifyFirstRole", process.getVerifyFirstRoleName());
        result.put("verifySecondRole", process.getVerifySecondRoleName());
        result.put("verifyThirdRole", process.getVerifyThirdRoleName());
        result.put("verifyFourthRole", process.getVerifyFourthRoleName());
        result.put("verifyFifthRole", process.getVerifyFifthRoleName());

        return result;
    }

    @Override
    public boolean canOperate(Integer mainVerifyProcessId, Integer userId) {
        if (mainVerifyProcessId == null || userId == null) {
            return false;
        }

        Optional<MainVerifyProcess> recordOpt = mainVerifyProcessDao.findById(mainVerifyProcessId);
        if (recordOpt.isEmpty()) {
            return false;
        }

        MainVerifyProcess record = recordOpt.get();

        // 检查是否是最新记录
        Optional<MainVerifyProcess> latestOpt = mainVerifyProcessDao
                .findFirstByRelationIdAndIsDeletedFalseOrderByIdDesc(record.getRelationId());
        if (latestOpt.isEmpty() || !latestOpt.get().getId().equals(record.getId())) {
            return false;
        }

        Integer isAudit = record.getIsAudit();

        if (isAudit == 0) {
            // 待审核：检查用户是否在审核人列表中
            List<Integer> verifyUserIds = parseVerifyUserIds(record.getVerifyUserId());
            return verifyUserIds.contains(userId);
        } else if (isAudit == 3) {
            // 退回：只有原流程创建人可以重新提交
            List<MainVerifyProcess> allRecords = mainVerifyProcessDao
                    .findByRelationIdAndTableNameAndIsDeletedFalseOrderByIdAsc(record.getRelationId(), "RelProcessInternship");
            if (!allRecords.isEmpty()) {
                return allRecords.get(0).getCreateUserId().equals(userId);
            }
        }

        return false;
    }

    @Override
    public void updateVerifyStatus(Integer internshipId, Integer internshipTypeId,
                                    Integer createUserId, Integer newIsAudit) {
        if (internshipId == null || newIsAudit == null) {
            return;
        }

        // 1. 查找该实习项目的"实习计划制定"流程
        Optional<ViewRelProcessInternship> processOpt = viewRelProcessInternshipDao
                .findByInternshipIdAndProcessTypeNameAndIsDeletedFalse(internshipId, PROCESS_TYPE_NAME_PLAN);

        if (processOpt.isEmpty()) {
            // 没有"实习计划制定"流程
            return;
        }

        ViewRelProcessInternship process = processOpt.get();

        // 2. 查询是否已有审核记录
        Optional<MainVerifyProcess> existingRecordOpt = mainVerifyProcessDao
                .findFirstByRelationIdAndIsDeletedFalseOrderByIdDesc(process.getId());

        if (existingRecordOpt.isEmpty()) {
            // 没有审核记录，如果 newIsAudit=0 则创建新记录
            if (newIsAudit == 0) {
                createVerifyProcessIfNeeded(internshipId, internshipTypeId, createUserId, newIsAudit);
            }
            return;
        }

        MainVerifyProcess existingRecord = existingRecordOpt.get();
        Integer currentIsAudit = existingRecord.getIsAudit();

        // 3. 根据当前状态和目标状态处理
        if (currentIsAudit == -1 && newIsAudit == 0) {
            // 从暂存变为提交：更新现有记录的状态
            existingRecord.setIsAudit(0);
            mainVerifyProcessDao.save(existingRecord);
        } else if (currentIsAudit == 3 && newIsAudit == 0) {
            // 从退回状态重新提交：调用 resubmit 方法
            resubmit(existingRecord.getId(), createUserId);
        }
        // 其他情况不处理（如已经是0/1/2状态）
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 处理审核通过
     */
    private void handleApprove(MainVerifyProcess currentRecord, ViewRelProcessInternship relProcess,
                                Integer operatorId, String reason, int currentStep) {
        // 更新当前记录为通过
        currentRecord.setIsAudit(1);
        currentRecord.setVerifyUserId(String.valueOf(operatorId));
        currentRecord.setReason(reason);
        mainVerifyProcessDao.save(currentRecord);

        // 检查是否有下一级审核
        // 排除无效的角色ID：null、0、或默认占位角色ID(17)
        Integer nextRoleId = getNextRoleId(relProcess, currentStep);
        if (isValidRoleId(nextRoleId)) {
            // 获取实习类型的所属院系
            Integer universityId = getUniversityIdByInternshipTypeId(relProcess.getInternshipTypeId());

            // 获取下一级有资格审核的用户列表
            List<Integer> nextVerifyUserIds = baseUserDao.findUserIdsByRoleIdAndDepartmentId(nextRoleId, universityId);
            String nextVerifyUserIdStr = nextVerifyUserIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining("|"));

            // 创建下一级审核记录
            MainVerifyProcess newRecord = new MainVerifyProcess();
            newRecord.setRelationId(currentRecord.getRelationId());
            newRecord.setTableName("RelProcessInternship");
            newRecord.setCreateUserId(operatorId);
            newRecord.setVerifyUserId(nextVerifyUserIdStr);
            newRecord.setIsAudit(0);
            mainVerifyProcessDao.save(newRecord);
        }
        // 如果没有下一级，审核流程结束
    }

    /**
     * 处理审核不通过
     */
    private void handleReject(MainVerifyProcess currentRecord, Integer operatorId, String reason) {
        currentRecord.setIsAudit(2);
        currentRecord.setVerifyUserId(String.valueOf(operatorId));
        currentRecord.setReason(reason);
        mainVerifyProcessDao.save(currentRecord);
        // 审核流程终止
    }

    /**
     * 处理审核退回
     */
    private void handleReturn(MainVerifyProcess currentRecord, Integer operatorId, String reason) {
        currentRecord.setIsAudit(3);
        currentRecord.setVerifyUserId(String.valueOf(operatorId));
        currentRecord.setReason(reason);
        mainVerifyProcessDao.save(currentRecord);
        // 等待流程创建人修改后重新提交，不创建新记录
    }

    /**
     * 确定当前审核级别
     * @param relationId 关联的流程实例ID
     * @return 当前审核级别 (1-5)
     */
    private int determineCurrentStep(Integer relationId) {
        int passedCount = mainVerifyProcessDao.countPassedByRelationId(relationId);
        return passedCount + 1;
    }

    /**
     * 根据当前步骤获取下一级审核角色ID
     */
    private Integer getNextRoleId(ViewRelProcessInternship relProcess, int currentStep) {
        switch (currentStep) {
            case 1: return relProcess.getVerifySecondRoleId();
            case 2: return relProcess.getVerifyThirdRoleId();
            case 3: return relProcess.getVerifyFourthRoleId();
            case 4: return relProcess.getVerifyFifthRoleId();
            default: return null;
        }
    }

    /**
     * 根据步骤获取对应的审核角色ID
     */
    private Integer getRoleIdByStep(ViewRelProcessInternship relProcess, int step) {
        switch (step) {
            case 1: return relProcess.getVerifyFirstRoleId();
            case 2: return relProcess.getVerifySecondRoleId();
            case 3: return relProcess.getVerifyThirdRoleId();
            case 4: return relProcess.getVerifyFourthRoleId();
            case 5: return relProcess.getVerifyFifthRoleId();
            default: return null;
        }
    }

    /**
     * 根据步骤获取对应的审核角色名称
     */
    private String getRoleNameByStep(ViewRelProcessInternship relProcess, int step) {
        switch (step) {
            case 1: return relProcess.getVerifyFirstRoleName();
            case 2: return relProcess.getVerifySecondRoleName();
            case 3: return relProcess.getVerifyThirdRoleName();
            case 4: return relProcess.getVerifyFourthRoleName();
            case 5: return relProcess.getVerifyFifthRoleName();
            default: return null;
        }
    }

    /**
     * 根据实习类型ID获取所属院系ID
     */
    private Integer getUniversityIdByInternshipTypeId(Integer internshipTypeId) {
        Optional<BaseInternshipType> typeOpt = baseInternshipTypeDao.findById(internshipTypeId);
        return typeOpt.map(BaseInternshipType::getUniversityId).orElse(null);
    }

    /**
     * 解析 verifyUserId 字符串为用户ID列表
     * @param verifyUserIdStr 格式: "61|62|63" 或 "61"
     * @return 用户ID列表
     */
    private List<Integer> parseVerifyUserIds(String verifyUserIdStr) {
        if (verifyUserIdStr == null || verifyUserIdStr.isEmpty() || "-1".equals(verifyUserIdStr)) {
            return Collections.emptyList();
        }
        return Arrays.stream(verifyUserIdStr.split("\\|"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }

    /**
     * 判断角色ID是否有效（非空、非0、非默认占位角色）
     * @param roleId 角色ID
     * @return true-有效, false-无效
     */
    private boolean isValidRoleId(Integer roleId) {
        return roleId != null && roleId != 0 && !roleId.equals(DEFAULT_PLACEHOLDER_ROLE_ID);
    }
}
