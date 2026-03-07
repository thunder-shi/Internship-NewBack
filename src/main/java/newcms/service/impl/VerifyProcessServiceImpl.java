package newcms.service.impl;

import jakarta.annotation.Resource;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.base.Constant;
import newcms.entity.db.MainVerifyProcess;
import newcms.service.ICommonService;
import newcms.service.IVerifyProcessService;
import newcms.utils.FastJsonUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson.JSONObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Override
    @SuppressWarnings("unchecked")
    public Object GetInternshipProcess(Integer internshipId, String processTypeCode) {
        if (internshipId == null) {
            throw BaseResponse.parameterInvalid.error("实习项目ID不能为空");
        }

        // 如果 processTypeCode 为空，则使用默认值
        if (processTypeCode == null || processTypeCode.trim().isEmpty()) {
            processTypeCode = Constant.PROCESS_TYPE.INTERNSHIP_PLAN_MAKE;
        }

        // 查找流程关联记录（取第一条）
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("internshipId", internshipId);
        searchKeys.put("processTypeCode", processTypeCode);
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
        if (verifyFirstRoleId == null || verifyFirstRoleId == 0) {
            // 如果没有审核角色ID（null 或 0 均视为未配置），返回空字符串
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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
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

        // 1. 批量查出同校所有用户ID，避免逐条查询（解决 N+1 问题）
        JSONObject schoolSearchKeys = new JSONObject();
        schoolSearchKeys.put("schoolId", schoolId);
        Page<Object> schoolUserPage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewBaseUser", schoolSearchKeys, null, Sort.unsorted());
        Set<Integer> schoolUserIds = schoolUserPage.getContent().stream()
                .map(u -> FastJsonUtil.toJson(u).getInteger("id"))
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        if (schoolUserIds.isEmpty()) {
            return 0;
        }

        // 2. 查询所有待处理记录（isAudit = -1 保存未提交 或 isAudit = 0 待审核）
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("isAudit", "-1,0");
        Map<String, String> regMap = new HashMap<>();
        regMap.put("isAudit", Constant.IN);
        Page<MainVerifyProcess> pendingPage = (Page<MainVerifyProcess>) iCommonService.getSomeRecords(
                "MainVerifyProcess", searchKeys, regMap, Sort.unsorted());
        List<MainVerifyProcess> pendingList = pendingPage.getContent();

        // 3. 只刷新创建人在同校的记录（通过内存中的 Set 判断，无需逐条查库）
        int updatedCount = 0;
        for (MainVerifyProcess verifyProcess : pendingList) {
            if (schoolUserIds.contains(verifyProcess.getCreateUserId())) {
                boolean updated = refreshSingleVerifyProcess(verifyProcess);
                if (updated) {
                    updatedCount++;
                }
            }
        }

        logger.info("刷新用户 {} 相关的待审核记录完成，共更新 {} 条记录", userId, updatedCount);
        return updatedCount;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Object activateProcess(JSONObject node) {
        if (node == null) return null;
        Integer relationId  = node.getInteger("relationId");
        Integer processId   = node.getInteger("processId");
        Integer createUserId = node.getInteger("createUserId");
        String  tableName   = node.getString("tableName");
        if (relationId == null || createUserId == null) {
            logger.warn("activateProcess 参数不完整: {}", node);
            return null;
        }
        String finalTableName = (tableName != null) ? tableName : "RelProcessInternship";

        // 加载流程配置（使用 relationId，它是 RelProcessInternship 表的主键ID）
        Object relObj = iCommonService.getOneRecordById("RelProcessInternship", relationId);
        if (relObj == null) {
            logger.warn("activateProcess：未找到流程配置 {}", relationId);
            return null;
        }
        JSONObject relJson = FastJsonUtil.toJson(relObj);
        Integer verifyTypeId = relJson.getInteger("verifyTypeId");
        boolean needsVerify  = verifyTypeId != null && verifyTypeId >= 2;
        // 更新 currentVerifyTypeId
        // Integer currentVerifyTypeId = needsVerify ? 2 : 1;
        // relJson.put("currentVerifyTypeId", currentVerifyTypeId);
        // iCommonService.saveOneRecord("RelProcessInternship", relJson);
        // 计算审核人
        Integer verifyRoleId = needsVerify ? getVerifyRoleIdByLevel(relJson, 2) : null;
        String  verifyUserId = needsVerify ? GetVerifyUserId(verifyRoleId, createUserId) : "";
        // 创建审核记录
        JSONObject verifyJson = new JSONObject();
        verifyJson.put("relationId", relationId);
        verifyJson.put("processId", processId);
        verifyJson.put("createUserId", createUserId);
        verifyJson.put("verifyUserId", verifyUserId);
        verifyJson.put("isAudit", needsVerify ? -1 : 1);
        verifyJson.put("reason", "");
        verifyJson.put("tableName", finalTableName);
        Object saved = iCommonService.saveOneRecord("MainVerifyProcess", verifyJson);
        // logger.info("流程 {} 激活成功，isAudit: {}, currentVerifyTypeId: {}",
        //         relationId, saved.getIsAudit(), currentVerifyTypeId);
        return saved;
    }

    /**
     * 刷新单条审核记录的 verifyUserId
     * 通过 processId 查找 RelProcessInternship 流程配置，获取当前审核级别对应的角色，
     * 重新计算同校同角色的审核人列表。
     *
     * @param verifyProcess 审核记录
     * @return 是否更新成功
     */
    private boolean refreshSingleVerifyProcess(MainVerifyProcess verifyProcess) {
        try {
            Integer processId = verifyProcess.getProcessId();
            Integer createUserId = verifyProcess.getCreateUserId();

            if (processId == null || createUserId == null) {
                return false;
            }

            // 通过 processId 查找流程配置（processId 是 RelProcessInternship 的主键）
            Object relObj = iCommonService.getOneRecordById("RelProcessInternship", processId);
            if (relObj == null) {
                return false;
            }
            JSONObject relJson = FastJsonUtil.toJson(relObj);
            Integer currentVerifyTypeId = relJson.getInteger("currentVerifyTypeId");
            Integer verifyRoleId = getVerifyRoleIdByLevel(relJson, currentVerifyTypeId);

            if (verifyRoleId == null || verifyRoleId == 0) {
                return false;
            }

            // 重新计算 verifyUserId
            String newVerifyUserId = GetVerifyUserId(verifyRoleId, createUserId);
            String oldVerifyUserId = verifyProcess.getVerifyUserId();

            // 如果有变化则更新
            if (!newVerifyUserId.equals(oldVerifyUserId)) {
                JSONObject updateJson = new JSONObject();
                updateJson.put("id", verifyProcess.getId());
                updateJson.put("verifyUserId", newVerifyUserId);
                iCommonService.saveOneRecord("MainVerifyProcess", updateJson);
                logger.info("更新审核记录 {} 的 verifyUserId: {} -> {}",
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
     * 审核通过后的回调处理
     * <p>
     * 当审核记录被标记为通过（isAudit = 1）时调用此方法，用于推进多级审核流程。
     * </p>
     * <p>
     * 功能说明：
     * <ul>
     *   <li>获取审核通过的记录和对应的流程配置</li>
     *   <li>判断是否还有下一级审核（通过比较 currentVerifyTypeId 和 verifyTypeId）</li>
     *   <li>如果还有下一级审核：
     *     <ul>
     *       <li>更新 RelProcessInternship 的 currentVerifyTypeId 为下一级</li>
     *       <li>根据下一级审核级别获取对应的审核角色ID</li>
     *       <li>计算下一级的审核用户ID列表</li>
     *       <li>创建新的 MainVerifyProcess 记录，状态为待审核（isAudit = 0）</li>
     *     </ul>
     *   </li>
     *   <li>如果审核全部完成：
     *     <ul>
     *       <li>更新 RelProcessInternship 的 currentVerifyTypeId 为 verifyTypeId + 1</li>
     *       <li>便于后续通过 currentVerifyTypeId > verifyTypeId 判断审核是否结束</li>
     *     </ul>
     *   </li>
     * </ul>
     * </p>
     * 
     * @param Id 审核通过的 MainVerifyProcess 记录ID
     */
    @Override
    public void onVerifyProcessApproved(Integer Id) {
        if (Id == null) {
            return;
        }
        // 获取审核通过的记录
        Object verifyProcessObj = iCommonService.getOneRecordById("MainVerifyProcess", Id);
        if (verifyProcessObj == null) {
            logger.warn("未找到审核记录 {}", Id);
            return;
        }
        MainVerifyProcess verifyProcess = (MainVerifyProcess) verifyProcessObj;
        Integer processId = verifyProcess.getProcessId();
        Integer relationId = verifyProcess.getRelationId();
        String tableName = verifyProcess.getTableName();
        Integer createUserId = verifyProcess.getCreateUserId();
        String verifyUserId = verifyProcess.getVerifyUserId();
        // 获取 RelProcessInternship 记录
        Object relObj = iCommonService.getOneRecordById("RelProcessInternship", processId);
        if (relObj == null) {
            logger.warn("未找到流程关联记录 {}", processId);
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
            JSONObject nextVerifyJson = new JSONObject();
            nextVerifyJson.put("relationId", relationId);
            nextVerifyJson.put("processId", processId);
            nextVerifyJson.put("createUserId", Integer.parseInt(verifyUserId.split("\\|")[0]));
            nextVerifyJson.put("verifyUserId", nextVerifyUserId);
            nextVerifyJson.put("isAudit", 0); // 待审核
            nextVerifyJson.put("reason", "");
            nextVerifyJson.put("tableName", tableName);
            Object savedNextVerify = iCommonService.saveOneRecord("MainVerifyProcess", nextVerifyJson);
            JSONObject savedNextVerifyJson = FastJsonUtil.toJson(savedNextVerify);
            Integer nextVerifyId = savedNextVerifyJson.getInteger("id");

            logger.info("审核记录 {} 通过，流程 {} 进入下一级审核 {}，创建新审核记录 {}",
                    Id, relationId, nextLevel, nextVerifyId);
        } else {
            // 审核全部完成，将 currentVerifyTypeId 更新为 nextLevel（verifyTypeId + 1）
            // 方便后续通过 currentVerifyTypeId > verifyTypeId 直接判断审核是否结束
            relJson.put("currentVerifyTypeId", nextLevel);
            iCommonService.saveOneRecord("RelProcessInternship", relJson);

            logger.info("审核记录 {} 通过，流程 {} 审核全部完成（currentVerifyTypeId 更新为 {}，verifyTypeId {}）",
                    Id, relationId, nextLevel, verifyTypeId);
        }
    }

    /**
     * 根据审核级别从流程记录JSON中获取对应的审核角色ID
     *
     * @param relJson 流程关联记录JSON
     * @param verifyLevel 审核级别（2-6）
     * @return 对应级别的审核角色ID
     */
    @Override
    public Integer getVerifyRoleIdByLevel(JSONObject relJson, Integer verifyLevel) {
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
