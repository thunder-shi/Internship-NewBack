package newcms.service.impl;

import jakarta.annotation.Resource;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.entity.db.MainVerifyProcess;
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
                // 根据当前审核级别返回对应的审核角色ID
                Integer currentVerifyTypeId = relJson.getInteger("currentVerifyTypeId");
                return getVerifyRoleIdByLevel(relJson, currentVerifyTypeId);
            }
        }
        // 可以在此扩展支持其他表
        return null;
    }

    @Override
    public Object activateProcess(JSONObject node) {
        if (node == null) {
            throw BaseResponse.parameterInvalid.error("node 参数不能为空");
        }
        // 从 node 中获取参数
        Integer relationId = node.getInteger("relationId");
        Integer processId = node.getInteger("processId");
        Integer createUserId = node.getInteger("createUserId");
        String tableName = node.getString("tableName");
        // 检查是否已存在审核记录（需要 relationId 和 tableName）
        // if (relationId != null && tableName != null) {
        //     if (mainVerifyProcessDao.existsByRelationIdAndTableNameAndIsDeletedFalse(relationId, tableName)) {
        //         logger.debug("流程 {} 已存在审核记录，跳过", relationId);
        //         return null;
        //     }
        // }
        // 获取关联记录以获取审核配置信息（需要 tableName 和 relationId）
        Object relObj = null;
        if (tableName != null && relationId != null) {
            relObj = iCommonService.getOneRecordById("RelProcessInternship", processId);
            if (relObj == null) {
                logger.warn("未找到关联记录 {}，表名: {}", relationId, tableName);
            }
        }
        JSONObject relJson = null;
        if (relObj != null) {
            relJson = FastJsonUtil.toJson(relObj);
        }
        // 判断是否需要审核：verifyTypeId = 1 表示不需要审核，verifyTypeId >= 2 表示需要审核
        Integer verifyTypeId = null;
        boolean needsVerify = false;
        if (relJson != null) {
            verifyTypeId = relJson.getInteger("verifyTypeId");
            // verifyTypeId: 1-无需审核, 2-一级审核, 3-二级审核, 4-三级审核, 5-四级审核, 6-五级审核
            needsVerify = verifyTypeId != null && verifyTypeId >= 2;
            // 初始化 RelProcessInternship.currentVerifyTypeId（如果表是 RelProcessInternship）
            // Integer currentVerifyTypeId = needsVerify ? 2 : 1;
            // relJson.put("currentVerifyTypeId", currentVerifyTypeId);
            // iCommonService.saveOneRecord(tableName, relJson);
        }
        // 获取当前级别的审核角色ID
        Integer verifyRoleId = null;
        if (needsVerify && relJson != null) {
            verifyRoleId = getVerifyRoleIdByLevel(relJson, 2); // 从第一级审核开始
        }
        String verifyUserId = "";
        if (needsVerify && createUserId != null) {
            verifyUserId = GetVerifyUserId(verifyRoleId, createUserId);
        }
        // 创建审核记录
        MainVerifyProcess verifyProcess = new MainVerifyProcess();
        verifyProcess.setRelationId(relationId);
        verifyProcess.setProcessId(processId);
        verifyProcess.setCreateUserId(createUserId);
        verifyProcess.setVerifyUserId(verifyUserId);
        // 需要审核：isAudit = -1（未提交）；不需要审核：isAudit = 1（直接通过）
        verifyProcess.setIsAudit(needsVerify ? -1 : 1);
        verifyProcess.setReason("");
        verifyProcess.setTableName(tableName);
        MainVerifyProcess savedProcess = mainVerifyProcessDao.save(verifyProcess);
        logger.info("为流程 {} 创建审核记录成功，需要审核: {}，isAudit: {}，当前审核级别: {}",
                relationId, needsVerify, savedProcess.getIsAudit(), needsVerify ? 2 : 1);
        return savedProcess;
    }

    @Override
    public void onVerifyProcessApproved(Integer verifyProcessId) {
        if (verifyProcessId == null) {
            return;
        }

        // 获取审核通过的记录
        MainVerifyProcess verifyProcess = mainVerifyProcessDao.findById(verifyProcessId).orElse(null);
        if (verifyProcess == null) {
            logger.warn("未找到审核记录 {}", verifyProcessId);
            return;
        }

        Integer relationId = verifyProcess.getRelationId();
        String tableName = verifyProcess.getTableName();
        Integer createUserId = verifyProcess.getCreateUserId();

        if (!"RelProcessInternship".equals(tableName) || relationId == null) {
            return;
        }

        // 获取 RelProcessInternship 记录
        Object relObj = iCommonService.getOneRecordById("RelProcessInternship", relationId);
        if (relObj == null) {
            logger.warn("未找到流程关联记录 {}", relationId);
            return;
        }
        JSONObject relJson = FastJsonUtil.toJson(relObj);
        Integer currentVerifyTypeId = relJson.getInteger("currentVerifyTypeId");
        Integer verifyTypeId = relJson.getInteger("verifyTypeId");

        if (currentVerifyTypeId == null) {
            currentVerifyTypeId = 2; // 默认一级审核
        }
        if (verifyTypeId == null) {
            verifyTypeId = 1; // 默认无需审核
        }

        int nextLevel = currentVerifyTypeId + 1;
        if (nextLevel <= verifyTypeId) {
            System.out.println("nextLevel = " + nextLevel + ", verifyTypeId = " + verifyTypeId);
            // 还有下一级审核：更新 RelProcessInternship.currentVerifyTypeId，创建新审核记录
            relJson.put("currentVerifyTypeId", nextLevel);
            iCommonService.saveOneRecord("RelProcessInternship", relJson);

            // 获取下一级审核角色ID
            Integer nextVerifyRoleId = getVerifyRoleIdByLevel(relJson, nextLevel);
            String nextVerifyUserId = GetVerifyUserId(nextVerifyRoleId, createUserId);

            // 创建下一级审核记录
            MainVerifyProcess nextVerifyProcess = new MainVerifyProcess();
            nextVerifyProcess.setRelationId(relationId);
            nextVerifyProcess.setCreateUserId(createUserId);
            nextVerifyProcess.setVerifyUserId(nextVerifyUserId);
            nextVerifyProcess.setIsAudit(0); // 待审核
            nextVerifyProcess.setReason("");
            nextVerifyProcess.setTableName(tableName);
            mainVerifyProcessDao.save(nextVerifyProcess);

            logger.info("审核记录 {} 通过，流程 {} 进入下一级审核 {}，创建新审核记录 {}",
                    verifyProcessId, relationId, nextLevel, nextVerifyProcess.getId());
        } else {
            // 审核全部完成，将 currentVerifyTypeId 更新为 nextLevel（verifyTypeId + 1）
            // 方便后续通过 currentVerifyTypeId > verifyTypeId 直接判断审核是否结束
            relJson.put("currentVerifyTypeId", nextLevel);
            iCommonService.saveOneRecord("RelProcessInternship", relJson);

            logger.info("审核记录 {} 通过，流程 {} 审核全部完成（currentVerifyTypeId 更新为 {}，verifyTypeId {}）",
                    verifyProcessId, relationId, nextLevel, verifyTypeId);
        }
    }

    /**
     * 根据审核级别从流程记录JSON中获取对应的审核角色ID
     *
     * @param relJson 流程关联记录JSON
     * @param verifyLevel 审核级别（2-6）
     * @return 对应级别的审核角色ID
     */
    private Integer getVerifyRoleIdByLevel(JSONObject relJson, Integer verifyLevel) {
        if (relJson == null || verifyLevel == null) {
            return null;
        }
        switch (verifyLevel) {
            case 2: return relJson.getInteger("verifyFirstRoleId");
            case 3: return relJson.getInteger("verifySecondRoleId");
            case 4: return relJson.getInteger("verifyThirdRoleId");
            case 5: return relJson.getInteger("verifyFourthRoleId");
            case 6: return relJson.getInteger("verifyFifthRoleId");
            default: return null;
        }
    }

}
