package newcms.service.impl;

import jakarta.annotation.Resource;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.entity.db.MainInternship;
import newcms.entity.db.MainVerifyProcess;
import newcms.entity.db.RelProcessInternship;
import newcms.repository.db.MainVerifyProcessDao;
import newcms.repository.db.RelProcessInternshipDao;
import newcms.service.ICommonService;
import newcms.service.IVerifyProcessService;
import newcms.utils.FastJsonUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson.JSONObject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 审核流程服务实现类
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class VerifyProcessServiceImpl extends Base implements IVerifyProcessService {

    @Resource
    private ICommonService iCommonService;

    @Resource
    private MainVerifyProcessDao mainVerifyProcessDao;

    @Resource
    private RelProcessInternshipDao relProcessInternshipDao;

    @Override
    @SuppressWarnings("unchecked")
    public Object GetInternshipFoundProcess(Integer internshipId) {
        if (internshipId == null) {
            throw BaseResponse.parameterInvalid.error("实习项目ID不能为空");
        }

        // 查找流程关联记录（取第一条）
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("internshipId", internshipId);
        Page<Object> relPage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewRelProcessInternship", searchKeys, null,
                Sort.by(Sort.Direction.ASC, "theOrder"), 1, 1);
        List<Object> relList = relPage.getContent();
        
        if (relList == null || relList.isEmpty()) {
            throw BaseResponse.moreInfoError.error("未找到实习项目的流程配置，请先创建流程模板");
        }
        
        return relList.get(0);
    }

    @Override
    @SuppressWarnings("unchecked")
    public String GetVerifyUserId(Integer verifyFirstRoleId, Integer createUserId) {
        if (verifyFirstRoleId == null) {
            // 如果没有审核角色ID，返回空字符串
            return "";
        }
        if (createUserId == null) {
            throw BaseResponse.parameterInvalid.error("创建用户ID不能为空");
        }

        // (1) 获取当前用户的 schoolId
        Object currentUserObj = iCommonService.getOneRecordById("ViewBaseUser", createUserId);
        if (currentUserObj == null) {
            throw BaseResponse.moreInfoError.error("未找到当前用户信息");
        }
        JSONObject currentUserJson = FastJsonUtil.toJson(currentUserObj);
        Integer schoolId = currentUserJson.getInteger("schoolId");
        
        if (schoolId == null) {
            // 如果当前用户没有 schoolId，返回空字符串
            return "";
        }

        // (2) 查找 ViewBaseUser 中所有 schoolId 相同的用户，获取他们的 id 列表
        JSONObject schoolSearchKeys = new JSONObject();
        schoolSearchKeys.put("schoolId", schoolId);
        Page<Object> schoolUserPage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewBaseUser", schoolSearchKeys, null, Sort.unsorted());
        List<Object> schoolUserList = schoolUserPage.getContent();
        Set<Integer> schoolUserIds = schoolUserList.stream()
                .map(user -> {
                    JSONObject userJson = FastJsonUtil.toJson(user);
                    return userJson.getInteger("id");
                })
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        if (schoolUserIds.isEmpty()) {
            return "";
        }

        // (3) 查找 RelUserRole 中所有 roleId = verifyFirstRoleId 的记录
        JSONObject roleSearchKeys = new JSONObject();
        roleSearchKeys.put("roleId", verifyFirstRoleId);
        Page<Object> userRolePage = (Page<Object>) iCommonService.getSomeRecords(
                "RelUserRole", roleSearchKeys, null, Sort.unsorted());
        List<Object> userRoleList = userRolePage.getContent();

        // (4) 筛选出 userId 在 schoolUserIds 中的记录，并提取 userId
        List<Integer> verifyUserIds = userRoleList.stream()
                .map(role -> {
                    JSONObject roleJson = FastJsonUtil.toJson(role);
                    return roleJson.getInteger("userId");
                })
                .filter(userId -> userId != null && schoolUserIds.contains(userId))
                .distinct()
                .collect(Collectors.toList());

        // (5) 将 userId 用竖线连接成字符串
        if (verifyUserIds.isEmpty()) {
            return "";
        }

        return verifyUserIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining("|"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public int refreshPendingVerifyUsers() {
        // 查询所有待审核记录（isAudit = 0）
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("isAudit", 0);
        Page<MainVerifyProcess> pendingPage = (Page<MainVerifyProcess>) iCommonService.getSomeRecords(
                "MainVerifyProcess", searchKeys, null, Sort.unsorted());
        List<MainVerifyProcess> pendingList = pendingPage.getContent();

        int updatedCount = 0;
        for (MainVerifyProcess verifyProcess : pendingList) {
            boolean updated = refreshSingleVerifyProcess(verifyProcess);
            if (updated) {
                updatedCount++;
            }
        }

        logger.info("刷新待审核记录完成，共更新 {} 条记录", updatedCount);
        return updatedCount;
    }

    @Override
    @SuppressWarnings("unchecked")
    public int refreshPendingVerifyUsersByUser(Integer userId) {
        if (userId == null) {
            return 0;
        }

        // 获取用户的 schoolId
        Object userObj = iCommonService.getOneRecordById("ViewBaseUser", userId);
        if (userObj == null) {
            return 0;
        }
        JSONObject userJson = FastJsonUtil.toJson(userObj);
        Integer schoolId = userJson.getInteger("schoolId");

        if (schoolId == null) {
            return 0;
        }

        // 查询所有待审核记录（isAudit = 0）
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("isAudit", 0);
        Page<MainVerifyProcess> pendingPage = (Page<MainVerifyProcess>) iCommonService.getSomeRecords(
                "MainVerifyProcess", searchKeys, null, Sort.unsorted());
        List<MainVerifyProcess> pendingList = pendingPage.getContent();

        int updatedCount = 0;
        for (MainVerifyProcess verifyProcess : pendingList) {
            // 检查该审核记录的创建人是否与变更用户在同一学校
            Object creatorObj = iCommonService.getOneRecordById("ViewBaseUser", verifyProcess.getCreateUserId());
            if (creatorObj == null) {
                continue;
            }
            JSONObject creatorJson = FastJsonUtil.toJson(creatorObj);
            Integer creatorSchoolId = creatorJson.getInteger("schoolId");

            // 只更新同一学校的审核记录
            if (schoolId.equals(creatorSchoolId)) {
                boolean updated = refreshSingleVerifyProcess(verifyProcess);
                if (updated) {
                    updatedCount++;
                }
            }
        }

        logger.info("刷新用户 {} 相关的待审核记录完成，共更新 {} 条记录", userId, updatedCount);
        return updatedCount;
    }

    /**
     * 刷新单条审核记录的 verifyUserId
     *
     * @param verifyProcess 审核记录
     * @return 是否更新成功
     */
    @SuppressWarnings("unchecked")
    private boolean refreshSingleVerifyProcess(MainVerifyProcess verifyProcess) {
        try {
            Integer relationId = verifyProcess.getRelationId();
            String tableName = verifyProcess.getTableName();
            Integer createUserId = verifyProcess.getCreateUserId();

            if (relationId == null || tableName == null || createUserId == null) {
                return false;
            }

            // 根据 tableName 获取对应的流程关联记录
            Integer verifyRoleId = getVerifyRoleIdFromRelation(relationId, tableName);
            if (verifyRoleId == null) {
                return false;
            }

            // 重新计算 verifyUserId
            String newVerifyUserId = GetVerifyUserId(verifyRoleId, createUserId);
            String oldVerifyUserId = verifyProcess.getVerifyUserId();

            // 如果有变化则更新
            if (!newVerifyUserId.equals(oldVerifyUserId)) {
                verifyProcess.setVerifyUserId(newVerifyUserId);
                mainVerifyProcessDao.save(verifyProcess);
                logger.debug("更新审核记录 {} 的 verifyUserId: {} -> {}",
                        verifyProcess.getId(), oldVerifyUserId, newVerifyUserId);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.warn("刷新审核记录 {} 失败: {}", verifyProcess.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * 根据关联ID和表名获取审核角色ID
     *
     * @param relationId 关联ID
     * @param tableName 表名
     * @return 审核角色ID
     */
    private Integer getVerifyRoleIdFromRelation(Integer relationId, String tableName) {
        // 目前主要支持 RelProcessInternship 表
        if ("RelProcessInternship".equals(tableName)) {
            Object relObj = iCommonService.getOneRecordById("ViewRelProcessInternship", relationId);
            if (relObj != null) {
                JSONObject relJson = FastJsonUtil.toJson(relObj);
                // 返回第一级审核角色ID（后续可扩展支持多级审核）
                return relJson.getInteger("verifyFirstRoleId");
            }
        }
        // 可以在此扩展支持其他表
        return null;
    }

    @Override
    public int activateStartedProcesses() {
        LocalDateTime now = LocalDateTime.now();
        logger.info("开始检查已到开始时间的流程，当前时间: {}", now);

        // 查找所有已到开始时间的流程
        List<RelProcessInternship> startedProcesses = relProcessInternshipDao.findStartedProcesses(now);

        int createdCount = 0;
        for (RelProcessInternship process : startedProcesses) {
            try {
                boolean created = createVerifyProcessIfNotExists(process);
                if (created) {
                    createdCount++;
                }
            } catch (Exception e) {
                logger.warn("为流程 {} 创建审核记录失败: {}", process.getId(), e.getMessage());
            }
        }

        logger.info("流程激活检查完成，共创建 {} 条审核记录", createdCount);
        return createdCount;
    }

    /**
     * 如果不存在审核记录则创建
     *
     * @param process 流程关联记录
     * @return 是否创建了新记录
     */
    private boolean createVerifyProcessIfNotExists(RelProcessInternship process) {
        Integer relationId = process.getId();
        String tableName = "RelProcessInternship";

        // 检查是否已存在审核记录
        if (mainVerifyProcessDao.existsByRelationIdAndTableNameAndIsDeletedFalse(relationId, tableName)) {
            logger.debug("流程 {} 已存在审核记录，跳过", relationId);
            return false;
        }

        // 获取实习项目信息以获取创建人ID
        Integer internshipId = process.getInternshipId();
        if (internshipId == null) {
            logger.warn("流程 {} 缺少实习项目ID", relationId);
            return false;
        }

        Object internshipObj = iCommonService.getOneRecordById("MainInternship", internshipId);
        if (internshipObj == null) {
            logger.warn("未找到实习项目 {}", internshipId);
            return false;
        }

        JSONObject internshipJson = FastJsonUtil.toJson(internshipObj);
        Integer creatorId = internshipJson.getInteger("creatorId");
        if (creatorId == null) {
            logger.warn("实习项目 {} 缺少创建人ID", internshipId);
            return false;
        }

        // 判断是否需要审核：verifyTypeId 为 0 或 null 表示不需要审核
        Integer verifyTypeId = process.getVerifyTypeId();
        boolean needsVerify = verifyTypeId != null && verifyTypeId != 0;

        // 获取审核用户ID（即使不需要审核也计算，用于记录）
        Integer verifyFirstRoleId = process.getVerifyFirstRoleId();
        String verifyUserId = needsVerify ? GetVerifyUserId(verifyFirstRoleId, creatorId) : "";

        // 创建审核记录
        MainVerifyProcess verifyProcess = new MainVerifyProcess();
        verifyProcess.setRelationId(relationId);
        verifyProcess.setCreateUserId(creatorId);
        verifyProcess.setVerifyUserId(verifyUserId);
        // 需要审核：isAudit = -1（未提交）；不需要审核：isAudit = 1（直接通过）
        verifyProcess.setIsAudit(needsVerify ? -1 : 1);
        verifyProcess.setReason("");
        verifyProcess.setTableName(tableName);

        mainVerifyProcessDao.save(verifyProcess);
        logger.info("为流程 {} 创建审核记录成功，需要审核: {}，isAudit: {}",
                relationId, needsVerify, verifyProcess.getIsAudit());
        return true;
    }
}
