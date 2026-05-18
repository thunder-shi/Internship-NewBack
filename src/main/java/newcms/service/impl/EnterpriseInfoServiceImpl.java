package newcms.service.impl;

import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.base.Constant;
import newcms.entity.db.BaseDepartment;
import newcms.entity.db.BaseEnterpriseVerifyConfig;
import newcms.entity.db.BaseUser;
import newcms.entity.db.MainEnterpriseInfo;
import newcms.entity.db.MainVerifyProcess;
import newcms.entity.db.RelUserRole;
import newcms.entity.db.SysOssFile;
import newcms.entity.db.SysRole;
import newcms.repository.db.BaseEnterpriseVerifyConfigDao;
import newcms.repository.db.MainEnterpriseInfoDao;
import newcms.repository.db.MainVerifyProcessDao;
import newcms.repository.db.RelUserRoleDao;
import newcms.service.ICommonService;
import newcms.service.IEnterpriseInfoService;
import newcms.service.IVerifyProcessService;
import newcms.utils.FastJsonUtil;
import newcms.utils.LogUtil;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class EnterpriseInfoServiceImpl extends Base implements IEnterpriseInfoService {
    private static final String TABLE_ENTERPRISE_INFO = "MainEnterpriseInfo";
    private static final String TABLE_VERIFY = "MainVerifyProcess";
    private static final String TABLE_OSS = "MainEnterpriseInfo";
    private static final Set<Integer> EDITABLE_STATUSES = Set.of(
            Constant.AUDIT_STATUS.SAVE,
            Constant.AUDIT_STATUS.BACK
    );

    @Resource
    private ICommonService iCommonService;
    @Resource
    private IVerifyProcessService iVerifyProcessService;
    @Resource
    private MainEnterpriseInfoDao mainEnterpriseInfoDao;
    @Resource
    private BaseEnterpriseVerifyConfigDao baseEnterpriseVerifyConfigDao;
    @Resource
    private MainVerifyProcessDao mainVerifyProcessDao;
    @Resource
    private RelUserRoleDao relUserRoleDao;

    @Override
    public Object getMine(Integer currentUserId) {
        CompanyContext context = requireCompanyContext(currentUserId);
        MainEnterpriseInfo currentApproved = resolveEffectiveApprovedRecord(context.companyId);
        List<MainEnterpriseInfo> all = mainEnterpriseInfoDao
                .findByCompanyIdAndIsDeletedFalseOrderByVersionNoDescIdDesc(context.companyId);
        MainEnterpriseInfo latest = all.isEmpty() ? null : all.get(0);

        JSONObject result = new JSONObject();
        result.put("companyId", context.companyId);
        result.put("schoolId", context.schoolId);
        result.put("companyName", context.company.getName());
        result.put("companyCode", context.company.getCode());
        result.put("currentApproved", currentApproved == null ? null : buildRecordSummary(currentApproved, false, null));
        result.put("latestRecord", latest == null ? null : buildDetailPayload(latest));
        result.put("verifyConfig", normalizeVerifyConfigJson(loadOrInitVerifyConfig(currentUserId)));
        return result;
    }

    @Override
    public Object listMyHistory(JSONObject searchKeys, Integer currentUserId, Sort sort, Integer page, Integer size) {
        CompanyContext context = requireCompanyContext(currentUserId);
        Map<Integer, Integer> effectiveIdByCompanyCache = new HashMap<>();
        List<JSONObject> rows = mainEnterpriseInfoDao
                .findByCompanyIdAndIsDeletedFalseOrderByVersionNoDescIdDesc(context.companyId)
                .stream()
                .map(record -> buildRecordSummary(record, true, effectiveIdByCompanyCache))
                .collect(Collectors.toList());
        List<JSONObject> filtered = filterMyHistory(rows, searchKeys);
        sortRows(filtered, sort);
        return toPage(filtered, page, size);
    }

    @Override
    public Object detail(Integer enterpriseInfoId, Integer currentUserId) {
        MainEnterpriseInfo record = requireEnterpriseInfo(enterpriseInfoId);
        if (!canCurrentUserViewRecord(currentUserId, record)) {
            throw BaseResponse.lackPermissions.error("no permission to view enterprise info");
        }
        return buildDetailPayload(record);
    }

    @Override
    public Object saveDraft(JSONObject node, Integer currentUserId) {
        MainEnterpriseInfo draft = saveDraftInternal(node, currentUserId);
        return buildDetailPayload(requireEnterpriseInfo(draft.getId()));
    }

    @Override
    public Object submit(JSONObject node, Integer currentUserId) {
        MainEnterpriseInfo record = submitInternal(node, currentUserId, false);
        return buildDetailPayload(requireEnterpriseInfo(record.getId()));
    }

    @Override
    public Object resubmit(JSONObject node, Integer currentUserId) {
        MainEnterpriseInfo record = submitInternal(node, currentUserId, true);
        return buildDetailPayload(requireEnterpriseInfo(record.getId()));
    }

    @Override
    public Object listAudits(JSONObject searchKeys, Integer currentUserId, Sort sort, Integer page, Integer size) {
        Integer schoolScope = getAuditSchoolScope(currentUserId);
        boolean schoolWide = canViewSchoolWide(currentUserId);
        String keyword = normalizeOptionalText(searchKeys == null ? null : searchKeys.getString("keyword"));
        Integer auditStatus = searchKeys == null ? null : searchKeys.getInteger("auditStatus");
        Boolean onlyMine = searchKeys != null && Boolean.TRUE.equals(searchKeys.getBoolean("onlyMine"));
        Boolean onlyEffectiveCurrent = searchKeys != null
                && Boolean.TRUE.equals(searchKeys.getBoolean("onlyEffectiveCurrent"));
        Integer companyId = searchKeys == null ? null : searchKeys.getInteger("companyId");
        Integer schoolId = searchKeys == null ? null : searchKeys.getInteger("schoolId");

        Integer effectiveSchoolFilter;
        if (schoolScope != null) {
            effectiveSchoolFilter = schoolScope;
        } else if (schoolWide && schoolId != null) {
            effectiveSchoolFilter = schoolId;
        } else {
            effectiveSchoolFilter = null;
        }

        Specification<MainEnterpriseInfo> spec = buildAuditListSpec(effectiveSchoolFilter, companyId, auditStatus);
        Sort dbSort = resolveDbSort(sort);
        List<MainEnterpriseInfo> records = mainEnterpriseInfoDao.findAll(spec, dbSort);
        if (records.isEmpty()) {
            return toPage(Collections.emptyList(), page, size);
        }

        List<Integer> recordIds = records.stream().map(MainEnterpriseInfo::getId).collect(Collectors.toList());
        Map<Integer, List<MainVerifyProcess>> verifyProcessByRecordId =
                mainVerifyProcessDao
                        .findByRelationIdInAndTableNameAndIsDeletedFalse(recordIds, TABLE_ENTERPRISE_INFO)
                        .stream()
                        .collect(Collectors.groupingBy(MainVerifyProcess::getRelationId));
        Map<Integer, Long> attachmentCountByRecordId =
                sysOssFileDao
                        .findByRelationIdsInAndTableNameAndIsDeletedFalse(recordIds, TABLE_OSS)
                        .stream()
                        .collect(Collectors.groupingBy(SysOssFile::getRelationIds, Collectors.counting()));

        Map<Integer, Integer> effectiveIdByCompanyCache = new HashMap<>();
        List<JSONObject> rows = new ArrayList<>(records.size());
        for (MainEnterpriseInfo record : records) {
            if (Boolean.TRUE.equals(onlyEffectiveCurrent)) {
                Integer effId = effectiveIdByCompanyCache.computeIfAbsent(
                        record.getCompanyId(), this::resolveEffectiveApprovedRecordId);
                if (effId == null || !effId.equals(record.getId())) {
                    continue;
                }
            }
            List<MainVerifyProcess> verifyProcesses = verifyProcessByRecordId.getOrDefault(
                    record.getId(), Collections.emptyList());
            long attachmentCount = attachmentCountByRecordId.getOrDefault(record.getId(), 0L);
            JSONObject row = buildRecordSummary(record, true, effectiveIdByCompanyCache,
                    verifyProcesses, attachmentCount);
            if (!matchesAuditVisibility(currentUserId, record, row, onlyMine)) {
                continue;
            }
            if (keyword != null && !matchesKeyword(row, keyword)) {
                continue;
            }
            rows.add(row);
        }
        return toPage(rows, page, size);
    }

    private Specification<MainEnterpriseInfo> buildAuditListSpec(
            Integer schoolId, Integer companyId, Integer auditStatus) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (schoolId != null) {
                predicates.add(cb.equal(root.get("schoolId"), schoolId));
            }
            if (companyId != null) {
                predicates.add(cb.equal(root.get("companyId"), companyId));
            }
            if (auditStatus != null) {
                predicates.add(cb.equal(root.get("auditStatus"), auditStatus));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Sort resolveDbSort(Sort sort) {
        if (sort == null || !sort.iterator().hasNext()) {
            return Sort.by(Sort.Direction.DESC, "id");
        }
        Sort.Order order = sort.iterator().next();
        String property = order.getProperty();
        // 仅允许实体已有列直接下推，其他列回退到 id 排序，避免 JPA 抛 PropertyReferenceException
        Set<String> entityColumns = Set.of("id", "versionNo", "auditStatus", "updateTime",
                "createTime", "code", "name", "approvedTime");
        if (!entityColumns.contains(property)) {
            property = "id";
        }
        return Sort.by(order.isAscending() ? Sort.Direction.ASC : Sort.Direction.DESC, property);
    }

    @Override
    public Object auditDetail(Integer enterpriseInfoId, Integer currentUserId) {
        MainEnterpriseInfo record = requireEnterpriseInfo(enterpriseInfoId);
        if (!canCurrentUserAuditRecord(currentUserId, record) && !canViewSchoolWide(currentUserId)) {
            throw BaseResponse.lackPermissions.error("no permission to view enterprise audit detail");
        }
        return buildDetailPayload(record);
    }

    @Override
    public Object audit(JSONObject node, Integer currentUserId) {
        if (node == null) {
            throw BaseResponse.parameterInvalid.error("node cannot be empty");
        }
        Integer verifyProcessId = firstInteger(node, "verifyProcessId", "id");
        Integer isAudit = node.getInteger("isAudit");
        if (verifyProcessId == null || isAudit == null) {
            throw BaseResponse.parameterInvalid.error("verifyProcessId and isAudit cannot be empty");
        }
        MainVerifyProcess verifyProcess = requireVerifyProcess(verifyProcessId);
        if (!TABLE_ENTERPRISE_INFO.equals(verifyProcess.getTableName())) {
            throw BaseResponse.parameterInvalid.error("verify process does not belong to enterprise info");
        }
        MainEnterpriseInfo record = requireEnterpriseInfo(verifyProcess.getRelationId());
        if (!canCurrentUserAuditRecord(currentUserId, record)) {
            throw BaseResponse.lackPermissions.error("no permission to audit this enterprise info");
        }
        boolean pendingLike = Objects.equals(verifyProcess.getIsAudit(), Constant.AUDIT_STATUS.SUBMIT)
                || Objects.equals(verifyProcess.getIsAudit(), Constant.AUDIT_STATUS.SAVE);
        // 审核通过后仍允许「退回 / 不通过」纠错：此时审核行已是 PASS，但企业单据仍为已通过态
        boolean revokeApproved = Objects.equals(verifyProcess.getIsAudit(), Constant.AUDIT_STATUS.PASS)
                && Objects.equals(record.getAuditStatus(), Constant.AUDIT_STATUS.PASS)
                && (Objects.equals(isAudit, Constant.AUDIT_STATUS.BACK)
                        || Objects.equals(isAudit, Constant.AUDIT_STATUS.NOTPASS));
        if (!pendingLike && !revokeApproved) {
            throw BaseResponse.parameterInvalid.error("verify process is not pending");
        }

        String auditText = auditReasonFromNode(node);
        JSONObject verifyUpdate = new JSONObject();
        verifyUpdate.put("id", verifyProcessId);
        verifyUpdate.put("isAudit", isAudit);
        verifyUpdate.put("reason", auditText);
        Object saved = iCommonService.saveOneRecord(TABLE_VERIFY, verifyUpdate);

        if (Objects.equals(isAudit, Constant.AUDIT_STATUS.PASS)) {
            iVerifyProcessService.onVerifyProcessApproved(verifyProcessId);
            MainEnterpriseInfo refreshed = requireEnterpriseInfo(record.getId());
            if (isVerifyFinished(refreshed)) {
                finalizeApproval(refreshed, currentUserId);
            } else {
                JSONObject update = new JSONObject();
                update.put("id", refreshed.getId());
                update.put("auditStatus", Constant.AUDIT_STATUS.SUBMIT);
                iCommonService.saveOneRecord(TABLE_ENTERPRISE_INFO, update);
            }
        } else if (Objects.equals(isAudit, Constant.AUDIT_STATUS.NOTPASS)) {
            JSONObject update = new JSONObject();
            update.put("id", record.getId());
            update.put("auditStatus", Constant.AUDIT_STATUS.NOTPASS);
            if (Boolean.TRUE.equals(record.getIsCurrent())
                    && Objects.equals(record.getAuditStatus(), Constant.AUDIT_STATUS.PASS)) {
                update.put("isCurrent", false);
                update.put("approvedBy", null);
                update.put("approvedTime", null);
            }
            iCommonService.saveOneRecord(TABLE_ENTERPRISE_INFO, update);
            syncIsCurrentForCompany(record.getCompanyId());
        } else if (Objects.equals(isAudit, Constant.AUDIT_STATUS.BACK)) {
            handleReturnedRecord(record, verifyProcess);
        } else {
            throw BaseResponse.parameterInvalid.error("isAudit must be pass, not pass or back");
        }

        return saved;
    }

    @Override
    public Object getVerifyConfig(Integer schoolId, Integer currentUserId) {
        ensureCanManageVerifyConfig(currentUserId);
        return normalizeVerifyConfigJson(loadOrInitVerifyConfig(currentUserId));
    }

    @Override
    public Object saveVerifyConfig(JSONObject node, Integer currentUserId) {
        if (node == null) {
            throw BaseResponse.parameterInvalid.error("node cannot be empty");
        }
        ensureCanManageVerifyConfig(currentUserId);
        Integer verifyTypeId = node.getInteger("verifyTypeId");
        if (verifyTypeId == null) {
            throw BaseResponse.parameterInvalid.error("verifyTypeId cannot be empty");
        }
        validateVerifyConfig(verifyTypeId, node);

        BaseEnterpriseVerifyConfig config = loadOrInitVerifyConfig(currentUserId);
        Integer schoolId = resolveGlobalConfigSchoolId(config, node.getInteger("schoolId"), currentUserId);
        JSONObject saveJson = new JSONObject();
        if (config.getId() != null) {
            saveJson.put("id", config.getId());
        }
        saveJson.put("schoolId", schoolId);
        saveJson.put("verifyTypeId", verifyTypeId);
        saveJson.put("verifyFirstRoleId", zeroIfNull(node.getInteger("verifyFirstRoleId")));
        saveJson.put("verifySecondRoleId", zeroIfNull(node.getInteger("verifySecondRoleId")));
        saveJson.put("verifyThirdRoleId", zeroIfNull(node.getInteger("verifyThirdRoleId")));
        saveJson.put("verifyFourthRoleId", zeroIfNull(node.getInteger("verifyFourthRoleId")));
        saveJson.put("verifyFifthRoleId", zeroIfNull(node.getInteger("verifyFifthRoleId")));
        saveJson.put("remarks", normalizeOptionalText(node.getString("remarks")));
        Object saved = iCommonService.saveOneRecord("BaseEnterpriseVerifyConfig", saveJson);
        return normalizeVerifyConfigJson((BaseEnterpriseVerifyConfig) saved);
    }

    @Override
    public boolean canAccessAttachment(Integer currentUserId, Integer enterpriseInfoId) {
        MainEnterpriseInfo record = mainEnterpriseInfoDao.getByIdAndIsDeletedFalse(enterpriseInfoId);
        return record != null && canCurrentUserViewRecord(currentUserId, record);
    }

    @Override
    public void assertCurrentUserCanDeclareExternal() {
        CompanyContext context = requireCompanyContext(getLoginUserId());
        MainEnterpriseInfo approved = resolveEffectiveApprovedRecord(context.companyId);
        if (approved == null
                || !Objects.equals(approved.getAuditStatus(), Constant.AUDIT_STATUS.PASS)) {
            throw BaseResponse.parameterInvalid.error("企业信息未审核通过，暂无校外实习申报资格");
        }
    }

    private MainEnterpriseInfo saveDraftInternal(JSONObject node, Integer currentUserId) {
        if (node == null) {
            throw BaseResponse.parameterInvalid.error("node cannot be empty");
        }
        CompanyContext context = requireCompanyContext(currentUserId);
        Integer recordId = firstInteger(node, "enterpriseInfoId", "id");
        if (recordId != null) {
            MainEnterpriseInfo existing = requireOwnedRecord(recordId, context.companyId);
            if (Objects.equals(existing.getAuditStatus(), Constant.AUDIT_STATUS.NOTPASS)) {
                return createNewDraft(context, node, existing);
            }
            if (!EDITABLE_STATUSES.contains(existing.getAuditStatus())) {
                throw BaseResponse.parameterInvalid.error("only draft or returned record can be edited");
            }
            JSONObject update = new JSONObject();
            update.put("id", existing.getId());
            update.put("auditStatus", Constant.AUDIT_STATUS.SAVE);
            applyEditableFields(update, node, context.company, false);
            iCommonService.saveOneRecord(TABLE_ENTERPRISE_INFO, update);
            return requireEnterpriseInfo(existing.getId());
        }

        return createNewDraft(context, node, latestRecordForCreate(context.companyId));
    }

    private MainEnterpriseInfo submitInternal(JSONObject node, Integer currentUserId, boolean forceResubmit) {
        if (node == null) {
            throw BaseResponse.parameterInvalid.error("node cannot be empty");
        }
        CompanyContext context = requireCompanyContext(currentUserId);
        Integer recordId = firstInteger(node, "enterpriseInfoId", "id");
        if (recordId == null) {
            throw BaseResponse.parameterInvalid.error("enterpriseInfoId cannot be empty");
        }
        MainEnterpriseInfo record = requireOwnedRecord(recordId, context.companyId);
        if (Objects.equals(record.getAuditStatus(), Constant.AUDIT_STATUS.BACK) || forceResubmit) {
            return doResubmit(record, node, currentUserId, context);
        }
        if (!Objects.equals(record.getAuditStatus(), Constant.AUDIT_STATUS.SAVE)) {
            throw BaseResponse.parameterInvalid.error("only draft record can be submitted");
        }

        JSONObject draftUpdate = new JSONObject();
        draftUpdate.put("id", record.getId());
        applyEditableFields(draftUpdate, node, context.company, false);
        iCommonService.saveOneRecord(TABLE_ENTERPRISE_INFO, draftUpdate);
        record = requireEnterpriseInfo(record.getId());
        validateSubmitRecord(record);
        BaseEnterpriseVerifyConfig config = requireVerifyConfig();
        JSONObject update = new JSONObject();
        update.put("id", record.getId());
        update.put("auditStatus", Constant.AUDIT_STATUS.SUBMIT);
        update.put("verifyTypeId", config.getVerifyTypeId());
        update.put("verifyFirstRoleId", zeroIfNull(config.getVerifyFirstRoleId()));
        update.put("verifySecondRoleId", zeroIfNull(config.getVerifySecondRoleId()));
        update.put("verifyThirdRoleId", zeroIfNull(config.getVerifyThirdRoleId()));
        update.put("verifyFourthRoleId", zeroIfNull(config.getVerifyFourthRoleId()));
        update.put("verifyFifthRoleId", zeroIfNull(config.getVerifyFifthRoleId()));

        Integer verifyTypeId = defaultVerifyType(config.getVerifyTypeId());
        if (verifyTypeId < Constant.VERIFY_LEVEL.ONE_VERIFY) {
            update.put("currentVerifyTypeId", verifyTypeId + 1);
            update.put("auditStatus", Constant.AUDIT_STATUS.PASS);
            iCommonService.saveOneRecord(TABLE_ENTERPRISE_INFO, update);

            JSONObject verifyJson = new JSONObject();
            verifyJson.put("relationId", record.getId());
            verifyJson.put("createUserId", currentUserId);
            verifyJson.put("verifyUserId", Constant.SYSTEM_AUDIT_NOTE.AUTO_PASS);
            verifyJson.put("isAudit", Constant.AUDIT_STATUS.PASS);
            verifyJson.put("reason", Constant.SYSTEM_AUDIT_NOTE.AUTO_PASS);
            verifyJson.put("tableName", TABLE_ENTERPRISE_INFO);
            iCommonService.saveOneRecord(TABLE_VERIFY, verifyJson);
            finalizeApproval(requireEnterpriseInfo(record.getId()), currentUserId);
            return requireEnterpriseInfo(record.getId());
        }

        Integer verifyRoleId = iVerifyProcessService.getVerifyRoleIdByLevel(normalizeVerifyConfigJson(config),
                Constant.VERIFY_LEVEL.ONE_VERIFY);
        record = ensureEnterpriseRecordCooperatingSchool(record);
        String verifyUserId = iVerifyProcessService.GetVerifyUserId(verifyRoleId, currentUserId, null,
                record.getSchoolId());
        if (verifyUserId == null || verifyUserId.isBlank()) {
            throw BaseResponse.moreInfoError.error("enterprise verify user is not configured");
        }
        update.put("currentVerifyTypeId", Constant.VERIFY_LEVEL.ONE_VERIFY);
        iCommonService.saveOneRecord(TABLE_ENTERPRISE_INFO, update);

        JSONObject verifyJson = new JSONObject();
        verifyJson.put("relationId", record.getId());
        verifyJson.put("createUserId", currentUserId);
        verifyJson.put("verifyUserId", verifyUserId);
        verifyJson.put("isAudit", Constant.AUDIT_STATUS.SUBMIT);
        verifyJson.put("reason", "");
        verifyJson.put("tableName", TABLE_ENTERPRISE_INFO);
        iCommonService.saveOneRecord(TABLE_VERIFY, verifyJson);
        return requireEnterpriseInfo(record.getId());
    }

    private MainEnterpriseInfo doResubmit(MainEnterpriseInfo record, JSONObject node, Integer currentUserId, CompanyContext context) {
        if (!Objects.equals(record.getAuditStatus(), Constant.AUDIT_STATUS.BACK)) {
            throw BaseResponse.parameterInvalid.error("only returned record can be resubmitted");
        }

        Integer currentLevel = record.getCurrentVerifyTypeId();
        if (currentLevel == null || currentLevel < Constant.VERIFY_LEVEL.ONE_VERIFY) {
            currentLevel = Constant.VERIFY_LEVEL.ONE_VERIFY;
        }
        record = ensureEnterpriseRecordCooperatingSchool(record);
        Integer verifyRoleId = iVerifyProcessService.getVerifyRoleIdByLevel(FastJsonUtil.toJson(record), currentLevel);
        String verifyUserId = iVerifyProcessService.GetVerifyUserId(verifyRoleId, currentUserId, null,
                record.getSchoolId());
        if (verifyUserId == null || verifyUserId.isBlank()) {
            throw BaseResponse.moreInfoError.error("enterprise verify user is not configured");
        }

        JSONObject entityUpdate = new JSONObject();
        entityUpdate.put("id", record.getId());
        entityUpdate.put("auditStatus", Constant.AUDIT_STATUS.SUBMIT);
        applyEditableFields(entityUpdate, node, context.company, false);
        iCommonService.saveOneRecord(TABLE_ENTERPRISE_INFO, entityUpdate);
        record = requireEnterpriseInfo(record.getId());
        validateSubmitRecord(record);

        MainVerifyProcess saveRecord = latestVerifyRecord(record.getId(),
                Collections.singletonList(Constant.AUDIT_STATUS.SAVE));
        if (saveRecord != null) {
            JSONObject verifyUpdate = new JSONObject();
            verifyUpdate.put("id", saveRecord.getId());
            verifyUpdate.put("verifyUserId", verifyUserId);
            verifyUpdate.put("isAudit", Constant.AUDIT_STATUS.SUBMIT);
            verifyUpdate.put("reason", "");
            iCommonService.saveOneRecord(TABLE_VERIFY, verifyUpdate);
        } else {
            JSONObject verifyJson = new JSONObject();
            verifyJson.put("relationId", record.getId());
            verifyJson.put("createUserId", currentUserId);
            verifyJson.put("verifyUserId", verifyUserId);
            verifyJson.put("isAudit", Constant.AUDIT_STATUS.SUBMIT);
            verifyJson.put("reason", "");
            verifyJson.put("tableName", TABLE_ENTERPRISE_INFO);
            iCommonService.saveOneRecord(TABLE_VERIFY, verifyJson);
        }
        return requireEnterpriseInfo(record.getId());
    }

    private void handleReturnedRecord(MainEnterpriseInfo record, MainVerifyProcess verifyProcess) {
        Integer currentLevel = record.getCurrentVerifyTypeId();
        if (currentLevel == null) {
            currentLevel = Constant.VERIFY_LEVEL.ONE_VERIFY;
        }
        if (currentLevel > Constant.VERIFY_LEVEL.ONE_VERIFY) {
            currentLevel -= 1;
        }

        JSONObject entityUpdate = new JSONObject();
        entityUpdate.put("id", record.getId());
        entityUpdate.put("auditStatus", Constant.AUDIT_STATUS.BACK);
        entityUpdate.put("currentVerifyTypeId", currentLevel);
        if (Boolean.TRUE.equals(record.getIsCurrent())
                && Objects.equals(record.getAuditStatus(), Constant.AUDIT_STATUS.PASS)) {
            entityUpdate.put("isCurrent", false);
            entityUpdate.put("approvedBy", null);
            entityUpdate.put("approvedTime", null);
        }
        iCommonService.saveOneRecord(TABLE_ENTERPRISE_INFO, entityUpdate);

        record = requireEnterpriseInfo(record.getId());
        record = ensureEnterpriseRecordCooperatingSchool(record);
        Integer verifyRoleId = iVerifyProcessService.getVerifyRoleIdByLevel(FastJsonUtil.toJson(record), currentLevel);
        String verifyUserId = iVerifyProcessService.GetVerifyUserId(verifyRoleId, verifyProcess.getCreateUserId(), null,
                record.getSchoolId());
        JSONObject pending = new JSONObject();
        pending.put("relationId", record.getId());
        pending.put("createUserId", verifyProcess.getCreateUserId());
        pending.put("verifyUserId", verifyUserId);
        pending.put("isAudit", Constant.AUDIT_STATUS.SAVE);
        pending.put("reason", "");
        pending.put("tableName", TABLE_ENTERPRISE_INFO);
        iCommonService.saveOneRecord(TABLE_VERIFY, pending);
        syncIsCurrentForCompany(record.getCompanyId());
    }

    private void finalizeApproval(MainEnterpriseInfo record, Integer auditUserId) {
        if (!isVerifyFinished(record)) {
            return;
        }
        assertUniqueCompanyCode(record.getCode(), record.getCompanyId());

        JSONObject entityUpdate = new JSONObject();
        entityUpdate.put("id", record.getId());
        entityUpdate.put("auditStatus", Constant.AUDIT_STATUS.PASS);
        entityUpdate.put("isCurrent", true);
        entityUpdate.put("approvedBy", auditUserId);
        entityUpdate.put("approvedTime", new Date());
        iCommonService.saveOneRecord(TABLE_ENTERPRISE_INFO, entityUpdate);

        BaseDepartment company = tblDepartmentInfoDao.getByIdAndIsDeletedFalse(record.getCompanyId());
        if (company == null) {
            throw BaseResponse.moreInfoError.error("company department does not exist");
        }
        JSONObject companyUpdate = new JSONObject();
        companyUpdate.put("id", company.getId());
        companyUpdate.put("code", normalizeOptionalText(record.getCode()));
        companyUpdate.put("name", normalizeOptionalText(record.getName()));
        companyUpdate.put("departmentPhone", normalizeOptionalText(record.getContactPhone()));
        companyUpdate.put("departmentEmail", normalizeOptionalText(record.getContactEmail()));
        companyUpdate.put("departmentAdd", normalizeOptionalText(record.getAddress()));
        iCommonService.saveOneRecord("BaseDepartment", companyUpdate);

        syncIsCurrentForCompany(record.getCompanyId());
    }

    private JSONObject buildDetailPayload(MainEnterpriseInfo record) {
        JSONObject result = buildRecordSummary(record, true, null);
        result.put("attachments", listAttachments(record.getId()));
        result.put("verifyProcesses", listVerifyProcessJson(record.getId()));
        result.put("currentRoleName", resolveCurrentRoleName(record));
        result.put("canEdit", EDITABLE_STATUSES.contains(record.getAuditStatus())
                || Objects.equals(record.getAuditStatus(), Constant.AUDIT_STATUS.NOTPASS));
        return result;
    }

    private JSONObject buildRecordSummary(MainEnterpriseInfo record, boolean includeAuditTrail) {
        return buildRecordSummary(record, includeAuditTrail, null);
    }

    private JSONObject buildRecordSummary(MainEnterpriseInfo record, boolean includeAuditTrail,
            Map<Integer, Integer> effectiveIdByCompanyIdCache) {
        return buildRecordSummary(record, includeAuditTrail, effectiveIdByCompanyIdCache, null, null);
    }

    /**
     * cached 路径：列表场景下由调用方批量预取 verifyProcesses / attachmentCount，避免每条记录都触发独立查询。
     * 传 null 则走原有的逐条查询路径。
     */
    private JSONObject buildRecordSummary(MainEnterpriseInfo record, boolean includeAuditTrail,
            Map<Integer, Integer> effectiveIdByCompanyIdCache,
            List<MainVerifyProcess> cachedVerifyProcesses,
            Long cachedAttachmentCount) {
        JSONObject row = FastJsonUtil.toJson(record);
        enrichEnterpriseSubmitter(row, record, cachedVerifyProcesses);
        BaseDepartment company = tblDepartmentInfoDao.getByIdAndIsDeletedFalse(record.getCompanyId());
        row.put("companyName", company == null ? record.getName() : company.getName());
        row.put("attachmentsCount", cachedAttachmentCount != null
                ? cachedAttachmentCount.intValue()
                : listAttachments(record.getId()).size());
        row.put("currentRoleName", resolveCurrentRoleName(record));

        Integer effId = effectiveIdByCompanyIdCache != null
                ? effectiveIdByCompanyIdCache.computeIfAbsent(record.getCompanyId(), this::resolveEffectiveApprovedRecordId)
                : resolveEffectiveApprovedRecordId(record.getCompanyId());
        row.put("effectiveCurrent", effId != null && effId.equals(record.getId()));

        if (includeAuditTrail) {
            if (cachedVerifyProcesses != null) {
                putEnterpriseVerifyListTrail(row, cachedVerifyProcesses);
            } else {
                putEnterpriseVerifyListTrail(row, record.getId());
            }
        }
        return row;
    }

    private List<JSONObject> listAttachments(Integer enterpriseInfoId) {
        return sysOssFileDao.findByRelationIdsAndTableNameAndIsDeletedFalse(enterpriseInfoId, TABLE_OSS)
                .stream()
                .sorted(Comparator.comparing(SysOssFile::getId))
                .map(file -> {
                    JSONObject item = FastJsonUtil.toJson(file);
                    item.put("url", "/common/minio/file/" + file.getId());
                    item.put("previewUrl", "/common/minio/preview/" + file.getId());
                    item.put("downloadUrl", "/common/minio/download/" + file.getId());
                    return item;
                })
                .collect(Collectors.toList());
    }

    private List<JSONObject> listVerifyProcessJson(Integer enterpriseInfoId) {
        return mainVerifyProcessDao.findByRelationIdAndTableNameAndIsDeletedFalse(enterpriseInfoId, TABLE_ENTERPRISE_INFO)
                .stream()
                .sorted(Comparator.comparing(MainVerifyProcess::getId))
                .map(process -> {
                    JSONObject item = FastJsonUtil.toJson(process);
                    item.put("createUserName", tblUserInfoDao.findNameById(process.getCreateUserId()));
                    item.put("verifyUserName", resolveVerifyUserNames(process.getVerifyUserId()));
                    return item;
                })
                .collect(Collectors.toList());
    }

    private String resolveCurrentRoleName(MainEnterpriseInfo record) {
        Integer roleId = iVerifyProcessService.getVerifyRoleIdByLevel(FastJsonUtil.toJson(record), record.getCurrentVerifyTypeId());
        if (roleId == null || roleId == 0) {
            return null;
        }
        SysRole role = tblRoleInfoDao.getByIdAndIsDeletedFalse(roleId);
        return role == null ? null : role.getName();
    }

    private String resolveVerifyUserNames(String verifyUserId) {
        if (verifyUserId == null || verifyUserId.isBlank()) {
            return "";
        }
        String[] pieces = verifyUserId.split("\\|");
        List<String> names = new ArrayList<>();
        for (String piece : pieces) {
            String trimmed = piece.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                names.add(tblUserInfoDao.findNameById(Integer.parseInt(trimmed)));
            } catch (NumberFormatException ignored) {
                names.add(trimmed);
            }
        }
        return names.stream().filter(Objects::nonNull).collect(Collectors.joining(","));
    }

    private List<JSONObject> filterMyHistory(List<JSONObject> rows, JSONObject searchKeys) {
        if (searchKeys == null || searchKeys.isEmpty()) {
            return rows;
        }
        String keyword = normalizeOptionalText(searchKeys.getString("keyword"));
        Integer auditStatus = searchKeys.getInteger("auditStatus");
        Boolean onlyEffectiveCurrent = Boolean.TRUE.equals(searchKeys.getBoolean("onlyEffectiveCurrent"));
        return rows.stream()
                .filter(row -> auditStatus == null || Objects.equals(auditStatus, row.getInteger("auditStatus")))
                .filter(row -> !onlyEffectiveCurrent || Boolean.TRUE.equals(row.getBoolean("effectiveCurrent")))
                .filter(row -> keyword == null || matchesKeyword(row, keyword))
                .collect(Collectors.toList());
    }

    private boolean matchesKeyword(JSONObject row, String keyword) {
        String lower = keyword.toLowerCase(Locale.ROOT);
        return containsIgnoreCase(row.getString("name"), lower)
                || containsIgnoreCase(row.getString("companyName"), lower)
                || containsIgnoreCase(row.getString("code"), lower)
                || containsIgnoreCase(row.getString("contactName"), lower)
                || containsIgnoreCase(row.getString("contactPhone"), lower)
                || containsIgnoreCase(row.getString("createUserName"), lower)
                || containsIgnoreCase(row.getString("reason"), lower)
                || containsIgnoreCase(row.getString("latestVerifyReason"), lower);
    }

    /**
     * 与通用审核列表字段对齐：提交人 id / 姓名（前端「提交人」列绑定 createUserName）。
     */
    private void enrichEnterpriseSubmitter(JSONObject row, MainEnterpriseInfo record) {
        enrichEnterpriseSubmitter(row, record, null);
    }

    private void enrichEnterpriseSubmitter(JSONObject row, MainEnterpriseInfo record,
            List<MainVerifyProcess> cachedVerifyProcesses) {
        Integer submitterId = record.getAdminUserId();
        if (submitterId == null) {
            List<MainVerifyProcess> processes = cachedVerifyProcesses != null
                    ? cachedVerifyProcesses
                    : mainVerifyProcessDao.findByRelationIdAndTableNameAndIsDeletedFalse(
                            record.getId(), TABLE_ENTERPRISE_INFO);
            submitterId = processes.stream()
                    .min(Comparator.comparing(MainVerifyProcess::getId))
                    .map(MainVerifyProcess::getCreateUserId)
                    .orElse(null);
        }
        row.put("createUserId", submitterId);
        row.put("createUserName", submitterId == null ? null : tblUserInfoDao.findNameById(submitterId));
    }

    private boolean containsIgnoreCase(String value, String lowerKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerKeyword);
    }

    private boolean matchesAuditVisibility(Integer currentUserId, MainEnterpriseInfo record, JSONObject row, Boolean onlyMine) {
        if (Boolean.TRUE.equals(onlyMine)) {
            return isCurrentPendingAuditor(currentUserId, row);
        }
        if (isCurrentPendingAuditor(currentUserId, row)) {
            return true;
        }
        return canViewSchoolWide(currentUserId);
    }

    private boolean isCurrentPendingAuditor(Integer currentUserId, JSONObject row) {
        return Objects.equals(row.getInteger("auditStatus"), Constant.AUDIT_STATUS.SUBMIT)
                && containsUserId(row.getString("verifyUserId"), currentUserId);
    }

    private boolean canCurrentUserViewRecord(Integer currentUserId, MainEnterpriseInfo record) {
        if (currentUserId == null) {
            return false;
        }
        Set<String> roleCodes = getRoleCodes(currentUserId);
        if (roleCodes.contains(Constant.USER_JOB_CODE.SUPER_ADMIN)) {
            return true;
        }
        if (Objects.equals(record.getAdminUserId(), currentUserId)) {
            return true;
        }
        Integer departmentId = resolveUserDepartmentId(currentUserId);
        if (roleCodes.contains(Constant.USER_JOB_CODE.COMPANY_ADMIN) && Objects.equals(departmentId, record.getCompanyId())) {
            return true;
        }
        Integer schoolId = resolveUserSchoolId(currentUserId);
        if (schoolId != null && schoolId.equals(record.getSchoolId())
                && (roleCodes.contains(Constant.USER_JOB_CODE.SCHOOL_ADMIN)
                || roleCodes.contains(Constant.USER_JOB_CODE.ACADEMIC_AFFAIRS_ADMIN))) {
            return true;
        }
        return containsUserId(allVerifyUsers(record.getId()), currentUserId);
    }

    private boolean canCurrentUserAuditRecord(Integer currentUserId, MainEnterpriseInfo record) {
        if (currentUserId == null) {
            return false;
        }
        if (containsUserId(currentPendingVerifyUsers(record.getId()), currentUserId)) {
            return true;
        }
        Set<String> roleCodes = getRoleCodes(currentUserId);
        if (roleCodes.contains(Constant.USER_JOB_CODE.SUPER_ADMIN)) {
            return true;
        }
        Integer schoolId = resolveUserSchoolId(currentUserId);
        return schoolId != null && schoolId.equals(record.getSchoolId())
                && (roleCodes.contains(Constant.USER_JOB_CODE.SCHOOL_ADMIN)
                || roleCodes.contains(Constant.USER_JOB_CODE.ACADEMIC_AFFAIRS_ADMIN));
    }

    private String currentPendingVerifyUsers(Integer enterpriseInfoId) {
        MainVerifyProcess latest = latestVerifyRecord(enterpriseInfoId,
                Collections.singletonList(Constant.AUDIT_STATUS.SUBMIT));
        return latest == null ? null : latest.getVerifyUserId();
    }

    private String allVerifyUsers(Integer enterpriseInfoId) {
        LinkedHashSet<String> userIds = new LinkedHashSet<>();
        for (MainVerifyProcess process : mainVerifyProcessDao.findByRelationIdAndTableNameAndIsDeletedFalse(
                enterpriseInfoId, TABLE_ENTERPRISE_INFO)) {
            if (process.getVerifyUserId() == null || process.getVerifyUserId().isBlank()) {
                continue;
            }
            userIds.addAll(Arrays.asList(process.getVerifyUserId().split("\\|")));
        }
        return String.join("|", userIds);
    }

    private MainVerifyProcess latestVerifyRecord(Integer enterpriseInfoId, Collection<Integer> auditStatuses) {
        return mainVerifyProcessDao.findByRelationIdAndTableNameAndIsDeletedFalse(enterpriseInfoId, TABLE_ENTERPRISE_INFO)
                .stream()
                .filter(process -> auditStatuses == null || auditStatuses.contains(process.getIsAudit()))
                .max(Comparator.comparing(MainVerifyProcess::getId))
                .orElse(null);
    }

    /**
     * 审核意见/待审 id：不能仅用 max(审核行 id)，否则多级流程在通过后插入的下级「待审」占位行会盖住上一级已填的 reason。
     */
    private void putEnterpriseVerifyListTrail(JSONObject row, Integer enterpriseInfoId) {
        putEnterpriseVerifyListTrail(row, mainVerifyProcessDao.findByRelationIdAndTableNameAndIsDeletedFalse(
                enterpriseInfoId, TABLE_ENTERPRISE_INFO));
    }

    private void putEnterpriseVerifyListTrail(JSONObject row, List<MainVerifyProcess> all) {
        if (all == null || all.isEmpty()) {
            return;
        }
        Comparator<MainVerifyProcess> byId = Comparator.comparing(MainVerifyProcess::getId);
        Optional<MainVerifyProcess> pending = all.stream()
                .filter(p -> Objects.equals(p.getIsAudit(), Constant.AUDIT_STATUS.SUBMIT)
                        || Objects.equals(p.getIsAudit(), Constant.AUDIT_STATUS.SAVE))
                .max(byId);
        MainVerifyProcess opinionRow = all.stream()
                .filter(p -> p.getReason() != null && !p.getReason().isBlank())
                .max(byId)
                .orElseGet(() -> all.stream()
                        .filter(p -> Objects.equals(p.getIsAudit(), Constant.AUDIT_STATUS.PASS)
                                || Objects.equals(p.getIsAudit(), Constant.AUDIT_STATUS.NOTPASS)
                                || Objects.equals(p.getIsAudit(), Constant.AUDIT_STATUS.BACK))
                        .max(byId)
                        .orElseGet(() -> all.stream().max(byId).orElse(null)));
        if (opinionRow == null) {
            return;
        }
        row.put("latestVerifyStatus", opinionRow.getIsAudit());
        String opinionReason = opinionRow.getReason();
        row.put("latestVerifyReason", opinionReason);
        // 与详情页一致：审核意见走 reason；remarks 仅为申报方备注，二者互不覆盖
        row.put("reason", opinionReason);
        MainVerifyProcess whoRow = pending.orElse(opinionRow);
        row.put("verifyUserId", whoRow.getVerifyUserId());
        row.put("verifyUserName", resolveVerifyUserNames(whoRow.getVerifyUserId()));
        row.put("verifyProcessId", pending.map(MainVerifyProcess::getId).orElseGet(opinionRow::getId));
    }

    /**
     * 审核意见字段：规范字段名为 {@code reason}，历史上前端混用过 remarks / auditRemark 等别名。
     * 现阶段仍兼容这些别名以避免破坏旧前端，但命中任一别名时打 WARN 日志方便发现并整改；
     * 后续整改完成可直接保留 {@code REASON_FIELD_CANONICAL} 单一逻辑。
     */
    private static final String REASON_FIELD_CANONICAL = "reason";
    private static final String[] REASON_FIELD_DEPRECATED_ALIASES = {
            "remarks", "auditRemark", "auditReason", "auditOpinion",
            "auditReasonText", "auditDescription", "handleOpinion", "comment"
    };

    private String auditReasonFromNode(JSONObject node) {
        if (node == null) {
            return null;
        }
        String fromRoot = auditReasonFromNodeFlat(node);
        if (fromRoot != null) {
            return fromRoot;
        }
        JSONObject inner = node.getJSONObject("node");
        if (inner != null && inner != node) {
            return auditReasonFromNodeFlat(inner);
        }
        return null;
    }

    private String auditReasonFromNodeFlat(JSONObject node) {
        if (node == null) {
            return null;
        }
        String canonical = normalizeOptionalText(node.getString(REASON_FIELD_CANONICAL));
        if (canonical != null) {
            return canonical;
        }
        for (String alias : REASON_FIELD_DEPRECATED_ALIASES) {
            String s = normalizeOptionalText(node.getString(alias));
            if (s != null) {
                JSONObject warn = new JSONObject();
                warn.put("deprecatedField", alias);
                warn.put("canonical", REASON_FIELD_CANONICAL);
                warn.put("message", "deprecated audit reason alias; please switch front-end to use 'reason'");
                LogUtil.loggerRecord("EnterpriseInfo.audit.deprecatedReasonAlias", warn);
                return s;
            }
        }
        return null;
    }

    private BaseEnterpriseVerifyConfig requireVerifyConfig() {
        BaseEnterpriseVerifyConfig config = baseEnterpriseVerifyConfigDao.findTopByIsDeletedFalseOrderByIdDesc();
        if (config == null) {
            throw BaseResponse.moreInfoError.error("enterprise verify config is not initialized");
        }
        return config;
    }

    private BaseEnterpriseVerifyConfig loadOrInitVerifyConfig(Integer currentUserId) {
        BaseEnterpriseVerifyConfig config = baseEnterpriseVerifyConfigDao.findTopByIsDeletedFalseOrderByIdDesc();
        if (config != null) {
            return config;
        }
        BaseEnterpriseVerifyConfig init = new BaseEnterpriseVerifyConfig();
        init.setSchoolId(resolveDefaultSchoolId(currentUserId));
        init.setVerifyTypeId(Constant.VERIFY_LEVEL.NO_VERIFY);
        init.setVerifyFirstRoleId(0);
        init.setVerifySecondRoleId(0);
        init.setVerifyThirdRoleId(0);
        init.setVerifyFourthRoleId(0);
        init.setVerifyFifthRoleId(0);
        init.setRemarks("");
        return init;
    }

    private Integer resolveGlobalConfigSchoolId(BaseEnterpriseVerifyConfig config, Integer requestedSchoolId, Integer currentUserId) {
        if (config != null && config.getId() != null && config.getSchoolId() != null && config.getSchoolId() > 0) {
            return config.getSchoolId();
        }
        Integer schoolId = resolveManageableSchoolId(requestedSchoolId, currentUserId);
        if (schoolId == null || schoolId <= 0) {
            throw BaseResponse.parameterInvalid.error("schoolId cannot be resolved");
        }
        return schoolId;
    }

    private JSONObject normalizeVerifyConfigJson(BaseEnterpriseVerifyConfig config) {
        JSONObject json = FastJsonUtil.toJson(config);
        if (!json.containsKey("verifyTypeId") || json.getInteger("verifyTypeId") == null) {
            json.put("verifyTypeId", Constant.VERIFY_LEVEL.NO_VERIFY);
        }
        return json;
    }

    private void validateVerifyConfig(Integer verifyTypeId, JSONObject node) {
        if (verifyTypeId < Constant.VERIFY_LEVEL.NO_VERIFY || verifyTypeId > Constant.VERIFY_LEVEL.FIVE_VERIFYS) {
            throw BaseResponse.parameterInvalid.error("verifyTypeId is out of range");
        }
        if (verifyTypeId >= Constant.VERIFY_LEVEL.ONE_VERIFY && zeroIfNull(node.getInteger("verifyFirstRoleId")) <= 0) {
            throw BaseResponse.parameterInvalid.error("first verify role cannot be empty");
        }
        if (verifyTypeId >= Constant.VERIFY_LEVEL.TWO_VERIFYS && zeroIfNull(node.getInteger("verifySecondRoleId")) <= 0) {
            throw BaseResponse.parameterInvalid.error("second verify role cannot be empty");
        }
        if (verifyTypeId >= Constant.VERIFY_LEVEL.THREE_VERIFYS && zeroIfNull(node.getInteger("verifyThirdRoleId")) <= 0) {
            throw BaseResponse.parameterInvalid.error("third verify role cannot be empty");
        }
        if (verifyTypeId >= Constant.VERIFY_LEVEL.FOUR_VERIFYS && zeroIfNull(node.getInteger("verifyFourthRoleId")) <= 0) {
            throw BaseResponse.parameterInvalid.error("fourth verify role cannot be empty");
        }
        if (verifyTypeId >= Constant.VERIFY_LEVEL.FIVE_VERIFYS && zeroIfNull(node.getInteger("verifyFifthRoleId")) <= 0) {
            throw BaseResponse.parameterInvalid.error("fifth verify role cannot be empty");
        }
    }

    private Integer resolveManageableSchoolId(Integer requestedSchoolId, Integer currentUserId) {
        Set<String> roleCodes = getRoleCodes(currentUserId);
        if (roleCodes.contains(Constant.USER_JOB_CODE.SUPER_ADMIN)) {
            Integer resolved = requestedSchoolId != null && requestedSchoolId > 0
                    ? requestedSchoolId : resolveDefaultSchoolId(currentUserId);
            if (resolved == null || resolved <= 0) {
                throw BaseResponse.parameterInvalid.error("schoolId cannot be resolved");
            }
            return resolved;
        }
        if (!roleCodes.contains(Constant.USER_JOB_CODE.SCHOOL_ADMIN)
                && !roleCodes.contains(Constant.USER_JOB_CODE.ACADEMIC_AFFAIRS_ADMIN)) {
            throw BaseResponse.lackPermissions.error("no permission to manage enterprise verify config");
        }
        Integer currentSchoolId = resolveUserSchoolId(currentUserId);
        if (currentSchoolId == null || currentSchoolId <= 0) {
            throw BaseResponse.moreInfoError.error("current schoolId cannot be resolved");
        }
        return currentSchoolId;
    }

    private void ensureCanManageVerifyConfig(Integer currentUserId) {
        Set<String> roleCodes = getRoleCodes(currentUserId);
        if (roleCodes.contains(Constant.USER_JOB_CODE.SUPER_ADMIN)
                || roleCodes.contains(Constant.USER_JOB_CODE.SCHOOL_ADMIN)
                || roleCodes.contains(Constant.USER_JOB_CODE.ACADEMIC_AFFAIRS_ADMIN)) {
            return;
        }
        throw BaseResponse.lackPermissions.error("no permission to manage enterprise verify config");
    }

    private Integer resolveDefaultSchoolId(Integer currentUserId) {
        Integer currentSchoolId = resolveUserSchoolId(currentUserId);
        if (currentSchoolId != null && currentSchoolId > 0) {
            return currentSchoolId;
        }
        List<BaseEnterpriseVerifyConfig> configs = baseEnterpriseVerifyConfigDao.findByIsDeletedFalse();
        if (configs != null && !configs.isEmpty()) {
            Integer schoolId = configs.get(0).getSchoolId();
            if (schoolId != null && schoolId > 0) {
                return schoolId;
            }
        }
        Integer departmentId = resolveUserDepartmentId(currentUserId);
        if (departmentId != null && departmentId > 0) {
            return findSchoolRootId(departmentId);
        }
        return null;
    }

    private Integer getAuditSchoolScope(Integer currentUserId) {
        if (canViewSchoolWide(currentUserId)) {
            return resolveUserSchoolId(currentUserId);
        }
        return resolveUserSchoolId(currentUserId);
    }

    private boolean canViewSchoolWide(Integer currentUserId) {
        Set<String> roleCodes = getRoleCodes(currentUserId);
        return roleCodes.contains(Constant.USER_JOB_CODE.SUPER_ADMIN)
                || roleCodes.contains(Constant.USER_JOB_CODE.SCHOOL_ADMIN)
                || roleCodes.contains(Constant.USER_JOB_CODE.ACADEMIC_AFFAIRS_ADMIN);
    }

    private CompanyContext requireCompanyContext(Integer currentUserId) {
        Set<String> roleCodes = getRoleCodes(currentUserId);
        if (!roleCodes.contains(Constant.USER_JOB_CODE.COMPANY_ADMIN)
                && !roleCodes.contains(Constant.USER_JOB_CODE.SUPER_ADMIN)) {
            throw BaseResponse.lackPermissions.error("only company admin can maintain enterprise info");
        }
        BaseUser user = tblUserInfoDao.getByIdAndIsDeletedFalse(currentUserId);
        if (user == null || user.getDepartmentId() == null || user.getDepartmentId() <= 0) {
            throw BaseResponse.moreInfoError.error("current company department cannot be resolved");
        }
        BaseDepartment company = tblDepartmentInfoDao.getByIdAndIsDeletedFalse(user.getDepartmentId());
        if (company == null) {
            throw BaseResponse.moreInfoError.error("company department does not exist");
        }
        Integer schoolId = resolveCooperatingSchoolIdForEnterprise(null);
        if (schoolId == null || schoolId <= 0) {
            schoolId = findSchoolRootId(company.getId());
        }
        return new CompanyContext(company.getId(), schoolId, user, company);
    }

    /**
     * 企业信息业务上的「合作高校」根部门 id：优先 {@link BaseEnterpriseVerifyConfig} 中学校（与「企业信息审核配置」一致），
     * 否则使用 fallback（如主档已存 schoolId），避免将外埠企业部门树树根误当作高校。
     */
    private Integer resolveCooperatingSchoolIdForEnterprise(Integer fallbackSchoolId) {
        BaseEnterpriseVerifyConfig cfg = baseEnterpriseVerifyConfigDao.findTopByIsDeletedFalseOrderByIdDesc();
        if (cfg != null && cfg.getSchoolId() != null && cfg.getSchoolId() > 0) {
            return cfg.getSchoolId();
        }
        if (fallbackSchoolId != null && fallbackSchoolId > 0) {
            return fallbackSchoolId;
        }
        return null;
    }

    /**
     * 仅在 record.schoolId 缺失（null 或 0）时按合作高校回填一次，写入即冻结，不再随当前
     * 合作关系变化静默回写历史记录的 schoolId（避免审核轨迹错乱）。
     */
    private MainEnterpriseInfo ensureEnterpriseRecordCooperatingSchool(MainEnterpriseInfo record) {
        if (record == null) {
            return null;
        }
        if (record.getSchoolId() != null && record.getSchoolId() > 0) {
            return record;
        }
        Integer coop = resolveCooperatingSchoolIdForEnterprise(null);
        if (coop == null || coop <= 0) {
            return record;
        }
        JSONObject patch = new JSONObject();
        patch.put("id", record.getId());
        patch.put("schoolId", coop);
        iCommonService.saveOneRecord(TABLE_ENTERPRISE_INFO, patch);
        return requireEnterpriseInfo(record.getId());
    }

    private MainEnterpriseInfo requireEnterpriseInfo(Integer enterpriseInfoId) {
        MainEnterpriseInfo record = mainEnterpriseInfoDao.getByIdAndIsDeletedFalse(enterpriseInfoId);
        if (record == null) {
            throw BaseResponse.parameterInvalid.error("enterprise info record does not exist");
        }
        return record;
    }

    private MainEnterpriseInfo requireOwnedRecord(Integer enterpriseInfoId, Integer companyId) {
        MainEnterpriseInfo record = requireEnterpriseInfo(enterpriseInfoId);
        if (!Objects.equals(record.getCompanyId(), companyId)) {
            throw BaseResponse.lackPermissions.error("enterprise info does not belong to current company");
        }
        return record;
    }

    private MainVerifyProcess requireVerifyProcess(Integer verifyProcessId) {
        MainVerifyProcess process = (MainVerifyProcess) iCommonService.getOneRecordById(TABLE_VERIFY, verifyProcessId);
        if (process == null) {
            throw BaseResponse.parameterInvalid.error("verify process does not exist");
        }
        return process;
    }

    /**
     * 同一家企业对外「有效」的已通过版本：与 isCurrent 在终审写入时一致；若无 isCurrent（如新版退回后），
     * 则在仍为 PASS 的记录中取 approvedTime 最新的一条；无 approvedTime 的旧数据再按 versionNo、id 次序比较。
     */
    private MainEnterpriseInfo resolveEffectiveApprovedRecord(Integer companyId) {
        if (companyId == null) {
            return null;
        }
        MainEnterpriseInfo byFlag = mainEnterpriseInfoDao
                .findFirstByCompanyIdAndIsCurrentTrueAndIsDeletedFalseOrderByVersionNoDescIdDesc(companyId);
        if (byFlag != null && Objects.equals(byFlag.getAuditStatus(), Constant.AUDIT_STATUS.PASS)) {
            return byFlag;
        }
        return mainEnterpriseInfoDao.findFirstByCompanyIdAndAuditStatusAndIsDeletedFalseOrderByApprovedTimeDescVersionNoDescIdDesc(
                companyId, Constant.AUDIT_STATUS.PASS);
    }

    private Integer resolveEffectiveApprovedRecordId(Integer companyId) {
        MainEnterpriseInfo e = resolveEffectiveApprovedRecord(companyId);
        return e == null ? null : e.getId();
    }

    /**
     * 按 resolveEffectiveApprovedRecord 的判定结果，将同企业各主档 isCurrent 与之一致（无 PASS 则全部 false）。
     */
    private void syncIsCurrentForCompany(Integer companyId) {
        if (companyId == null) {
            return;
        }
        MainEnterpriseInfo effective = resolveEffectiveApprovedRecord(companyId);
        Integer effectiveId = effective != null ? effective.getId() : null;
        for (MainEnterpriseInfo item : mainEnterpriseInfoDao.findByCompanyIdAndIsDeletedFalseOrderByVersionNoDescIdDesc(companyId)) {
            boolean want = effectiveId != null && effectiveId.equals(item.getId());
            boolean now = Boolean.TRUE.equals(item.getIsCurrent());
            if (want == now) {
                continue;
            }
            JSONObject u = new JSONObject();
            u.put("id", item.getId());
            u.put("isCurrent", want);
            iCommonService.saveOneRecord(TABLE_ENTERPRISE_INFO, u);
        }
    }

    private MainEnterpriseInfo latestRecordForCreate(Integer companyId) {
        return mainEnterpriseInfoDao.findByCompanyIdAndIsDeletedFalseOrderByVersionNoDescIdDesc(companyId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private MainEnterpriseInfo createNewDraft(CompanyContext context, JSONObject node, MainEnterpriseInfo source) {
        JSONObject create = new JSONObject();
        create.put("companyId", context.companyId);
        create.put("schoolId", context.schoolId);
        create.put("adminUserId", context.user.getId());
        create.put("auditStatus", Constant.AUDIT_STATUS.SAVE);
        create.put("currentVerifyTypeId", Constant.VERIFY_LEVEL.NO_VERIFY);
        create.put("isCurrent", false);
        create.put("versionNo", nextVersionNo(context.companyId));
        applySourceFields(create, source, context.company);
        applyEditableFields(create, node, context.company, true);
        Object saved = iCommonService.saveOneRecord(TABLE_ENTERPRISE_INFO, create);
        return (MainEnterpriseInfo) saved;
    }

    private void applySourceFields(JSONObject target, MainEnterpriseInfo source, BaseDepartment company) {
        if (source == null) {
            target.put("code", normalizeOptionalText(company.getCode()));
            target.put("name", normalizeOptionalText(company.getName()));
            target.put("contactPhone", normalizeOptionalText(company.getDepartmentPhone()));
            target.put("contactEmail", normalizeOptionalText(company.getDepartmentEmail()));
            target.put("address", normalizeOptionalText(company.getDepartmentAdd()));
            return;
        }
        target.put("code", source.getCode());
        target.put("name", source.getName());
        target.put("contactName", source.getContactName());
        target.put("contactPhone", source.getContactPhone());
        target.put("contactEmail", source.getContactEmail());
        target.put("address", source.getAddress());
        target.put("legalPerson", source.getLegalPerson());
        target.put("industry", source.getIndustry());
        target.put("companyScale", source.getCompanyScale());
        target.put("businessScope", source.getBusinessScope());
        target.put("introduction", source.getIntroduction());
        target.put("remarks", source.getRemarks());
    }

    private void applyEditableFields(JSONObject target, JSONObject node, BaseDepartment company, boolean fillDefaults) {
        putIfPresent(target, "code", firstString(node, "code", "companyCode"),
                fillDefaults ? company == null ? null : company.getCode() : null);
        putIfPresent(target, "name", firstString(node, "name", "companyName"),
                fillDefaults ? company == null ? null : company.getName() : null);
        putIfPresent(target, "contactName", normalizeOptionalText(node.getString("contactName")), null);
        putIfPresent(target, "contactPhone", firstString(node, "contactPhone", "phone"),
                fillDefaults ? company == null ? null : company.getDepartmentPhone() : null);
        putIfPresent(target, "contactEmail", firstString(node, "contactEmail", "email"),
                fillDefaults ? company == null ? null : company.getDepartmentEmail() : null);
        putIfPresent(target, "address", firstString(node, "address", "companyAddress"),
                fillDefaults ? company == null ? null : company.getDepartmentAdd() : null);
        putIfPresent(target, "legalPerson", normalizeOptionalText(node.getString("legalPerson")), null);
        putIfPresent(target, "industry", normalizeOptionalText(node.getString("industry")), null);
        putIfPresent(target, "companyScale", normalizeOptionalText(node.getString("companyScale")), null);
        putIfPresent(target, "businessScope", normalizeOptionalText(node.getString("businessScope")), null);
        putIfPresent(target, "introduction", normalizeOptionalText(node.getString("introduction")), null);
        if (fillDefaults || node.containsKey("remarks")) {
            putIfPresent(target, "remarks", normalizeOptionalText(node.getString("remarks")), null);
        }
    }

    private void validateSubmitRecord(MainEnterpriseInfo record) {
        if (record == null) {
            throw BaseResponse.parameterInvalid.error("enterprise info record cannot be empty");
        }
        if (normalizeOptionalText(record.getCode()) == null) {
            throw BaseResponse.parameterInvalid.error("enterprise code cannot be empty");
        }
        if (normalizeOptionalText(record.getName()) == null) {
            throw BaseResponse.parameterInvalid.error("enterprise name cannot be empty");
        }
        if (listAttachments(record.getId()).isEmpty()) {
            throw BaseResponse.parameterInvalid.error("at least one attachment is required before submit");
        }
        assertUniqueCompanyCode(record.getCode(), record.getCompanyId());
    }

    private void assertUniqueCompanyCode(String code, Integer companyId) {
        String normalizedCode = normalizeOptionalText(code);
        if (normalizedCode == null) {
            return;
        }
        Object existing = tblDepartmentInfoDao.getByCodeAndIsDeletedFalse(normalizedCode);
        if (existing == null) {
            return;
        }
        BaseDepartment department = (BaseDepartment) existing;
        if (department.getId() != null && !department.getId().equals(companyId)) {
            throw BaseResponse.parameterInvalid.error("enterprise code already exists in another company");
        }
    }

    private boolean isVerifyFinished(MainEnterpriseInfo record) {
        Integer verifyTypeId = defaultVerifyType(record.getVerifyTypeId());
        Integer currentLevel = record.getCurrentVerifyTypeId();
        return currentLevel != null && currentLevel > verifyTypeId;
    }

    private Integer nextVersionNo(Integer companyId) {
        return mainEnterpriseInfoDao.findByCompanyIdAndIsDeletedFalseOrderByVersionNoDescIdDesc(companyId)
                .stream()
                .map(MainEnterpriseInfo::getVersionNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private Integer defaultVerifyType(Integer verifyTypeId) {
        return verifyTypeId == null ? Constant.VERIFY_LEVEL.NO_VERIFY : verifyTypeId;
    }

    private Integer zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }

    private Integer firstInteger(JSONObject node, String... keys) {
        for (String key : keys) {
            Integer value = node.getInteger(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstString(JSONObject node, String key1, String key2) {
        String value = normalizeOptionalText(node.getString(key1));
        if (value != null) {
            return value;
        }
        value = normalizeOptionalText(node.getString(key2));
        return value;
    }

    private void putIfPresent(JSONObject target, String key, String value, String fallback) {
        String finalValue = value != null ? value : normalizeOptionalText(fallback);
        if (finalValue != null) {
            target.put(key, finalValue);
        }
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || "-".equals(trimmed) ? null : trimmed;
    }

    private Integer resolveUserDepartmentId(Integer userId) {
        BaseUser user = tblUserInfoDao.getByIdAndIsDeletedFalse(userId);
        return user == null ? null : user.getDepartmentId();
    }

    private Integer resolveUserSchoolId(Integer userId) {
        Object userObj = iCommonService.getOneRecordById("ViewBaseUser", userId);
        return userObj == null ? null : FastJsonUtil.toJson(userObj).getInteger("schoolId");
    }

    private Integer findSchoolRootId(Integer deptId) {
        int maxDepth = 20;
        Integer current = deptId;
        while (current != null && current > 0 && maxDepth-- > 0) {
            BaseDepartment department = tblDepartmentInfoDao.findById(current).orElse(null);
            if (department == null) {
                return deptId;
            }
            Integer parentId = department.getParentId();
            if (parentId == null || parentId == -1) {
                return current;
            }
            current = parentId;
        }
        return deptId;
    }

    private Set<String> getRoleCodes(Integer userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        Set<String> roleCodes = new HashSet<>();
        for (RelUserRole relation : relUserRoleDao.findByUserIdAndIsDeletedFalse(userId)) {
            SysRole role = tblRoleInfoDao.getByIdAndIsDeletedFalse(relation.getRoleId());
            if (role != null && role.getCode() != null) {
                roleCodes.add(role.getCode());
            }
        }
        return roleCodes;
    }

    private boolean containsUserId(String verifyUserIds, Integer userId) {
        if (verifyUserIds == null || verifyUserIds.isBlank() || userId == null) {
            return false;
        }
        String target = String.valueOf(userId);
        for (String piece : verifyUserIds.split("\\|")) {
            if (target.equals(piece.trim())) {
                return true;
            }
        }
        return false;
    }

    private void sortRows(List<JSONObject> rows, Sort sort) {
        Comparator<JSONObject> comparator = Comparator.comparing(json -> json.getInteger("id"), Comparator.nullsLast(Integer::compareTo));
        if (sort != null && sort.iterator().hasNext()) {
            for (Sort.Order order : sort) {
                comparator = comparatorBy(order.getProperty(), order.isAscending());
                break;
            }
        } else {
            comparator = comparatorBy("id", false);
        }
        rows.sort(comparator);
    }

    private Comparator<JSONObject> comparatorBy(String property, boolean asc) {
        Comparator<JSONObject> comparator;
        switch (property) {
            case "versionNo":
                comparator = Comparator.comparing(json -> json.getInteger("versionNo"), Comparator.nullsLast(Integer::compareTo));
                break;
            case "name":
            case "companyName":
                comparator = Comparator.comparing(json -> nullSafeString(json.getString("name")));
                break;
            case "code":
                comparator = Comparator.comparing(json -> nullSafeString(json.getString("code")));
                break;
            case "auditStatus":
                comparator = Comparator.comparing(json -> json.getInteger("auditStatus"), Comparator.nullsLast(Integer::compareTo));
                break;
            case "updateTime":
                comparator = Comparator.comparing(json -> json.getDate("updateTime"), Comparator.nullsLast(Date::compareTo));
                break;
            default:
                comparator = Comparator.comparing(json -> json.getInteger("id"), Comparator.nullsLast(Integer::compareTo));
                break;
        }
        return asc ? comparator : comparator.reversed();
    }

    private String nullSafeString(String value) {
        return value == null ? "" : value;
    }

    private Object toPage(List<JSONObject> rows, Integer page, Integer size) {
        int resolvedPage = page == null || page < 1 ? Constant.DEFAULT_PAGE : page;
        int resolvedSize = size == null || size < 1 ? Constant.DEFAULT_SIZE : size;
        int from = Math.min((resolvedPage - 1) * resolvedSize, rows.size());
        int to = Math.min(from + resolvedSize, rows.size());
        return new PageImpl<>(rows.subList(from, to), PageRequest.of(resolvedPage - 1, resolvedSize), rows.size());
    }

    private static class CompanyContext {
        private final Integer companyId;
        private final Integer schoolId;
        private final BaseUser user;
        private final BaseDepartment company;

        private CompanyContext(Integer companyId, Integer schoolId, BaseUser user, BaseDepartment company) {
            this.companyId = companyId;
            this.schoolId = schoolId;
            this.user = user;
            this.company = company;
        }
    }
}
