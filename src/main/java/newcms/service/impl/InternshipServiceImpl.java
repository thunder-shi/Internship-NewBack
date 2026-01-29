package newcms.service.impl;

import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.repository.db.RelProcessInternshipDao;
import newcms.service.ICommonService;
import newcms.service.IInternshipService;
import newcms.service.IVerifyProcessService;
import newcms.utils.FastJsonUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(rollbackFor = Exception.class)
public class InternshipServiceImpl extends Base implements IInternshipService {

    @Resource
    private ICommonService iCommonService;

    @Resource
    private IVerifyProcessService iVerifyProcessService;

    @Resource
    private RelProcessInternshipDao relProcessInternshipDao;

    // ==================== 实习项目管理（无需审核） ====================

    @Override
    public Object addNewInternship(JSONObject node) {
        // (1) 在 MainInternship 实体增加一条记录（称为实体a）
        Object savedInternship = iCommonService.saveOneRecord("MainInternship", node);
        JSONObject savedInternshipJson = FastJsonUtil.toJson(savedInternship);
        Integer internshipId = savedInternshipJson.getInteger("id");
        Integer internshipTypeId = savedInternshipJson.getInteger("internshipTypeId");

        if (internshipTypeId == null) {
            throw BaseResponse.moreInfoError.error("实习类型ID不能为空");
        }

        // (2) 查找 RelProcessInternshipType 所有 internshipTypeId 和新增实体的 internshipTypeId 值相等的记录
        // 譬如说找到3条（称为实体b1 b2 b3）
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("internshipTypeId", internshipTypeId);
        @SuppressWarnings("unchecked")
        Page<Object> processTypePage = (Page<Object>) iCommonService.getSomeRecords("RelProcessInternshipType", searchKeys, null, Sort.unsorted());
        List<Object> processTypeList = processTypePage.getContent();

        // (3) 在 RelProcessInternship 实体中增加若干条记录，例如对应刚刚的3条（称为实体c1 c2 c3）
        List<Object> processInternshipList = new ArrayList<>();
        for (Object processTypeObj : processTypeList) {
            JSONObject processTypeJson = FastJsonUtil.toJson(processTypeObj);
            JSONObject processInternshipJson = new JSONObject();
            
            // c1 c2 c3 的 internshipId 是实体a的id
            processInternshipJson.put("internshipId", internshipId);
            
            // c1 c2 c3 的其他几个属性，包括 processTypeId、verifyTypeId、verifyFirstRoleId、
            // verifySecondRoleId、verifyThirdRoleId、verifyFourthRoleId、verifyFifthRoleId，
            // 直接带入 b1 b2 b3 中的对应值
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
                    "ViewMainVerifyProcess", searchKeys, null, Sort.unsorted());
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
}
