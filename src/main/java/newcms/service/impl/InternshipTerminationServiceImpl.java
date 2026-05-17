package newcms.service.impl;

import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.base.Constant;
import newcms.entity.db.*;
import newcms.repository.db.*;
import newcms.service.ICommonService;
import newcms.service.IInternshipTerminationService;
import newcms.service.IVerifyProcessService;
import newcms.utils.FastJsonUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(rollbackFor = Exception.class)
public class InternshipTerminationServiceImpl extends Base implements IInternshipTerminationService {
    private static final String TABLE_TERMINATION = "MainInternshipTermination";
    private static final String TABLE_VERIFY = "MainVerifyProcess";
    private static final String TABLE_EXTERNAL = "RelStuInternshipPost";
    private static final String TABLE_INTERNAL = "RelTitleStudent";

    @Resource
    private ICommonService iCommonService;
    @Resource
    private IVerifyProcessService iVerifyProcessService;
    @Resource
    private MainInternshipTerminationDao mainInternshipTerminationDao;
    @Resource
    private MainVerifyProcessDao mainVerifyProcessDao;
    @Resource
    private RelStuInternshipPostDao relStuInternshipPostDao;
    @Resource
    private RelTitleStudentDao relTitleStudentDao;
    @Resource
    private RelTitleTeacherDao relTitleTeacherDao;
    @Resource
    private MainInternshipPostDao mainInternshipPostDao;

    @Override
    public Object listCandidates(JSONObject searchKeys, Map<String, String> regMap, Sort sort, Integer page, Integer size) {
        return iCommonService.getSomeRecords("ViewStudentInternshipTerminationCandidate",
                searchKeys == null ? new JSONObject() : searchKeys,
                regMap, sort == null ? Sort.by(Sort.Direction.DESC, "id") : sort, page, size);
    }

    @Override
    public Object listAudits(JSONObject searchKeys, Map<String, String> regMap, Sort sort, Integer page, Integer size) {
        return iCommonService.getSomeRecords("ViewVerifyStudentInternshipTerminationMerge",
                searchKeys == null ? new JSONObject() : searchKeys,
                regMap, sort == null ? Sort.by(Sort.Direction.DESC, "id") : sort, page, size);
    }

    @Override
    public Object create(JSONObject node, Integer currentUserId) {
        if (node == null) {
            throw BaseResponse.parameterInvalid.error("node cannot be empty");
        }
        if (currentUserId == null) {
            throw BaseResponse.parameterInvalid.error("currentUserId cannot be empty");
        }

        String relationTable = normalizeRelationTable(node.getString("relationTable"));
        Integer relationId = node.getInteger("relationId");
        if (relationId == null) {
            throw BaseResponse.parameterInvalid.error("relationId cannot be empty");
        }
        RelationInfo relationInfo = resolveRelationInfo(relationTable, relationId);
        assertRelationBelongsToCurrentStudent(relationInfo, currentUserId);

        Integer internshipId = node.getInteger("internshipId");
        Integer studentId = node.getInteger("studentId");
        if (internshipId != null && !internshipId.equals(relationInfo.internshipId)) {
            throw BaseResponse.parameterInvalid.error("internshipId does not match relation");
        }
        if (studentId != null && !studentId.equals(currentUserId)) {
            throw BaseResponse.lackPermissions.error("students can only apply to terminate their own internship");
        }
        internshipId = relationInfo.internshipId;
        studentId = currentUserId;

        Integer relationStatus = relationInfo.internshipStatus == null
                ? Constant.INTERNSHIP_RELATION_STATUS.ACTIVE : relationInfo.internshipStatus;
        if (relationStatus == Constant.INTERNSHIP_RELATION_STATUS.TERMINATED) {
            throw BaseResponse.parameterInvalid.error("student internship has already been terminated");
        }
        if (relationStatus == Constant.INTERNSHIP_RELATION_STATUS.TERMINATING) {
            throw BaseResponse.parameterInvalid.error("student internship is already in termination review");
        }
        ensureNoActiveTermination(relationTable, relationId);

        JSONObject processJson = FastJsonUtil.toJson(
                iVerifyProcessService.GetInternshipProcess(internshipId,
                        Constant.PROCESS_TYPE.STUDENT_INTERNSHIP_TERMINATION));
        Integer processId = processJson.getInteger("id");
        Integer verifyTypeId = defaultVerifyType(processJson.getInteger("verifyTypeId"));
        boolean needsVerify = verifyTypeId >= Constant.VERIFY_LEVEL.ONE_VERIFY;

        JSONObject terminationJson = new JSONObject();
        terminationJson.put("internshipId", internshipId);
        terminationJson.put("studentId", studentId);
        terminationJson.put("relationTable", relationTable);
        terminationJson.put("relationId", relationId);
        terminationJson.put("terminateDate", node.getDate("terminateDate"));
        terminationJson.put("reasonType", normalizeOptionalText(node.getString("reasonType")));
        terminationJson.put("reason", normalizeOptionalText(node.getString("reason")));
        terminationJson.put("attachmentIds", normalizeOptionalText(node.getString("attachmentIds")));
        terminationJson.put("applyUserId", currentUserId);
        terminationJson.put("status", needsVerify
                ? Constant.INTERNSHIP_TERMINATION_STATUS.PENDING
                : Constant.INTERNSHIP_TERMINATION_STATUS.APPROVED);
        terminationJson.put("verifyTypeId", verifyTypeId);
        terminationJson.put("verifyFirstRoleId", processJson.getInteger("verifyFirstRoleId"));
        terminationJson.put("verifySecondRoleId", processJson.getInteger("verifySecondRoleId"));
        terminationJson.put("verifyThirdRoleId", processJson.getInteger("verifyThirdRoleId"));
        terminationJson.put("verifyFourthRoleId", processJson.getInteger("verifyFourthRoleId"));
        terminationJson.put("verifyFifthRoleId", processJson.getInteger("verifyFifthRoleId"));
        terminationJson.put("currentVerifyTypeId", needsVerify
                ? Constant.VERIFY_LEVEL.ONE_VERIFY
                : verifyTypeId + 1);

        Object savedTermination = iCommonService.saveOneRecord(TABLE_TERMINATION, terminationJson);
        Integer terminationId = FastJsonUtil.toJson(savedTermination).getInteger("id");
        bindAttachmentsToTermination(terminationId, node.getString("attachmentIds"));
        markRelation(relationTable, relationId, Constant.INTERNSHIP_RELATION_STATUS.TERMINATING, terminationId);

        JSONObject verifyJson = new JSONObject();
        verifyJson.put("relationId", terminationId);
        verifyJson.put("processId", processId);
        verifyJson.put("createUserId", currentUserId);
        verifyJson.put("tableName", TABLE_TERMINATION);
        verifyJson.put("reason", "");

        if (needsVerify) {
            Integer verifyFirstRoleId = processJson.getInteger("verifyFirstRoleId");
            String verifyUserId = iVerifyProcessService.GetVerifyUserId(verifyFirstRoleId, currentUserId, internshipId);
            if (verifyUserId == null || verifyUserId.isBlank()) {
                throw BaseResponse.moreInfoError.error("termination audit user is not configured");
            }
            verifyJson.put("verifyUserId", verifyUserId);
            verifyJson.put("isAudit", Constant.AUDIT_STATUS.SUBMIT);
            iCommonService.saveOneRecord(TABLE_VERIFY, verifyJson);
        } else {
            verifyJson.put("verifyUserId", Constant.SYSTEM_AUDIT_NOTE.AUTO_PASS);
            verifyJson.put("reason", Constant.SYSTEM_AUDIT_NOTE.AUTO_PASS);
            verifyJson.put("isAudit", Constant.AUDIT_STATUS.PASS);
            iCommonService.saveOneRecord(TABLE_VERIFY, verifyJson);
            afterAuditPassed(terminationId, currentUserId);
        }

        return iCommonService.getOneRecordById(TABLE_TERMINATION, terminationId);
    }

    @Override
    public Object detail(Integer terminationId) {
        if (terminationId == null) {
            throw BaseResponse.parameterInvalid.error("terminationId cannot be empty");
        }
        Object termination = iCommonService.getOneRecordById(TABLE_TERMINATION, terminationId);
        if (termination == null) {
            throw BaseResponse.parameterInvalid.error("termination record does not exist");
        }
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("relationId", terminationId);
        searchKeys.put("tableName", TABLE_TERMINATION);
        @SuppressWarnings("unchecked")
        Page<Object> verifyPage = (Page<Object>) iCommonService.getSomeRecords(
                TABLE_VERIFY, searchKeys, null, Sort.by(Sort.Direction.ASC, "id"), 1, 100);
        JSONObject result = new JSONObject();
        result.put("termination", termination);
        result.put("verifyProcesses", verifyPage.getContent());
        result.put("attachments", listAttachmentsForTermination(terminationId));
        return result;
    }

    private List<JSONObject> listAttachmentsForTermination(Integer terminationId) {
        List<SysOssFile> files = sysOssFileDao.findByRelationIdsAndTableNameAndIsDeletedFalse(
                terminationId, TABLE_TERMINATION);
        List<JSONObject> out = new ArrayList<>(files.size());
        for (SysOssFile file : files) {
            JSONObject item = FastJsonUtil.toJson(file);
            item.put("url", "/common/minio/file/" + file.getId());
            item.put("previewUrl", "/common/minio/preview/" + file.getId());
            item.put("downloadUrl", "/common/minio/download/" + file.getId());
            out.add(item);
        }
        return out;
    }

    @Override
    public Object cancel(Integer terminationId, Integer currentUserId) {
        if (terminationId == null) {
            throw BaseResponse.parameterInvalid.error("terminationId cannot be empty");
        }
        MainInternshipTermination termination = mainInternshipTerminationDao.getByIdAndIsDeletedFalse(terminationId);
        if (termination == null) {
            throw BaseResponse.parameterInvalid.error("termination record does not exist");
        }
        assertOwnTermination(termination, currentUserId);
        if (Objects.equals(termination.getStatus(), Constant.INTERNSHIP_TERMINATION_STATUS.APPROVED)) {
            throw BaseResponse.parameterInvalid.error("approved termination cannot be cancelled");
        }
        JSONObject update = new JSONObject();
        update.put("id", terminationId);
        update.put("status", Constant.INTERNSHIP_TERMINATION_STATUS.CANCELLED);
        iCommonService.saveOneRecord(TABLE_TERMINATION, update);
        markRelation(termination.getRelationTable(), termination.getRelationId(),
                Constant.INTERNSHIP_RELATION_STATUS.ACTIVE, 0);
        deletePendingVerifyProcesses(terminationId);
        return iCommonService.getOneRecordById(TABLE_TERMINATION, terminationId);
    }

    @Override
    public Object resubmit(JSONObject node, Integer currentUserId) {
        if (node == null) {
            throw BaseResponse.parameterInvalid.error("node cannot be empty");
        }
        Integer terminationId = node.getInteger("terminationId");
        if (terminationId == null) {
            terminationId = node.getInteger("id");
        }
        if (terminationId == null) {
            throw BaseResponse.parameterInvalid.error("terminationId cannot be empty");
        }
        MainInternshipTermination termination = mainInternshipTerminationDao.getByIdAndIsDeletedFalse(terminationId);
        if (termination == null) {
            throw BaseResponse.parameterInvalid.error("termination record does not exist");
        }
        assertOwnTermination(termination, currentUserId);
        if (!Objects.equals(termination.getStatus(), Constant.INTERNSHIP_TERMINATION_STATUS.RETURNED)) {
            throw BaseResponse.parameterInvalid.error("only returned termination can be resubmitted");
        }
        MainVerifyProcess pending = latestPendingSubmitRecord(terminationId);
        if (pending == null) {
            throw BaseResponse.parameterInvalid.error("pending resubmit audit record does not exist");
        }

        JSONObject update = new JSONObject();
        update.put("id", terminationId);
        update.put("status", Constant.INTERNSHIP_TERMINATION_STATUS.PENDING);
        if (node.containsKey("terminateDate")) {
            update.put("terminateDate", node.getDate("terminateDate"));
        }
        if (node.containsKey("reasonType")) {
            update.put("reasonType", normalizeOptionalText(node.getString("reasonType")));
        }
        if (node.containsKey("reason")) {
            update.put("reason", normalizeOptionalText(node.getString("reason")));
        }
        if (node.containsKey("attachmentIds")) {
            update.put("attachmentIds", normalizeOptionalText(node.getString("attachmentIds")));
            bindAttachmentsToTermination(terminationId, node.getString("attachmentIds"));
        }
        iCommonService.saveOneRecord(TABLE_TERMINATION, update);
        markRelation(termination.getRelationTable(), termination.getRelationId(),
                Constant.INTERNSHIP_RELATION_STATUS.TERMINATING, terminationId);

        JSONObject verifyUpdate = new JSONObject();
        verifyUpdate.put("id", pending.getId());
        verifyUpdate.put("isAudit", Constant.AUDIT_STATUS.SUBMIT);
        verifyUpdate.put("reason", "");
        iCommonService.saveOneRecord(TABLE_VERIFY, verifyUpdate);
        return detail(terminationId);
    }

    @Override
    public void afterAuditPassed(Integer terminationId, Integer auditUserId) {
        if (terminationId == null) {
            return;
        }
        MainInternshipTermination termination = mainInternshipTerminationDao.getByIdAndIsDeletedFalse(terminationId);
        if (termination == null) {
            return;
        }
        Integer verifyTypeId = defaultVerifyType(termination.getVerifyTypeId());
        Integer currentVerifyTypeId = termination.getCurrentVerifyTypeId();
        if (currentVerifyTypeId == null || currentVerifyTypeId <= verifyTypeId) {
            return;
        }
        Date now = new Date();
        JSONObject update = new JSONObject();
        update.put("id", terminationId);
        update.put("status", Constant.INTERNSHIP_TERMINATION_STATUS.APPROVED);
        update.put("approvedTime", now);
        update.put("approvedBy", auditUserId);
        iCommonService.saveOneRecord(TABLE_TERMINATION, update);
        markRelation(termination.getRelationTable(), termination.getRelationId(),
                Constant.INTERNSHIP_RELATION_STATUS.TERMINATED, terminationId, now);
    }

    @Override
    public void afterAuditRejectedOrReturned(Integer terminationId, Integer isAudit) {
        if (terminationId == null || isAudit == null) {
            return;
        }
        MainInternshipTermination termination = mainInternshipTerminationDao.getByIdAndIsDeletedFalse(terminationId);
        if (termination == null) {
            return;
        }
        if (isAudit == Constant.AUDIT_STATUS.NOTPASS) {
            if (Objects.equals(termination.getStatus(), Constant.INTERNSHIP_TERMINATION_STATUS.APPROVED)) {
                return;
            }
            JSONObject update = new JSONObject();
            update.put("id", terminationId);
            update.put("status", Constant.INTERNSHIP_TERMINATION_STATUS.REJECTED);
            iCommonService.saveOneRecord(TABLE_TERMINATION, update);
            markRelation(termination.getRelationTable(), termination.getRelationId(),
                    Constant.INTERNSHIP_RELATION_STATUS.ACTIVE, 0);
        } else if (isAudit == Constant.AUDIT_STATUS.BACK) {
            JSONObject update = new JSONObject();
            update.put("id", terminationId);
            update.put("status", Constant.INTERNSHIP_TERMINATION_STATUS.RETURNED);
            iCommonService.saveOneRecord(TABLE_TERMINATION, update);
            markRelation(termination.getRelationTable(), termination.getRelationId(),
                    Constant.INTERNSHIP_RELATION_STATUS.TERMINATING, terminationId);
        }
    }

    @Override
    public void assertNotTerminated(String relationTable, Integer relationId) {
        if (relationId == null) {
            return;
        }
        String normalizedTable = normalizeRelationTable(relationTable);
        RelationInfo relationInfo = resolveRelationInfo(normalizedTable, relationId);
        Integer status = relationInfo.internshipStatus == null
                ? Constant.INTERNSHIP_RELATION_STATUS.ACTIVE : relationInfo.internshipStatus;
        if (status == Constant.INTERNSHIP_RELATION_STATUS.TERMINATED) {
            throw BaseResponse.parameterInvalid.error("student internship has been terminated");
        }
    }

    private void ensureNoActiveTermination(String relationTable, Integer relationId) {
        List<Integer> activeStatuses = Arrays.asList(
                Constant.INTERNSHIP_TERMINATION_STATUS.PENDING,
                Constant.INTERNSHIP_TERMINATION_STATUS.RETURNED);
        List<MainInternshipTermination> active = mainInternshipTerminationDao
                .findByRelationTableAndRelationIdAndStatusInAndIsDeletedFalse(relationTable, relationId, activeStatuses);
        if (active != null && !active.isEmpty()) {
            throw BaseResponse.parameterInvalid.error("active termination application already exists");
        }
    }

    private void assertRelationBelongsToCurrentStudent(RelationInfo relationInfo, Integer currentUserId) {
        if (relationInfo == null || currentUserId == null || !Objects.equals(relationInfo.studentId, currentUserId)) {
            throw BaseResponse.lackPermissions.error("students can only apply to terminate their own internship");
        }
    }

    private void assertOwnTermination(MainInternshipTermination termination, Integer currentUserId) {
        if (termination == null || currentUserId == null || !Objects.equals(termination.getStudentId(), currentUserId)) {
            throw BaseResponse.lackPermissions.error("students can only operate their own termination application");
        }
    }

    private RelationInfo resolveRelationInfo(String relationTable, Integer relationId) {
        if (TABLE_EXTERNAL.equals(relationTable)) {
            RelStuInternshipPost rel = relStuInternshipPostDao.getByIdAndIsDeletedFalse(relationId);
            if (rel == null) {
                throw BaseResponse.parameterInvalid.error("external internship relation does not exist");
            }
            MainInternshipPost post = mainInternshipPostDao.getByIdAndIsDeletedFalse(rel.getInternshipPostId());
            if (post == null || post.getInternshipId() == null) {
                throw BaseResponse.parameterInvalid.error("external internship project does not exist");
            }
            return new RelationInfo(post.getInternshipId(), rel.getStudentId(), rel.getInternshipStatus());
        }
        if (TABLE_INTERNAL.equals(relationTable)) {
            RelTitleStudent rel = relTitleStudentDao.getByIdAndIsDeletedFalse(relationId);
            if (rel == null) {
                throw BaseResponse.parameterInvalid.error("internal internship relation does not exist");
            }
            Integer internshipId = rel.getInternshipId();
            if (internshipId == null && rel.getTitleId() != null) {
                RelTitleTeacher title = relTitleTeacherDao.getByIdAndIsDeletedFalse(rel.getTitleId());
                internshipId = title == null ? null : title.getInternshipId();
            }
            if (internshipId == null) {
                throw BaseResponse.parameterInvalid.error("internal internship project does not exist");
            }
            return new RelationInfo(internshipId, rel.getStuId(), rel.getInternshipStatus());
        }
        throw BaseResponse.parameterInvalid.error("relationTable must be RelStuInternshipPost or RelTitleStudent");
    }

    private String normalizeRelationTable(String relationTable) {
        if (relationTable == null || relationTable.isBlank()) {
            throw BaseResponse.parameterInvalid.error("relationTable cannot be empty");
        }
        String normalized = relationTable.replace("_", "").trim().toLowerCase(Locale.ROOT);
        if ("relstuinternshippost".equals(normalized)) {
            return TABLE_EXTERNAL;
        }
        if ("reltitlestudent".equals(normalized)) {
            return TABLE_INTERNAL;
        }
        throw BaseResponse.parameterInvalid.error("relationTable must be RelStuInternshipPost or RelTitleStudent");
    }

    private void markRelation(String relationTable, Integer relationId, Integer status, Integer terminationId) {
        markRelation(relationTable, relationId, status, terminationId, null);
    }

    private void markRelation(String relationTable, Integer relationId, Integer status, Integer terminationId, Date terminatedTime) {
        String normalizedTable = normalizeRelationTable(relationTable);
        JSONObject update = new JSONObject();
        update.put("id", relationId);
        update.put("internshipStatus", status);
        update.put("terminationId", terminationId == null ? 0 : terminationId);
        if (terminatedTime != null) {
            update.put("terminatedTime", terminatedTime);
        }
        iCommonService.saveOneRecord(normalizedTable, update);
    }

    private void deletePendingVerifyProcesses(Integer terminationId) {
        List<MainVerifyProcess> pending = mainVerifyProcessDao
                .findByRelationIdAndTableNameAndIsAuditInAndIsDeletedFalse(
                        terminationId, TABLE_TERMINATION,
                        Arrays.asList(Constant.AUDIT_STATUS.SAVE, Constant.AUDIT_STATUS.SUBMIT));
        for (MainVerifyProcess process : pending) {
            iCommonService.deleteRecordByDelflag(TABLE_VERIFY, process.getId());
        }
    }

    private MainVerifyProcess latestPendingSubmitRecord(Integer terminationId) {
        List<MainVerifyProcess> pending = mainVerifyProcessDao
                .findByRelationIdAndTableNameAndIsAuditInAndIsDeletedFalse(
                        terminationId, TABLE_TERMINATION, Collections.singletonList(Constant.AUDIT_STATUS.SAVE));
        return pending.stream().max(Comparator.comparing(MainVerifyProcess::getId)).orElse(null);
    }

    private Integer defaultVerifyType(Integer verifyTypeId) {
        return verifyTypeId == null ? Constant.VERIFY_LEVEL.NO_VERIFY : verifyTypeId;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || "-".equals(trimmed) ? null : trimmed;
    }

    /**
     * 将逗号分隔的 SysOssFile id 与本终止记录绑定（写 tableName + relationIds），与其他业务表的附件标准模式一致。
     * 同时保留实体 attachmentIds CSV 列以便旧前端读取，过渡完成后可删列。
     */
    private void bindAttachmentsToTermination(Integer terminationId, String attachmentIdsCsv) {
        if (terminationId == null) {
            return;
        }
        String normalized = normalizeOptionalText(attachmentIdsCsv);
        if (normalized == null) {
            return;
        }
        for (String piece : normalized.split(Constant.SPLIT_OPERATOR.COMMA)) {
            String trimmed = piece.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Integer fileId;
            try {
                fileId = Integer.valueOf(trimmed);
            } catch (NumberFormatException ignored) {
                continue;
            }
            JSONObject patch = new JSONObject();
            patch.put("id", fileId);
            patch.put("tableName", TABLE_TERMINATION);
            patch.put("relationIds", terminationId);
            iCommonService.saveOneRecord("SysOssFile", patch);
        }
    }

    private static class RelationInfo {
        private final Integer internshipId;
        private final Integer studentId;
        private final Integer internshipStatus;

        private RelationInfo(Integer internshipId, Integer studentId, Integer internshipStatus) {
            this.internshipId = internshipId;
            this.studentId = studentId;
            this.internshipStatus = internshipStatus;
        }
    }
}
