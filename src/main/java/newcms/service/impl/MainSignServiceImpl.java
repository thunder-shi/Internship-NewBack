package newcms.service.impl;

import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.base.Constant;
import newcms.entity.db.MainSign;
import newcms.entity.db.MainVerifyProcess;
import newcms.repository.db.MainSignDao;
import newcms.repository.db.MainVerifyProcessDao;
import newcms.service.ICommonService;
import newcms.service.IMainSignService;
import newcms.service.IVerifyProcessService;
import newcms.utils.FastJsonUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(rollbackFor = Exception.class)
public class MainSignServiceImpl extends Base implements IMainSignService {

    @Resource
    private ICommonService iCommonService;
    @Resource
    private MainSignDao mainSignDao;
    @Resource
    private MainVerifyProcessDao mainVerifyProcessDao;
    @Resource
    private IVerifyProcessService iVerifyProcessService;

    @Override
    public void ensureSubmitVerifyProcess(Integer signId, Integer currentUserId) {
        MainSign sign = mainSignDao.getByIdAndIsDeletedFalse(signId);
        if (sign == null) {
            throw BaseResponse.parameterInvalid.error("打卡记录不存在");
        }

        List<MainVerifyProcess> existing = mainVerifyProcessDao
                .findByRelationIdAndTableNameAndIsDeletedFalse(signId, "MainSign");
        boolean hasPending = existing.stream()
                .anyMatch(p -> p.getIsAudit() != null && p.getIsAudit() == Constant.AUDIT_STATUS.SUBMIT);
        if (hasPending) {
            return;
        }
        boolean alreadyPassed = existing.stream()
                .anyMatch(p -> p.getIsAudit() != null && p.getIsAudit() == Constant.AUDIT_STATUS.PASS);
        if (alreadyPassed) {
            return;
        }

        JSONObject signJson = FastJsonUtil.toJson(iCommonService.getOneRecordById("MainSign", signId));
        Integer verifyFirstRoleId = signJson.getInteger("verifyFirstRoleId");

        String verifyUserId = null;
        int isAudit = Constant.AUDIT_STATUS.SUBMIT;
        String reason = "";

        if (verifyFirstRoleId != null && verifyFirstRoleId > 0) {
            String fromRole = iVerifyProcessService.GetVerifyUserId(verifyFirstRoleId, currentUserId);
            if (fromRole != null && !fromRole.isBlank()) {
                verifyUserId = fromRole;
            }
        }
        if (verifyUserId == null || verifyUserId.isBlank()) {
            verifyUserId = "系统自动通过";
            isAudit = Constant.AUDIT_STATUS.PASS;
            reason = "系统自动通过";
        }

        JSONObject verifyJson = new JSONObject();
        verifyJson.put("relationId", signId);
        verifyJson.put("createUserId", currentUserId);
        verifyJson.put("verifyUserId", verifyUserId);
        verifyJson.put("isAudit", isAudit);
        verifyJson.put("reason", reason);
        verifyJson.put("tableName", "MainSign");
        iCommonService.saveOneRecord("MainVerifyProcess", verifyJson);
    }
}
