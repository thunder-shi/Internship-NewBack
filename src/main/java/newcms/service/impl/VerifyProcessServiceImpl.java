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
import java.util.Comparator;
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
    public String GetVerifyUserId(Integer verifyFirstRoleId, Integer createUserId) {
        return GetVerifyUserId(verifyFirstRoleId, createUserId, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public String GetVerifyUserId(Integer verifyFirstRoleId, Integer createUserId, Integer internshipId) {
        if (verifyFirstRoleId == null || verifyFirstRoleId == 0) {
            return "";
        }
        if (createUserId == null) {
            throw BaseResponse.parameterInvalid.error("创建用户ID不能为空");
        }

        // (1) 获取提交人的 schoolId
        Object currentUserObj = iCommonService.getOneRecordById("ViewBaseUser", createUserId);
        if (currentUserObj == null) {
            throw BaseResponse.moreInfoError.error("未找到当前用户信息");
        }
        Integer userSchoolId = FastJsonUtil.toJson(currentUserObj).getInteger("schoolId");

        // (1.1) 收集需要搜索的 schoolId 集合
        //       企业用户的 schoolId 与学校不同，需要同时搜索两边，让每条审核记录自动匹配正确的审核人
        Set<Integer> schoolIds = new java.util.LinkedHashSet<>();
        if (userSchoolId != null) {
            schoolIds.add(userSchoolId);
        }
        if (internshipId != null) {
            Object internshipObj = iCommonService.getOneRecordById("MainInternship", internshipId);
            if (internshipObj != null) {
                Integer creatorId = FastJsonUtil.toJson(internshipObj).getInteger("creatorId");
                if (creatorId != null) {
                    Object creatorObj = iCommonService.getOneRecordById("ViewBaseUser", creatorId);
                    if (creatorObj != null) {
                        Integer creatorSchoolId = FastJsonUtil.toJson(creatorObj).getInteger("schoolId");
                        if (creatorSchoolId != null) {
                            schoolIds.add(creatorSchoolId);
                        }
                    }
                }
            }

            // (1.2) 收集该实习项目下所有岗位所属企业的 schoolId
            //       学生报名岗位时，审核人可能是企业管理员，其 schoolId 与学生/学校不同
            JSONObject postSearchKeys = new JSONObject();
            postSearchKeys.put("internshipId", internshipId);
            Page<Object> postPage = (Page<Object>) iCommonService.getSomeRecords(
                    "MainInternshipPost", postSearchKeys, null, Sort.unsorted(), 1, 10000);
            Set<Integer> postTypeIds = postPage.getContent().stream()
                    .map(FastJsonUtil::toJson)
                    .map(json -> json.getInteger("postTypeId"))
                    .filter(id -> id != null)
                    .collect(Collectors.toSet());
            for (Integer postTypeId : postTypeIds) {
                Object postTypeObj = iCommonService.getOneRecordById("ViewBasePostType", postTypeId);
                if (postTypeObj != null) {
                    Integer companyId = FastJsonUtil.toJson(postTypeObj).getInteger("companyId");
                    if (companyId != null) {
                        Object deptObj = iCommonService.getOneRecordById("ViewBaseDepartment", companyId);
                        if (deptObj != null) {
                            Integer companySchoolId = FastJsonUtil.toJson(deptObj).getInteger("schoolId");
                            if (companySchoolId != null) {
                                schoolIds.add(companySchoolId);
                            }
                        }
                    }
                }
            }
        }

        logger.info("GetVerifyUserId: verifyRoleId={}, createUserId={}, internshipId={}, 搜索schoolIds={}",
                verifyFirstRoleId, createUserId, internshipId, schoolIds);

        if (schoolIds.isEmpty()) {
            logger.warn("GetVerifyUserId: createUserId={} 无法确定任何 schoolId", createUserId);
            return "";
        }

        // (2) 查找所有相关 schoolId 下的用户 ID（合并）
        Set<Integer> candidateUserIds = new java.util.HashSet<>();
        for (Integer sid : schoolIds) {
            JSONObject schoolSearchKeys = new JSONObject();
            schoolSearchKeys.put("schoolId", sid);
            Page<Object> schoolUserPage = (Page<Object>) iCommonService.getSomeRecords(
                    "ViewBaseUser", schoolSearchKeys, null, Sort.unsorted(), 1, 10000);
            schoolUserPage.getContent().stream()
                    .map(user -> FastJsonUtil.toJson(user).getInteger("id"))
                    .filter(id -> id != null)
                    .forEach(candidateUserIds::add);
        }

        if (candidateUserIds.isEmpty()) {
            return "";
        }

        // (3) 查找拥有该审核角色的用户
        JSONObject roleSearchKeys = new JSONObject();
        roleSearchKeys.put("roleId", verifyFirstRoleId);
        Page<Object> userRolePage = (Page<Object>) iCommonService.getSomeRecords(
                "RelUserRole", roleSearchKeys, null, Sort.unsorted(), 1, 10000);
        List<Object> userRoleList = userRolePage.getContent();

        // (4) 取交集：候选用户（企业+学校） ∩ 拥有审核角色
        List<Integer> verifyUserIds = userRoleList.stream()
                .map(role -> FastJsonUtil.toJson(role).getInteger("userId"))
                .filter(userId -> userId != null && candidateUserIds.contains(userId))
                .distinct()
                .collect(Collectors.toList());

        logger.info("GetVerifyUserId: roleId={}, 候选用户数={}, 匹配审核人={}",
                verifyFirstRoleId, candidateUserIds.size(), verifyUserIds);

        if (verifyUserIds.isEmpty()) {
            return "";
        }

        return verifyUserIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining("|"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public int refreshPendingVerifyUsersByUser(Integer userId) {
        if (userId == null) {
            return 0;
        }

        // 1. 获取用户的 schoolId
        Object userObj = iCommonService.getOneRecordById("ViewBaseUser", userId);
        if (userObj == null) {
            return 0;
        }
        JSONObject userJson = FastJsonUtil.toJson(userObj);
        Integer schoolId = userJson.getInteger("schoolId");
        if (schoolId == null) {
            return 0;
        }

        // 2. 批量查出同校所有用户ID
        JSONObject schoolSearchKeys = new JSONObject();
        schoolSearchKeys.put("schoolId", schoolId);
        Page<Object> schoolUserPage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewBaseUser", schoolSearchKeys, null, Sort.unsorted(), 1, 10000);
        Set<Integer> schoolUserIds = schoolUserPage.getContent().stream()
                .map(u -> FastJsonUtil.toJson(u).getInteger("id"))
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (schoolUserIds.isEmpty()) {
            return 0;
        }

        // 3. 查询所有 MainVerifyProcess 记录（不限 isAudit 状态，覆盖全量数据）
        //    显式指定大 pageSize 避免被默认的 25 条分页截断
        Page<MainVerifyProcess> allPage = (Page<MainVerifyProcess>) iCommonService.getSomeRecords(
                "MainVerifyProcess", new JSONObject(), null,
                Sort.by(Sort.Direction.ASC, "id"), 1, 10000);
        List<MainVerifyProcess> allRecords = allPage.getContent();

        // 4. 只处理创建人在同校的记录
        List<MainVerifyProcess> relevantRecords = allRecords.stream()
                .filter(vp -> schoolUserIds.contains(vp.getCreateUserId()))
                .collect(Collectors.toList());

        // 5. 按 processId 分组
        Map<Integer, List<MainVerifyProcess>> groupedByProcess = relevantRecords.stream()
                .filter(vp -> vp.getProcessId() != null)
                .collect(Collectors.groupingBy(MainVerifyProcess::getProcessId));

        // 6. 缓存流程配置和 verifyUserId 计算结果，避免重复查询
        Map<Integer, JSONObject> processConfigCache = new HashMap<>();
        Map<String, String> verifyUserIdCache = new HashMap<>();

        int updatedCount = 0;
        for (Map.Entry<Integer, List<MainVerifyProcess>> entry : groupedByProcess.entrySet()) {
            Integer processId = entry.getKey();
            List<MainVerifyProcess> records = entry.getValue();

            // 按 id 升序排列
            records.sort(Comparator.comparingInt(MainVerifyProcess::getId));

            // 加载流程配置（RelProcessInternship）
            JSONObject relJson = processConfigCache.get(processId);
            if (relJson == null) {
                Object relObj = iCommonService.getOneRecordById("RelProcessInternship", processId);
                if (relObj == null) continue;
                relJson = FastJsonUtil.toJson(relObj);
                processConfigCache.put(processId, relJson);
            }

            // 7. 遍历记录，通过"行走算法"推断每条记录的审核级别
            //    审核级别从 2 开始（对应 verifyFirstRoleId）
            //    - isAudit=1（通过）→ 下一条记录级别 +1
            //    - isAudit=2/3（退回）→ 下一条记录级别 -1（最低为 2）
            //    - isAudit=-1/0（未提交/待审）→ 级别不变
            int currentLevel = 2;
            for (MainVerifyProcess record : records) {
                Integer isAudit = record.getIsAudit();

                // 只更新待处理记录（isAudit=-1 保存未提交 或 isAudit=0 待审核）
                // 已通过/已退回的记录是历史数据，保留当时审核人信息不动
                if (isAudit != null && (isAudit == -1 || isAudit == 0)) {
                    Integer verifyRoleId = getVerifyRoleIdByLevel(relJson, currentLevel);
                    if (verifyRoleId != null && verifyRoleId != 0) {
                        Integer internshipId = relJson.getInteger("internshipId");
                        String cacheKey = verifyRoleId + ":" + record.getCreateUserId() + ":" + internshipId;
                        String newVerifyUserId = verifyUserIdCache.computeIfAbsent(cacheKey,
                                k -> GetVerifyUserId(verifyRoleId, record.getCreateUserId(), internshipId));
                        String oldVerifyUserId = record.getVerifyUserId();

                        if (!newVerifyUserId.equals(oldVerifyUserId)) {
                            JSONObject updateJson = new JSONObject();
                            updateJson.put("id", record.getId());
                            updateJson.put("verifyUserId", newVerifyUserId);
                            iCommonService.saveOneRecord("MainVerifyProcess", updateJson);
                            updatedCount++;
                        }
                    }
                }

                // 无论是否更新，都要根据 isAudit 状态推进级别（保证行走算法正确）
                if (isAudit != null) {
                    if (isAudit == 1) {
                        currentLevel++;
                    } else if (isAudit == 2 || isAudit == 3) {
                        currentLevel = Math.max(2, currentLevel - 1);
                    }
                }
            }
        }

        logger.info("刷新用户 {} 相关的审核记录完成，共更新 {} 条记录", userId, updatedCount);
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
        Integer internshipId = relJson.getInteger("internshipId");
        String  verifyUserId = needsVerify ? GetVerifyUserId(verifyRoleId, createUserId, internshipId) : "系统自动通过";
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
        // processId 为 null 时（如日志审核），直接从业务实体读审核配置；否则走 RelProcessInternship
        JSONObject relJson;
        if (processId != null) {
            Object relObj = iCommonService.getOneRecordById("RelProcessInternship", processId);
            if (relObj == null) {
                logger.warn("未找到流程关联记录 {}", processId);
                return;
            }
            relJson = FastJsonUtil.toJson(relObj);
        } else {
            Object entityObj = iCommonService.getOneRecordById(tableName, relationId);
            if (entityObj == null) {
                logger.warn("未找到业务实体配置 {} id={}", tableName, relationId);
                return;
            }
            relJson = FastJsonUtil.toJson(entityObj);
        }
        Integer verifyTypeId = relJson.getInteger("verifyTypeId");

        // 从对应的业务实体表获取 currentVerifyTypeId（每个审核条目独立跟踪审核级别）
        Object entityObj = iCommonService.getOneRecordById(tableName, relationId);
        if (entityObj == null) {
            logger.warn("未找到业务实体记录 {} id={}", tableName, relationId);
            return;
        }
        JSONObject entityJson = FastJsonUtil.toJson(entityObj);
        Integer currentVerifyTypeId = entityJson.getInteger("currentVerifyTypeId");

        if (currentVerifyTypeId == null) {
            currentVerifyTypeId = 2; // 默认一级审核
        }
        if (verifyTypeId == null) {
            verifyTypeId = 1; // 默认无需审核
        }

        int nextLevel = currentVerifyTypeId + 1;

        // 更新业务实体表的 currentVerifyTypeId
        JSONObject updateEntityJson = new JSONObject();
        updateEntityJson.put("id", relationId);
        updateEntityJson.put("currentVerifyTypeId", nextLevel);
        iCommonService.saveOneRecord(tableName, updateEntityJson);

        if (nextLevel <= verifyTypeId) {
            // 还有下一级审核：创建新审核记录
            Integer nextVerifyRoleId = getVerifyRoleIdByLevel(relJson, nextLevel);
            Integer internshipIdForVerify = relJson.getInteger("internshipId");
            String nextVerifyUserId = GetVerifyUserId(nextVerifyRoleId, createUserId, internshipIdForVerify);
            // 创建下一级审核记录
            JSONObject nextVerifyJson = new JSONObject();
            nextVerifyJson.put("relationId", relationId);
            nextVerifyJson.put("processId", processId);
            nextVerifyJson.put("createUserId", createUserId);
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
            // 审核全部完成（currentVerifyTypeId > verifyTypeId）
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

    @Override
    @SuppressWarnings("unchecked")
    public int refreshPendingVerifyUsersByProcess(Integer processId) {
        if (processId == null) {
            return 0;
        }

        // 1. 加载流程配置
        Object relObj = iCommonService.getOneRecordById("RelProcessInternship", processId);
        if (relObj == null) {
            return 0;
        }
        JSONObject relJson = FastJsonUtil.toJson(relObj);

        // 2. 查询该流程下所有待处理的 MainVerifyProcess 记录（isAudit = -1 或 0）
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("processId", processId);
        Page<Object> allPage = (Page<Object>) iCommonService.getSomeRecords(
                "MainVerifyProcess", searchKeys, null,
                Sort.by(Sort.Direction.ASC, "id"), 1, 10000);
        List<Object> allRecords = allPage.getContent();

        // 4. 用行走算法推断每条记录的审核级别，并刷新待处理记录的 verifyUserId
        int updatedCount = 0;
        int currentLevel = 2;
        for (Object record : allRecords) {
            JSONObject recordJson = FastJsonUtil.toJson(record);
            Integer isAudit = recordJson.getInteger("isAudit");
            Integer createUserId = recordJson.getInteger("createUserId");

            if (isAudit != null && (isAudit == -1 || isAudit == 0)) {
                Integer verifyRoleId = getVerifyRoleIdByLevel(relJson, currentLevel);
                if (verifyRoleId != null && verifyRoleId != 0) {
                    Integer internshipIdForRefresh = relJson.getInteger("internshipId");
                    String newVerifyUserId = GetVerifyUserId(verifyRoleId, createUserId, internshipIdForRefresh);
                    String oldVerifyUserId = recordJson.getString("verifyUserId");

                    if (!newVerifyUserId.equals(oldVerifyUserId)) {
                        JSONObject updateJson = new JSONObject();
                        updateJson.put("id", recordJson.getInteger("id"));
                        updateJson.put("verifyUserId", newVerifyUserId);
                        iCommonService.saveOneRecord("MainVerifyProcess", updateJson);
                        updatedCount++;
                    }
                }
            }

            // 根据 isAudit 推进级别
            if (isAudit != null) {
                if (isAudit == 1) {
                    currentLevel++;
                } else if (isAudit == 2 || isAudit == 3) {
                    currentLevel = Math.max(2, currentLevel - 1);
                }
            }
        }

        logger.info("刷新流程 {} 的审核记录完成，共更新 {} 条记录", processId, updatedCount);
        return updatedCount;
    }

}
