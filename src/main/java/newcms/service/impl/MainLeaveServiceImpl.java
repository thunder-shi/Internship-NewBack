package newcms.service.impl;

import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.base.Constant;
import newcms.entity.db.MainInternshipPost;
import newcms.entity.db.MainLeave;
import newcms.entity.db.MainVerifyProcess;
import newcms.entity.db.RelStuInternshipPost;
import newcms.entity.db.RelTitleStudent;
import newcms.entity.db.RelTitleTeacher;
import newcms.repository.db.MainInternshipPostDao;
import newcms.repository.db.MainLeaveDao;
import newcms.repository.db.MainVerifyProcessDao;
import newcms.repository.db.RelStuInternshipPostDao;
import newcms.repository.db.RelTitleStudentDao;
import newcms.repository.db.RelTitleTeacherDao;
import newcms.service.ICommonService;
import newcms.service.IInternshipTerminationService;
import newcms.service.IMainLeaveService;
import newcms.service.IVerifyProcessService;
import newcms.utils.FastJsonUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional(rollbackFor = Exception.class)
public class MainLeaveServiceImpl extends Base implements IMainLeaveService {

    /** 按 leaveId 分段的应用层锁，防止并发提交同一请假记录时重复创建审核行 */
    private static final ConcurrentHashMap<Integer, Object> LEAVE_LOCKS = new ConcurrentHashMap<>();

    @Resource
    private ICommonService iCommonService;
    @Resource
    private MainLeaveDao mainLeaveDao;
    @Resource
    private MainVerifyProcessDao mainVerifyProcessDao;
    @Resource
    private IVerifyProcessService iVerifyProcessService;
    @Resource
    private RelStuInternshipPostDao relStuInternshipPostDao;
    @Resource
    private MainInternshipPostDao mainInternshipPostDao;
    @Resource
    private RelTitleStudentDao relTitleStudentDao;
    @Resource
    private RelTitleTeacherDao relTitleTeacherDao;
    @Resource
    private IInternshipTerminationService internshipTerminationService;

    @Override
    public void ensureSubmitVerifyProcess(Integer leaveId, Integer currentUserId) {
        ensureSubmitVerifyProcess(leaveId, currentUserId, null, null);
    }

    @Override
    public void ensureSubmitVerifyProcess(Integer leaveId, Integer currentUserId, Integer processId, String processTypeCode) {
        MainLeave leave = mainLeaveDao.getByIdAndIsDeletedFalse(leaveId);
        if (leave == null) {
            throw BaseResponse.parameterInvalid.error("请假记录不存在");
        }

        assertLeaveRelationNotTerminated(leave);
        Object lock = LEAVE_LOCKS.computeIfAbsent(leaveId, k -> new Object());
        synchronized (lock) {
            List<MainVerifyProcess> existing = mainVerifyProcessDao
                    .findByRelationIdAndTableNameAndIsDeletedFalse(leaveId, "MainLeave");
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

            JSONObject leaveJson = FastJsonUtil.toJson(iCommonService.getOneRecordById("MainLeave", leaveId));
            Integer leaveInternshipId = resolveLeaveInternshipId(leave);
            JSONObject processJson = resolveLeaveProcessJson(leaveId, leaveInternshipId, processId, processTypeCode);
            Integer resolvedProcessId = processJson != null ? processJson.getInteger("id") : null;
            Integer internshipIdForVerify = processJson != null ? processJson.getInteger("internshipId") : leaveInternshipId;
            Integer verifyTypeId = getConfigInteger(processJson, leaveJson, "verifyTypeId");
            if (verifyTypeId == null) {
                verifyTypeId = Constant.VERIFY_LEVEL.ONE_VERIFY;
            }
            Integer verifyFirstRoleId = getConfigInteger(processJson, leaveJson, "verifyFirstRoleId");

            String verifyUserId = null;
            int isAudit = Constant.AUDIT_STATUS.SUBMIT;
            String reason = "";

            if (verifyTypeId >= Constant.VERIFY_LEVEL.ONE_VERIFY && verifyFirstRoleId != null && verifyFirstRoleId > 0) {
                String fromRole = iVerifyProcessService.GetVerifyUserId(verifyFirstRoleId, currentUserId, internshipIdForVerify);
                if (fromRole != null && !fromRole.isBlank()) {
                    verifyUserId = fromRole;
                }
            }
            if (verifyTypeId < Constant.VERIFY_LEVEL.ONE_VERIFY || verifyUserId == null || verifyUserId.isBlank()) {
                verifyUserId = Constant.SYSTEM_AUDIT_NOTE.AUTO_PASS;
                isAudit = Constant.AUDIT_STATUS.PASS;
                reason = Constant.SYSTEM_AUDIT_NOTE.AUTO_PASS;
            }

            JSONObject leaveState = new JSONObject();
            leaveState.put("id", leaveId);
            if (leave.getVerifyTypeId() == null || processJson != null) {
                leaveState.put("verifyTypeId", verifyTypeId);
            }
            if (isAudit == Constant.AUDIT_STATUS.PASS) {
                leaveState.put("currentVerifyTypeId", verifyTypeId + 1);
            } else {
                leaveState.put("currentVerifyTypeId", Constant.VERIFY_LEVEL.ONE_VERIFY);
            }
            iCommonService.saveOneRecord("MainLeave", leaveState);

            JSONObject verifyJson = new JSONObject();
            verifyJson.put("relationId", leaveId);
            verifyJson.put("processId", resolvedProcessId);
            verifyJson.put("createUserId", currentUserId);
            verifyJson.put("verifyUserId", verifyUserId);
            verifyJson.put("isAudit", isAudit);
            verifyJson.put("reason", reason);
            verifyJson.put("tableName", "MainLeave");
            iCommonService.saveOneRecord("MainVerifyProcess", verifyJson);
        }
    }

    private Integer getConfigInteger(JSONObject processJson, JSONObject fallbackJson, String key) {
        Integer value = processJson != null ? processJson.getInteger(key) : null;
        return value != null ? value : fallbackJson.getInteger(key);
    }

    @SuppressWarnings("unchecked")
    private JSONObject resolveLeaveProcessJson(Integer leaveId, Integer internshipId, Integer processId,
                                               String processTypeCode) {
        if (processId != null) {
            Object processObj = iCommonService.getOneRecordById("RelProcessInternship", processId);
            if (processObj == null) {
                throw BaseResponse.parameterInvalid.error("processId does not exist");
            }
            JSONObject processJson = FastJsonUtil.toJson(processObj);
            Integer processInternshipId = processJson.getInteger("internshipId");
            if (internshipId != null && processInternshipId != null && !internshipId.equals(processInternshipId)) {
                throw BaseResponse.parameterInvalid.error("processId does not belong to this leave record");
            }
            return processJson;
        }

        if (internshipId == null) {
            logger.warn("Leave {} cannot resolve internshipId, processId remains null", leaveId);
            return null;
        }

        String normalizedProcessTypeCode = normalizeOptionalText(processTypeCode);
        if (normalizedProcessTypeCode != null) {
            JSONObject processJson = findProcessByCode(internshipId, normalizedProcessTypeCode);
            if (processJson == null) {
                throw BaseResponse.parameterInvalid.error("processTypeCode does not match this internship");
            }
            return processJson;
        }

        JSONObject leaveProcessJson = findLeaveProcess(internshipId);
        if (leaveProcessJson == null) {
            logger.warn("Leave {} cannot find leave process config for internship {}", leaveId, internshipId);
        }
        return leaveProcessJson;
    }

    @SuppressWarnings("unchecked")
    private JSONObject findProcessByCode(Integer internshipId, String processTypeCode) {
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("internshipId", internshipId);
        searchKeys.put("processTypeCode", processTypeCode);
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                "ViewRelProcessInternship", searchKeys, null,
                Sort.by(Sort.Direction.ASC, "theOrder"), 1, 1);
        if (page == null || page.getContent() == null || page.getContent().isEmpty()) {
            return null;
        }
        return FastJsonUtil.toJson(page.getContent().get(0));
    }

    @SuppressWarnings("unchecked")
    private JSONObject findLeaveProcess(Integer internshipId) {
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("internshipId", internshipId);
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                "ViewRelProcessInternship", searchKeys, null,
                Sort.by(Sort.Direction.ASC, "theOrder"), 1, 100);
        if (page == null || page.getContent() == null || page.getContent().isEmpty()) {
            return null;
        }

        List<JSONObject> matches = new java.util.ArrayList<>();
        for (Object item : page.getContent()) {
            JSONObject processJson = FastJsonUtil.toJson(item);
            if (looksLikeLeaveProcess(processJson)) {
                matches.add(processJson);
            }
        }
        if (matches.isEmpty()) {
            return null;
        }
        if (matches.size() > 1) {
            logger.warn("Multiple leave-like process configs found for internship {}, use first one", internshipId);
        }
        return matches.get(0);
    }

    private boolean looksLikeLeaveProcess(JSONObject processJson) {
        if (processJson == null) {
            return false;
        }
        String code = normalizeOptionalText(processJson.getString("processTypeCode"));
        if (code != null) {
            String upperCode = code.toUpperCase(Locale.ROOT);
            if (upperCode.contains("LEAVE") || upperCode.contains("ABSENCE") || upperCode.contains("VACATION")) {
                return true;
            }
        }
        String name = normalizeOptionalText(processJson.getString("processTypeName"));
        if (name == null) {
            return false;
        }
        String upperName = name.toUpperCase(Locale.ROOT);
        return upperName.contains("LEAVE")
                || name.contains("\u8bf7\u5047")
                || name.contains("\u5047");
    }

    private Integer resolveLeaveInternshipId(MainLeave leave) {
        if (leave == null || leave.getStuInternshipId() == null) {
            return null;
        }
        Integer relationId = leave.getStuInternshipId();
        String relationTable = resolveLeaveRelationTable(leave.getId());
        if (isRelationTable(relationTable, "relstuinternshippost")) {
            return resolveExternalInternshipId(relationId);
        }
        if (isRelationTable(relationTable, "reltitlestudent")) {
            return resolveInternalInternshipId(relationId);
        }

        Integer externalInternshipId = resolveExternalInternshipId(relationId);
        return externalInternshipId != null ? externalInternshipId : resolveInternalInternshipId(relationId);
    }

    private void assertLeaveRelationNotTerminated(MainLeave leave) {
        if (leave == null || leave.getStuInternshipId() == null) {
            return;
        }
        Integer relationId = leave.getStuInternshipId();
        String relationTable = resolveLeaveRelationTable(leave.getId());
        if (isRelationTable(relationTable, "reltitlestudent")) {
            internshipTerminationService.assertNotTerminated("RelTitleStudent", relationId);
            return;
        }
        if (isRelationTable(relationTable, "relstuinternshippost")) {
            internshipTerminationService.assertNotTerminated("RelStuInternshipPost", relationId);
            return;
        }
        if (resolveExternalInternshipId(relationId) != null) {
            internshipTerminationService.assertNotTerminated("RelStuInternshipPost", relationId);
        } else if (resolveInternalInternshipId(relationId) != null) {
            internshipTerminationService.assertNotTerminated("RelTitleStudent", relationId);
        }
    }

    @SuppressWarnings("unchecked")
    private String resolveLeaveRelationTable(Integer leaveId) {
        if (leaveId == null) {
            return null;
        }
        try {
            JSONObject searchKeys = new JSONObject();
            searchKeys.put("leaveId", leaveId);
            Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                    "ViewLeaveUniversalDetails", searchKeys, null, Sort.unsorted(), 1, 1);
            if (page != null && page.getContent() != null && !page.getContent().isEmpty()) {
                return FastJsonUtil.toJson(page.getContent().get(0)).getString("relationTable");
            }
        } catch (Exception ex) {
            logger.warn("Resolve leave relation table failed: {}", ex.getMessage());
        }
        return null;
    }

    private Integer resolveExternalInternshipId(Integer relStuInternshipPostId) {
        RelStuInternshipPost rel = relStuInternshipPostDao.getByIdAndIsDeletedFalse(relStuInternshipPostId);
        if (rel == null || rel.getInternshipPostId() == null) {
            return null;
        }
        MainInternshipPost post = mainInternshipPostDao.getByIdAndIsDeletedFalse(rel.getInternshipPostId());
        return post != null ? post.getInternshipId() : null;
    }

    private Integer resolveInternalInternshipId(Integer relTitleStudentId) {
        RelTitleStudent rel = relTitleStudentDao.getByIdAndIsDeletedFalse(relTitleStudentId);
        if (rel == null || rel.getTitleId() == null) {
            return null;
        }
        RelTitleTeacher title = relTitleTeacherDao.getByIdAndIsDeletedFalse(rel.getTitleId());
        return title != null ? title.getInternshipId() : null;
    }

    private boolean isRelationTable(String relationTable, String normalizedName) {
        if (relationTable == null) {
            return false;
        }
        String normalized = relationTable.replace("_", "").toLowerCase(Locale.ROOT);
        return normalized.contains(normalizedName);
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() || "-".equals(normalized) ? null : normalized;
    }
}
