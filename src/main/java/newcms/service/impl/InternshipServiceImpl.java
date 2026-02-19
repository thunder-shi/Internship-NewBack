package newcms.service.impl;

import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.base.Constant;
import newcms.repository.db.RelInterMajorDao;
import newcms.repository.db.RelProcessInternshipDao;
import newcms.service.ICommonService;
import newcms.service.IInternshipService;
import newcms.service.IVerifyProcessService;
import newcms.utils.FastJsonUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class InternshipServiceImpl extends Base implements IInternshipService {

    @Resource
    private ICommonService iCommonService;

    @Resource
    private IVerifyProcessService iVerifyProcessService;

    @Resource
    private RelProcessInternshipDao relProcessInternshipDao;

    @Resource
    private RelInterMajorDao relInterMajorDao;

    // ==================== 实习项目管理（无需审核） ====================

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
        Page<Object> processTypePage = (Page<Object>) iCommonService.getSomeRecords("RelProcessInternshipType", searchKeys, null, Sort.unsorted());
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
            processInternshipJson.put("currentVerifyTypeId", 1);
            processInternshipList.add(processInternshipJson);
        }
        // 批量保存 RelProcessInternship 记录
        if (!processInternshipList.isEmpty()) {
            iCommonService.saveSomeRecords("RelProcessInternship", processInternshipList);
        }
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

        // (2) 创建流程关联记录
        createProcessInternshipRecords(internshipId, internshipTypeId);

        // (3) 创建专业范围关联记录
        createInternshipMajorRecords(internshipId, internshipTypeId);

        return savedInternship;
    }

    // ==================== 实习计划流程（需要审核） ====================

    @Override
    public Object submitInternshipPlan(JSONObject requestJson) {
        if (requestJson == null) {
            throw BaseResponse.parameterInvalid.error("请求参数不能为空");
        }

        // 1. 保存/更新 MainInternship
        JSONObject node = requestJson.getJSONObject("node");
        if (node == null) {
            throw BaseResponse.parameterInvalid.error("node 参数不能为空");
        }
        Object savedInternship = iCommonService.saveOneRecord("MainInternship", node);
        JSONObject savedInternshipJson = FastJsonUtil.toJson(savedInternship);
        Integer internshipId = savedInternshipJson.getInteger("id");
        Integer internshipTypeId = savedInternshipJson.getInteger("internshipTypeId");
        if (internshipId == null || internshipTypeId == null) {
            throw BaseResponse.moreInfoError.error("保存实习项目失败，缺少关键字段");
        }

        // 2. 查找流程关联记录（取第一条，对应计划制定流程）
        Object foundProcess = iVerifyProcessService.GetInternshipFoundProcess(internshipId);
        JSONObject relJson = FastJsonUtil.toJson(foundProcess);
        Integer relationId = relJson.getInteger("id");
        Integer verifyFirstRoleId = relJson.getInteger("verifyFirstRoleId");

        // 3. 创建审核记录 MainVerifyProcess
        Integer createUserId = node.getInteger("creatorId");
        if (createUserId == null) {
            throw BaseResponse.parameterInvalid.error("creatorId 参数不能为空");
        }

        // 获取 verifyTypeId 判断是否需要审核
        Integer verifyTypeId = relJson.getInteger("verifyTypeId");
        // verifyTypeId: 1-无需审核, 2-一级审核, 3-二级审核, 4-三级审核, 5-四级审核, 6-五级审核
        boolean needsVerify = verifyTypeId != null && verifyTypeId >= 2;

        // 初始化 RelProcessInternship.currentVerifyTypeId
        Integer currentVerifyTypeId = needsVerify ? 2 : 1;
        JSONObject updateRelJson = new JSONObject();
        updateRelJson.put("id", relationId);
        updateRelJson.put("currentVerifyTypeId", currentVerifyTypeId);
        iCommonService.saveOneRecord("RelProcessInternship", updateRelJson);

        // 获取审核用户ID字符串
        String verifyUserId = needsVerify ? iVerifyProcessService.GetVerifyUserId(verifyFirstRoleId, createUserId) : "";

        // 创建审核记录
        JSONObject verifyJson = new JSONObject();
        verifyJson.put("relationId", relationId);
        verifyJson.put("processId", relationId);
        verifyJson.put("createUserId", createUserId);
        // 需要审核：isAudit = 0（提交待审核）；不需要审核：isAudit = 1（直接通过）
        verifyJson.put("isAudit", needsVerify ? 0 : 1);
        verifyJson.put("reason", "");
        verifyJson.put("tableName", "RelProcessInternship");
        verifyJson.put("verifyUserId", verifyUserId);
        iCommonService.saveOneRecord("MainVerifyProcess", verifyJson);

        return savedInternship;
    }

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
     * 审核退回后，在相同审核级别新建一条 isAudit=-1 的记录，等待用户重新提交。
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

        // 2. 读取流程配置，获取当前审核级别
        Object relObj = iCommonService.getOneRecordById("RelProcessInternship", relationId);
        if (relObj == null) {
            logger.warn("退回后新建记录失败：未找到流程配置 {}", relationId);
            return;
        }
        JSONObject relJson = FastJsonUtil.toJson(relObj);
        Integer currentVerifyTypeId = relJson.getInteger("currentVerifyTypeId");

        // 3. 重新计算该级别的审核人（刷新最新角色/部门状态）
        Integer verifyRoleId = getVerifyRoleIdByLevel(relJson, currentVerifyTypeId);
        String verifyUserId = iVerifyProcessService.GetVerifyUserId(verifyRoleId, createUserId);

        // 4. 新建待提交记录（isAudit=-1），级别与退回时相同，无需修改 currentVerifyTypeId
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

        // (1) 检查所有id是否在审核流程中
        for (Integer internshipId : internshipIds) {
            if (internshipId == null) {
                continue;
            }
            JSONObject searchKeys = new JSONObject();
            searchKeys.put("internshipId", internshipId);
            // 查询时需要考虑 isDeleted，所以使用默认的 getSomeRecords（会自动过滤 isDeleted=false）
            @SuppressWarnings("unchecked")
            Page<Object> verifyProcessPage = (Page<Object>) iCommonService.getSomeRecords(
                    "ViewVerifyProcessInternship", searchKeys, null, Sort.unsorted());
            List<Object> verifyProcessList = verifyProcessPage.getContent();

            // 如果存在审核流程记录，返回错误信息
            if (verifyProcessList != null && !verifyProcessList.isEmpty()) {
                throw BaseResponse.parameterInvalid.error("当前项目已经进入审核流程，无法删除");
            }
        }

        // (2) 批量删除 MainInternship 中对应的记录（伪删除）
        // 过滤掉 null 值
        List<Integer> validIds = internshipIds.stream()
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toList());
        if (!validIds.isEmpty()) {
            iCommonService.deleteSomeRecords("MainInternship", validIds);
        }

        // (3) 批量删除 RelProcessInternship 中所有 internshipId 是对应 Id 的记录（伪删除）
        for (Integer internshipId : validIds) {
            relProcessInternshipDao.deleteByInternshipId(internshipId);
        }

        return "删除成功";
    }

    @Override
    public Object getNowInternship(String processTypeCode) {
        if (processTypeCode == null || processTypeCode.trim().isEmpty()) {
            throw BaseResponse.parameterInvalid.error("processTypeCode 参数不能为空");
        }

        LocalDateTime now = LocalDateTime.now();

        // (1) 从 ViewRelProcessInternship 中查找，当前时间在 startTime 和 endTime 之间的且 ProcessTypeCode 和传过来的字符串相同的
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("processTypeCode", processTypeCode);
        searchKeys.put("startTime", now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        searchKeys.put("endTime", now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        Map<String, String> regMap = new HashMap<>();
        regMap.put("startTime", Constant.LE); // startTime <= 当前时间
        regMap.put("endTime", Constant.GE);   // endTime >= 当前时间

        @SuppressWarnings("unchecked")
        Page<Object> firstPage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewRelProcessInternship", searchKeys, regMap, Sort.unsorted());
        List<Object> firstList = firstPage.getContent();

        if (firstList == null || firstList.isEmpty()) {
            return new ArrayList<>();
        }

        // (2) 上一条中找到的所有条目还需要排除一些，满足下面条件的不排除：
        //     再去 ViewRelProcessInternship 中查找，这次查找条件是：
        //     - InternshipId 在刚刚找到的条目的 InternshipId 中
        //     - ProcessTypeCode 是 "INTERNSHIP_PLAN_MAKE"
        //     - CurrentVerifyTypeId > VerifyTypeId（这里的 VerifyTypeId 是第一步找到的记录的 verifyTypeId）
        
        // 提取第一步找到的所有 internshipId
        Set<Integer> firstInternshipIds = firstList.stream()
                .map(obj -> {
                    JSONObject json = FastJsonUtil.toJson(obj);
                    return json.getInteger("internshipId");
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (firstInternshipIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 查询所有 processTypeCode = INTERNSHIP_PLAN_MAKE 且 internshipId 在第一步结果中的记录
        JSONObject secondSearchKeys = new JSONObject();
        secondSearchKeys.put("processTypeCode", Constant.PROCESS_TYPE.INTERNSHIP_PLAN_MAKE);
        secondSearchKeys.put("internshipId", String.join(",", firstInternshipIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList())));

        Map<String, String> secondRegMap = new HashMap<>();
        secondRegMap.put("internshipId", Constant.IN); // internshipId IN (列表)

        @SuppressWarnings("unchecked")
        Page<Object> secondPage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewRelProcessInternship", secondSearchKeys, secondRegMap, Sort.unsorted());
        List<Object> secondList = secondPage.getContent();

        // 将第二步查询结果按 internshipId 分组，方便后续查找
        Map<Integer, List<JSONObject>> secondMapByInternshipId = new HashMap<>();
        if (secondList != null && !secondList.isEmpty()) {
            for (Object obj : secondList) {
                JSONObject json = FastJsonUtil.toJson(obj);
                Integer internshipId = json.getInteger("internshipId");
                if (internshipId != null) {
                    secondMapByInternshipId.computeIfAbsent(internshipId, k -> new ArrayList<>()).add(json);
                }
            }
        }

        // (3) 把剩下没排除的返回给前端
        // 对于第一步的每个记录，检查是否存在满足条件的记录（不排除的条件）
        List<Object> result = new ArrayList<>();
        for (Object firstObj : firstList) {
            JSONObject firstJson = FastJsonUtil.toJson(firstObj);
            Integer internshipId = firstJson.getInteger("internshipId");
            Integer verifyTypeId = firstJson.getInteger("verifyTypeId");

            if (internshipId == null || verifyTypeId == null) {
                continue;
            }

            // 查找该 internshipId 对应的 INTERNSHIP_PLAN_MAKE 记录
            List<JSONObject> planMakeRecords = secondMapByInternshipId.get(internshipId);
            if (planMakeRecords != null && !planMakeRecords.isEmpty()) {
                // 检查是否存在 currentVerifyTypeId > verifyTypeId 的记录
                boolean shouldKeep = false;
                for (JSONObject planMakeJson : planMakeRecords) {
                    Integer currentVerifyTypeId = planMakeJson.getInteger("currentVerifyTypeId");
                    Integer planVerifyTypeId = planMakeJson.getInteger("verifyTypeId");
                    if (currentVerifyTypeId != null && planVerifyTypeId != null && currentVerifyTypeId > planVerifyTypeId) {
                        shouldKeep = true;
                        break;
                    }
                }
                // 如果满足条件（不排除），则添加到结果中
                if (shouldKeep) {
                    result.add(firstObj);
                }
            }
        }

        return result;
    }
}
