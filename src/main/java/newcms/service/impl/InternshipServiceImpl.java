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
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class InternshipServiceImpl extends Base implements IInternshipService {

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
        // (3) 创建流程关联记录和第一条审核记录
        createProcessInternshipRecords(internshipId, internshipTypeId);
        // (4) 创建"实习计划制定"流程的第一条 MainVerifyProcess 记录
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
            // 根据 verifyTypeCode 设置 currentVerifyTypeId
            String verifyTypeCode = processTypeJson.getString("verifyTypeCode");
            if ("NO_VERIFY".equals(verifyTypeCode)) {
                processInternshipJson.put("currentVerifyTypeId", Constant.VERIFY_LEVEL.NO_VERIFY);
            } else {
                processInternshipJson.put("currentVerifyTypeId", Constant.VERIFY_LEVEL.ONE_VERIFY);
            }
            processInternshipList.add(processInternshipJson);
        }
        // 批量保存 RelProcessInternship 记录
        if (!processInternshipList.isEmpty()) {
            iCommonService.saveSomeRecords("RelProcessInternship", processInternshipList);
        }
    }

    /**
     * 创建"实习计划制定"流程的第一条 MainVerifyProcess 记录
     * @param internshipId 实习项目ID
     */
    private void createFirstVerifyProcessRecord(Integer internshipId) {
        // 2. 查找流程关联记录（取第一条，对应计划制定流程）
        Object foundProcess = iVerifyProcessService.GetInternshipProcess(internshipId, null);
        JSONObject relJson = FastJsonUtil.toJson(foundProcess);
        Integer relProcessId = relJson.getInteger("id");
        Integer currentVerifyTypeId = relJson.getInteger("currentVerifyTypeId");
        Integer verifyFirstRoleId = relJson.getInteger("verifyFirstRoleId");
        // 3. 创建审核记录 MainVerifyProcess
        // 从 MainInternship 获取 createUserId
        Object internshipObj = iCommonService.getOneRecordById("MainInternship", internshipId);
        if (internshipObj == null) {
            throw BaseResponse.moreInfoError.error("未找到实习项目记录");
        }
        JSONObject internshipJson = FastJsonUtil.toJson(internshipObj);
        Integer createUserId = internshipJson.getInteger("creatorId");
        if (createUserId == null) {
            throw BaseResponse.parameterInvalid.error("creatorId 参数不能为空");
        }

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

        // 2. 读取流程配置，获取当前审核级别（使用 processId 查 RelProcessInternship）
        Object relObj = iCommonService.getOneRecordById("RelProcessInternship", processId);
        if (relObj == null) {
            logger.warn("退回后新建记录失败：未找到流程配置 {}", processId);
            return;
        }
        JSONObject relJson = FastJsonUtil.toJson(relObj);
        Integer currentVerifyTypeId = relJson.getInteger("currentVerifyTypeId");

        // 3. 退回时 currentVerifyTypeId - 1，回退到上一级审核
        //    但不低于 2（第一级审核的初始值），第一级退回时保持原级别
        if (currentVerifyTypeId != null && currentVerifyTypeId > 2) {
            currentVerifyTypeId -= 1;
            relJson.put("currentVerifyTypeId", currentVerifyTypeId);
            iCommonService.saveOneRecord("RelProcessInternship", relJson);
        }

        // 4. 重新计算回退后级别的审核人
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
}
