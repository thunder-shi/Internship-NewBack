package newcms.service.impl;

import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.base.Constant;
import newcms.entity.db.MainSign;
import newcms.entity.db.MainVerifyProcess;
import newcms.entity.db.ViewRelProcessInternship;
import newcms.repository.db.MainSignDao;
import newcms.repository.db.MainVerifyProcessDao;
import newcms.repository.db.ViewRelProcessInternshipDao;
import newcms.service.ICommonService;
import newcms.service.IMainSignService;
import newcms.service.IVerifyProcessService;
import newcms.utils.FastJsonUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional(rollbackFor = Exception.class)
public class MainSignServiceImpl extends Base implements IMainSignService {

    /** 按 signId 分段的应用层锁，防止并发提交同一打卡记录时重复创建审核行（CONC-06） */
    private static final ConcurrentHashMap<Integer, Object> SIGN_LOCKS = new ConcurrentHashMap<>();

    @Resource
    private ICommonService iCommonService;
    @Resource
    private MainSignDao mainSignDao;
    @Resource
    private MainVerifyProcessDao mainVerifyProcessDao;
    @Resource
    private ViewRelProcessInternshipDao viewRelProcessInternshipDao;
    @Resource
    private IVerifyProcessService iVerifyProcessService;

    @Override
    public void ensureSubmitVerifyProcess(Integer signId, Integer internshipId, Integer currentUserId) {
        MainSign sign = mainSignDao.getByIdAndIsDeletedFalse(signId);
        if (sign == null) {
            throw BaseResponse.parameterInvalid.error("打卡记录不存在");
        }

        // 按 signId 加锁，防止并发提交同一打卡记录时重复创建审核行（CONC-06）
        Object lock = SIGN_LOCKS.computeIfAbsent(signId, k -> new Object());
        synchronized (lock) {

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

            JSONObject processJson = resolveStudentSignProcess(internshipId);
            Integer resolvedProcessId = processJson.getInteger("id");

            JSONObject signJson = FastJsonUtil.toJson(iCommonService.getOneRecordById("MainSign", signId));
            Integer verifyTypeId = getConfigInteger(processJson, signJson, "verifyTypeId");
            if (verifyTypeId == null) {
                verifyTypeId = Constant.VERIFY_LEVEL.ONE_VERIFY;
            }
            Integer verifyFirstRoleId = getConfigInteger(processJson, signJson, "verifyFirstRoleId");

            String verifyUserId = null;
            int isAudit = Constant.AUDIT_STATUS.SUBMIT;
            String reason = "";

            if (verifyTypeId >= Constant.VERIFY_LEVEL.ONE_VERIFY && verifyFirstRoleId != null && verifyFirstRoleId > 0) {
                String fromRole = iVerifyProcessService.GetVerifyUserId(verifyFirstRoleId, currentUserId, internshipId);
                if (fromRole != null && !fromRole.isBlank()) {
                    verifyUserId = fromRole;
                }
            }
            if (verifyTypeId < Constant.VERIFY_LEVEL.ONE_VERIFY || verifyUserId == null || verifyUserId.isBlank()) {
                verifyUserId = "系统自动通过";
                isAudit = Constant.AUDIT_STATUS.PASS;
                reason = "系统自动通过";
            }

            JSONObject signState = new JSONObject();
            signState.put("id", signId);
            signState.put("verifyTypeId", verifyTypeId);
            if (isAudit == Constant.AUDIT_STATUS.PASS) {
                signState.put("currentVerifyTypeId", verifyTypeId + 1);
            } else {
                signState.put("currentVerifyTypeId", Constant.VERIFY_LEVEL.ONE_VERIFY);
            }
            iCommonService.saveOneRecord("MainSign", signState);

            JSONObject verifyJson = new JSONObject();
            verifyJson.put("relationId", signId);
            verifyJson.put("processId", resolvedProcessId);
            verifyJson.put("createUserId", currentUserId);
            verifyJson.put("verifyUserId", verifyUserId);
            verifyJson.put("isAudit", isAudit);
            verifyJson.put("reason", reason);
            verifyJson.put("tableName", "MainSign");
            iCommonService.saveOneRecord("MainVerifyProcess", verifyJson);

        } // end synchronized
    }

    private Integer getConfigInteger(JSONObject processJson, JSONObject fallbackJson, String key) {
        Integer value = processJson != null ? processJson.getInteger(key) : null;
        return value != null ? value : fallbackJson.getInteger(key);
    }

    private JSONObject resolveStudentSignProcess(Integer internshipId) {
        List<ViewRelProcessInternship> rows = viewRelProcessInternshipDao
                .findByInternshipIdAndProcessTypeCodeAndIsDeletedFalseOrderByTheOrderAsc(
                        internshipId, Constant.PROCESS_TYPE.STUDENT_SIGN);
        if (rows.isEmpty()) {
            throw BaseResponse.parameterInvalid.error("未找到该实习项目下的学生打卡流程（STUDENT_SIGN）");
        }
        if (rows.size() > 1) {
            throw BaseResponse.parameterInvalid.error("该实习项目下学生打卡流程（STUDENT_SIGN）配置不唯一");
        }
        return FastJsonUtil.toJson(rows.get(0));
    }
}
