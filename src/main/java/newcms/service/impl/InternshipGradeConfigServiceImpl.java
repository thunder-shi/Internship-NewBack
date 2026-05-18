package newcms.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.base.Constant;
import newcms.entity.db.InternshipGradeConfigItem;
import newcms.entity.db.MainDiary;
import newcms.entity.db.MainInternshipPost;
import newcms.entity.db.MainVerifyProcess;
import newcms.entity.db.RelStuInternshipPost;
import newcms.entity.db.RelTitleStudent;
import newcms.entity.db.ViewRelProcessInternship;
import newcms.repository.db.InternshipGradeConfigItemDao;
import newcms.repository.db.MainDiaryDao;
import newcms.repository.db.MainInternshipPostDao;
import newcms.repository.db.MainVerifyProcessDao;
import newcms.repository.db.RelStuInternshipPostDao;
import newcms.repository.db.RelTitleStudentDao;
import newcms.repository.db.ViewRelProcessInternshipDao;
import newcms.service.ICommonService;
import newcms.service.IInternshipGradeConfigService;
import newcms.utils.FastJsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class InternshipGradeConfigServiceImpl extends Base implements IInternshipGradeConfigService {

    private static final Logger log = LoggerFactory.getLogger(InternshipGradeConfigServiceImpl.class);

    private static final String TABLE_CONFIG = "InternshipGradeConfigItem";
    private static final String TABLE_MAIN_DIARY = "MainDiary";
    private static final String TABLE_REL_STU_INT = "RelStuInternshipPost";
    private static final String TABLE_REL_TITLE_STU = "RelTitleStudent";
    /** "实习项目进行"流程的 BaseProcessType.name；该行 code 字段为空，只能用 name 匹配 */
    private static final String PROCESS_NAME_INTERNSHIP_RUNNING = "实习项目进行";
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final Set<Integer> ONGOING_AUDIT_STATUSES = Set.of(
            Constant.AUDIT_STATUS.SUBMIT, Constant.AUDIT_STATUS.PASS);

    @Resource
    private ICommonService iCommonService;
    @Resource
    private InternshipGradeConfigItemDao gradeConfigDao;
    @Resource
    private MainVerifyProcessDao mainVerifyProcessDao;
    @Resource
    private MainDiaryDao mainDiaryDao;
    @Resource
    private RelStuInternshipPostDao relStuInternshipPostDao;
    @Resource
    private RelTitleStudentDao relTitleStudentDao;
    @Resource
    private MainInternshipPostDao mainInternshipPostDao;
    @Resource
    private ViewRelProcessInternshipDao viewRelProcessInternshipDao;

    // ============================================================
    // 公开接口
    // ============================================================

    @Override
    public JSONObject list(Integer internshipId, String sourceTable) {
        requireKey(internshipId, sourceTable);
        return buildListPayload(internshipId, sourceTable);
    }

    @Override
    public JSONObject save(JSONObject node) {
        if (node == null) {
            throw BaseResponse.parameterInvalid.error("node cannot be empty");
        }
        Integer internshipId = node.getInteger("internshipId");
        String sourceTable = trim(node.getString("sourceTable"));
        Integer levelOrder = node.getInteger("levelOrder");
        BigDecimal weight = node.getBigDecimal("weight");
        BigDecimal maxScore = node.getBigDecimal("maxScore");
        if (maxScore == null) {
            maxScore = HUNDRED;
        }
        requireKey(internshipId, sourceTable);
        assertSourceTableSupported(sourceTable);
        if (levelOrder == null || levelOrder < 1 || levelOrder > 5) {
            throw BaseResponse.parameterInvalid.error("levelOrder must be in [1,5]");
        }
        if (weight == null || weight.compareTo(ZERO) < 0 || weight.compareTo(HUNDRED) > 0) {
            throw BaseResponse.parameterInvalid.error("weight must be in [0,100]");
        }
        if (maxScore.compareTo(ZERO) <= 0) {
            throw BaseResponse.parameterInvalid.error("maxScore must be positive");
        }

        // 守卫 1: NO_VERIFY 业务禁止配置
        Integer verifyTypeId = resolveVerifyTypeIdForSource(internshipId, sourceTable);
        if (verifyTypeId == null) {
            throw BaseResponse.parameterInvalid.error(
                    "该 internship 下尚无 " + sourceTable + " 业务记录或未配置 verifyTypeId，请先生成日志再配评分");
        }
        if (verifyTypeId < Constant.VERIFY_LEVEL.ONE_VERIFY) {
            throw BaseResponse.parameterInvalid.error(
                    "该 internship 下 " + sourceTable + " 配置为无需审核（NO_VERIFY），不能配评分项");
        }
        // 守卫 2: levelOrder 必须 ≤ 实际审核级数。verifyTypeId=2 表示 1 级，3 表示 2 级，依此类推（参考 Constant.VERIFY_LEVEL）
        int maxLevel = verifyTypeId - 1;
        if (levelOrder > maxLevel) {
            throw BaseResponse.parameterInvalid.error(
                    "levelOrder=" + levelOrder + " 超过实际审核级数 " + maxLevel);
        }
        // 守卫 3: 审核进行中禁止改动
        if (hasOngoingAudit(internshipId, sourceTable)) {
            throw BaseResponse.parameterInvalid.error(
                    "该 internship 下 " + sourceTable + " 存在进行中或已通过的审核记录，禁止修改评分配置");
        }

        Integer id = node.getInteger("id");
        // 守卫 4: 同 levelOrder 唯一
        Optional<InternshipGradeConfigItem> existingAtLevel = gradeConfigDao
                .findByInternshipIdAndSourceTableAndLevelOrderAndIsDeletedFalse(internshipId, sourceTable, levelOrder);
        if (existingAtLevel.isPresent() && !Objects.equals(existingAtLevel.get().getId(), id)) {
            throw BaseResponse.parameterInvalid.error(
                    "levelOrder=" + levelOrder + " 已存在评分项，请编辑现有项而非新增");
        }

        // 守卫 5: 权重总和 = 100（按"本次保存生效后"计算）
        BigDecimal sumAfter = sumWeightsAfterChange(internshipId, sourceTable, id, weight);
        if (sumAfter.compareTo(HUNDRED) != 0) {
            throw BaseResponse.parameterInvalid.error(
                    "本次保存后权重总和 = " + sumAfter + "，必须等于 100");
        }

        JSONObject patch = new JSONObject();
        if (id != null) patch.put("id", id);
        patch.put("internshipId", internshipId);
        patch.put("sourceTable", sourceTable);
        patch.put("levelOrder", levelOrder);
        patch.put("weight", weight);
        patch.put("maxScore", maxScore);
        Object saved = iCommonService.saveOneRecord(TABLE_CONFIG, patch);
        return FastJsonUtil.toJson(saved);
    }

    @Override
    public void delete(Integer configItemId) {
        if (configItemId == null) {
            throw BaseResponse.parameterInvalid.error("configItemId cannot be empty");
        }
        InternshipGradeConfigItem item = gradeConfigDao.getByIdAndIsDeletedFalse(configItemId);
        if (item == null) {
            throw BaseResponse.parameterInvalid.error("grade config item does not exist");
        }
        if (hasOngoingAudit(item.getInternshipId(), item.getSourceTable())) {
            throw BaseResponse.parameterInvalid.error(
                    "该 internship 下 " + item.getSourceTable() + " 存在进行中或已通过的审核记录，禁止删除评分配置");
        }
        iCommonService.deleteRecordByDelflag(TABLE_CONFIG, configItemId);
    }

    @Override
    public JSONObject validateWeights(Integer internshipId, String sourceTable) {
        requireKey(internshipId, sourceTable);
        return buildListPayload(internshipId, sourceTable);
    }

    @Override
    public JSONObject saveBatch(JSONObject node) {
        if (node == null) {
            throw BaseResponse.parameterInvalid.error("node cannot be empty");
        }
        Integer internshipId = node.getInteger("internshipId");
        String sourceTable = trim(node.getString("sourceTable"));
        requireKey(internshipId, sourceTable);
        assertSourceTableSupported(sourceTable);

        JSONArray items = node.getJSONArray("items");
        if (items == null || items.isEmpty()) {
            throw BaseResponse.parameterInvalid.error("items cannot be empty");
        }

        // 守卫 1: NO_VERIFY 业务禁止配置 + 校验业务实体已生成
        Integer verifyTypeId = resolveVerifyTypeIdForSource(internshipId, sourceTable);
        if (verifyTypeId == null) {
            throw BaseResponse.parameterInvalid.error(
                    "该 internship 下尚无 " + sourceTable + " 业务记录或未配置 verifyTypeId，请先生成日志再配评分");
        }
        if (verifyTypeId < Constant.VERIFY_LEVEL.ONE_VERIFY) {
            throw BaseResponse.parameterInvalid.error(
                    "该 internship 下 " + sourceTable + " 配置为无需审核（NO_VERIFY），不能配评分项");
        }
        int maxLevel = verifyTypeId - 1;

        // 守卫 2: 审核进行中禁止改动
        if (hasOngoingAudit(internshipId, sourceTable)) {
            throw BaseResponse.parameterInvalid.error(
                    "该 internship 下 " + sourceTable + " 存在进行中或已通过的审核记录，禁止修改评分配置");
        }

        // 守卫 3: 逐项基本校验 + levelOrder 唯一 + SUM = 100
        // 报错消息全部使用 1-based 行号，前端可原样 toast
        Map<Integer, Integer> levelToRow = new HashMap<>(); // level → 首次出现的 1-based 行号
        BigDecimal sum = ZERO;
        List<JSONObject> normalized = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            int row1 = i + 1;
            JSONObject raw = items.getJSONObject(i);
            if (raw == null) {
                throw BaseResponse.parameterInvalid.error("第 " + row1 + " 项为空");
            }
            Integer levelOrder = raw.getInteger("levelOrder");
            BigDecimal weight = raw.getBigDecimal("weight");
            BigDecimal maxScore = raw.getBigDecimal("maxScore");
            if (maxScore == null) maxScore = HUNDRED;

            if (levelOrder == null || levelOrder < 1 || levelOrder > 5) {
                throw BaseResponse.parameterInvalid.error(
                        "第 " + row1 + " 项 levelOrder 必须在 [1,5] 范围内，当前=" + levelOrder);
            }
            if (levelOrder > maxLevel) {
                throw BaseResponse.parameterInvalid.error(
                        "第 " + row1 + " 项 levelOrder=" + levelOrder + " 超过该实习实际审核级数 " + maxLevel);
            }
            Integer firstRow = levelToRow.get(levelOrder);
            if (firstRow != null) {
                throw BaseResponse.parameterInvalid.error(
                        "第 " + row1 + " 项 levelOrder=" + levelOrder + " 与第 " + firstRow + " 项冲突，同一级别只能配一项");
            }
            levelToRow.put(levelOrder, row1);
            if (weight == null || weight.compareTo(ZERO) < 0 || weight.compareTo(HUNDRED) > 0) {
                throw BaseResponse.parameterInvalid.error(
                        "第 " + row1 + " 项 weight 必须在 [0,100] 范围内，当前=" + weight);
            }
            if (maxScore.compareTo(ZERO) <= 0) {
                throw BaseResponse.parameterInvalid.error(
                        "第 " + row1 + " 项 maxScore 必须 > 0，当前=" + maxScore);
            }
            sum = sum.add(weight);

            JSONObject row = new JSONObject();
            row.put("internshipId", internshipId);
            row.put("sourceTable", sourceTable);
            row.put("levelOrder", levelOrder);
            row.put("weight", weight);
            row.put("maxScore", maxScore);
            normalized.add(row);
        }
        if (sum.compareTo(HUNDRED) != 0) {
            StringBuilder breakdown = new StringBuilder();
            for (int i = 0; i < normalized.size(); i++) {
                JSONObject r = normalized.get(i);
                if (i > 0) breakdown.append(", ");
                breakdown.append("第").append(i + 1).append("项 lv")
                        .append(r.getInteger("levelOrder")).append("=").append(r.getBigDecimal("weight"));
            }
            throw BaseResponse.parameterInvalid.error(
                    "权重总和=" + sum + "，必须等于 100。当前各项：" + breakdown);
        }

        // 软删旧的，插入新的（同一事务，校验失败整体回滚）
        List<InternshipGradeConfigItem> oldItems = gradeConfigDao
                .findByInternshipIdAndSourceTableAndIsDeletedFalseOrderByLevelOrderAsc(
                        internshipId, sourceTable);
        for (InternshipGradeConfigItem old : oldItems) {
            iCommonService.deleteRecordByDelflag(TABLE_CONFIG, old.getId());
        }
        for (JSONObject row : normalized) {
            iCommonService.saveOneRecord(TABLE_CONFIG, row);
        }

        return buildListPayload(internshipId, sourceTable);
    }

    @Override
    public boolean hasConfig(Integer internshipId, String sourceTable) {
        if (internshipId == null || sourceTable == null || sourceTable.isBlank()) {
            return false;
        }
        return !gradeConfigDao
                .findByInternshipIdAndSourceTableAndIsDeletedFalseOrderByLevelOrderAsc(
                        internshipId, sourceTable)
                .isEmpty();
    }

    @Override
    public void requireScoreOnPass(Integer internshipId, String sourceTable, Integer levelOrder, BigDecimal score) {
        if (internshipId == null || sourceTable == null || levelOrder == null) {
            return;
        }
        Optional<InternshipGradeConfigItem> opt = gradeConfigDao
                .findByInternshipIdAndSourceTableAndLevelOrderAndIsDeletedFalse(
                        internshipId, sourceTable, levelOrder);
        if (opt.isEmpty()) {
            return;
        }
        InternshipGradeConfigItem cfg = opt.get();
        if (score == null) {
            throw BaseResponse.parameterInvalid.error(
                    "当前级别（第 " + cfg.getLevelOrder() + " 级）已配置评分，PASS 前必须填 score");
        }
        if (score.compareTo(ZERO) < 0 || score.compareTo(cfg.getMaxScore()) > 0) {
            throw BaseResponse.parameterInvalid.error(
                    "score 超出范围 [0, " + cfg.getMaxScore() + "]");
        }
    }

    @Override
    public Integer resolveInternshipIdForDiary(Integer diaryId) {
        if (diaryId == null) return null;
        MainDiary diary = mainDiaryDao.getByIdAndIsDeletedFalse(diaryId);
        if (diary == null || diary.getRelationId() == null || diary.getTableName() == null) {
            return null;
        }
        if (TABLE_REL_STU_INT.equals(diary.getTableName())) {
            RelStuInternshipPost rel = relStuInternshipPostDao.getByIdAndIsDeletedFalse(diary.getRelationId());
            if (rel == null || rel.getInternshipPostId() == null) return null;
            MainInternshipPost post = mainInternshipPostDao.getByIdAndIsDeletedFalse(rel.getInternshipPostId());
            return post == null ? null : post.getInternshipId();
        }
        if (TABLE_REL_TITLE_STU.equals(diary.getTableName())) {
            RelTitleStudent rts = relTitleStudentDao.getByIdAndIsDeletedFalse(diary.getRelationId());
            return rts == null ? null : rts.getInternshipId();
        }
        return null;
    }

    @Override
    public void computeAndPersistTotalScore(Integer relationId, String sourceTable) {
        if (relationId == null || sourceTable == null) return;
        Integer internshipId = resolveInternshipIdForSource(relationId, sourceTable);
        if (internshipId == null) return;
        List<InternshipGradeConfigItem> configs = gradeConfigDao
                .findByInternshipIdAndSourceTableAndIsDeletedFalseOrderByLevelOrderAsc(
                        internshipId, sourceTable);
        if (configs.isEmpty()) return; // 未配置评分，静默跳过

        List<MainVerifyProcess> all = mainVerifyProcessDao
                .findByRelationIdAndTableNameAndIsDeletedFalse(relationId, sourceTable);
        Map<Integer, MainVerifyProcess> latestPassPerLevel = latestPassPerLevel(all);

        BigDecimal total = ZERO;
        JSONArray detail = new JSONArray();
        for (InternshipGradeConfigItem cfg : configs) {
            MainVerifyProcess vp = latestPassPerLevel.get(cfg.getLevelOrder());
            BigDecimal score = vp == null ? null : vp.getScore();
            BigDecimal weighted = ZERO;
            if (score != null && cfg.getMaxScore() != null && cfg.getMaxScore().compareTo(ZERO) > 0) {
                weighted = score.multiply(cfg.getWeight())
                        .divide(cfg.getMaxScore(), 4, RoundingMode.HALF_UP);
            }
            total = total.add(weighted);

            JSONObject row = new JSONObject(true); // 保留插入顺序
            row.put("levelOrder", cfg.getLevelOrder());
            row.put("weight", cfg.getWeight());
            row.put("maxScore", cfg.getMaxScore());
            row.put("score", score);
            row.put("verifyUserId", vp == null ? null : vp.getVerifyUserId());
            row.put("verifyUserName", resolveVerifyUserName(vp == null ? null : vp.getVerifyUserId()));
            detail.add(row);
        }
        total = total.setScale(2, RoundingMode.HALF_UP);

        JSONObject update = new JSONObject();
        update.put("id", relationId);
        update.put("totalScore", total);
        update.put("scoreDetail", detail.toJSONString());
        update.put("totalScoreLockTime", new Date());
        iCommonService.saveOneRecord(sourceTable, update);
    }

    // ============================================================
    // 内部
    // ============================================================

    /**
     * 给 list/validateWeights/saveBatch 复用的统一响应组装：
     * items + maxLevelOrder + locked + totalWeight + valid + expected
     */
    private JSONObject buildListPayload(Integer internshipId, String sourceTable) {
        List<InternshipGradeConfigItem> items = gradeConfigDao
                .findByInternshipIdAndSourceTableAndIsDeletedFalseOrderByLevelOrderAsc(
                        internshipId, sourceTable);
        BigDecimal sum = ZERO;
        JSONArray arr = new JSONArray();
        for (InternshipGradeConfigItem item : items) {
            sum = sum.add(item.getWeight() == null ? ZERO : item.getWeight());
            arr.add(FastJsonUtil.toJson(item));
        }
        Integer maxLevelOrder = null;
        if (TABLE_MAIN_DIARY.equals(sourceTable)) {
            Integer verifyTypeId = resolveVerifyTypeIdForSource(internshipId, sourceTable);
            if (verifyTypeId != null && verifyTypeId >= Constant.VERIFY_LEVEL.ONE_VERIFY) {
                maxLevelOrder = verifyTypeId - 1;
            }
        }
        boolean locked = hasOngoingAudit(internshipId, sourceTable);

        JSONObject result = new JSONObject();
        result.put("internshipId", internshipId);
        result.put("sourceTable", sourceTable);
        result.put("items", arr);
        result.put("maxLevelOrder", maxLevelOrder);
        result.put("locked", locked);
        result.put("totalWeight", sum);
        result.put("expected", HUNDRED);
        result.put("valid", sum.compareTo(HUNDRED) == 0);
        return result;
    }

    private void requireKey(Integer internshipId, String sourceTable) {
        if (internshipId == null) {
            throw BaseResponse.parameterInvalid.error("internshipId cannot be empty");
        }
        if (sourceTable == null || sourceTable.isBlank()) {
            throw BaseResponse.parameterInvalid.error("sourceTable cannot be empty");
        }
    }

    private void assertSourceTableSupported(String sourceTable) {
        if (!TABLE_MAIN_DIARY.equals(sourceTable)) {
            throw BaseResponse.parameterInvalid.error(
                    "sourceTable=" + sourceTable + " 暂未接入评分功能，当前仅支持 MainDiary");
        }
    }

    private String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * 列出某 internshipId 下所有 MainDiary 的 id（含校外、校内两条路径）。
     */
    private List<Integer> listDiaryIdsInInternship(Integer internshipId) {
        if (internshipId == null) return Collections.emptyList();
        Set<Integer> diaryIds = new HashSet<>();

        // 校外路径：internshipId → MainInternshipPost → RelStuInternshipPost → MainDiary
        List<MainInternshipPost> posts = mainInternshipPostDao.findByInternshipIdAndIsDeletedFalse(internshipId);
        if (!posts.isEmpty()) {
            List<Integer> postIds = posts.stream()
                    .map(MainInternshipPost::getId).filter(Objects::nonNull).collect(Collectors.toList());
            if (!postIds.isEmpty()) {
                List<RelStuInternshipPost> rels = relStuInternshipPostDao
                        .findByInternshipPostIdInAndIsDeletedFalse(postIds);
                List<Integer> relIds = rels.stream()
                        .map(RelStuInternshipPost::getId).filter(Objects::nonNull).collect(Collectors.toList());
                if (!relIds.isEmpty()) {
                    List<MainDiary> externalDiaries = mainDiaryDao
                            .findByRelationIdInAndTableNameAndIsDeletedFalse(relIds, TABLE_REL_STU_INT);
                    for (MainDiary d : externalDiaries) diaryIds.add(d.getId());
                }
            }
        }

        // 校内路径：internshipId → RelTitleStudent → MainDiary
        List<RelTitleStudent> rtsList = relTitleStudentDao.findByInternshipIdAndIsDeletedFalse(internshipId);
        if (!rtsList.isEmpty()) {
            List<Integer> rtsIds = rtsList.stream()
                    .map(RelTitleStudent::getId).filter(Objects::nonNull).collect(Collectors.toList());
            if (!rtsIds.isEmpty()) {
                List<MainDiary> internalDiaries = mainDiaryDao
                        .findByRelationIdInAndTableNameAndIsDeletedFalse(rtsIds, TABLE_REL_TITLE_STU);
                for (MainDiary d : internalDiaries) diaryIds.add(d.getId());
            }
        }
        return new ArrayList<>(diaryIds);
    }

    /**
     * 取该 internship 下 sourceTable 业务实体的 verifyTypeId。
     * <p>MainDiary 走"实习项目进行"流程：从 ViewRelProcessInternship 按 processTypeName 匹配，
     * 不依赖日志是否生成。匹配不到说明该实习未配该流程，返回 null。</p>
     */
    private Integer resolveVerifyTypeIdForSource(Integer internshipId, String sourceTable) {
        if (!TABLE_MAIN_DIARY.equals(sourceTable)) return null;
        if (internshipId == null) return null;
        Optional<ViewRelProcessInternship> processOpt = viewRelProcessInternshipDao
                .findByInternshipIdAndProcessTypeNameAndIsDeletedFalse(
                        internshipId, PROCESS_NAME_INTERNSHIP_RUNNING);
        if (processOpt.isEmpty()) {
            log.info("[GradeConfig] no '{}' process for internshipId={}",
                    PROCESS_NAME_INTERNSHIP_RUNNING, internshipId);
            return null;
        }
        Integer verifyTypeId = processOpt.get().getVerifyTypeId();
        log.debug("[GradeConfig] internshipId={} '{}' verifyTypeId={}",
                internshipId, PROCESS_NAME_INTERNSHIP_RUNNING, verifyTypeId);
        return verifyTypeId;
    }

    /**
     * 该 (internshipId, sourceTable) 是否已存在 isAudit ∈ {SUBMIT, PASS} 的审核行。
     */
    private boolean hasOngoingAudit(Integer internshipId, String sourceTable) {
        List<Integer> diaryIds = listDiaryIdsInInternship(internshipId);
        if (diaryIds.isEmpty()) return false;
        List<MainVerifyProcess> processes = mainVerifyProcessDao
                .findByRelationIdInAndTableNameAndIsDeletedFalse(diaryIds, sourceTable);
        for (MainVerifyProcess p : processes) {
            if (p.getIsAudit() != null && ONGOING_AUDIT_STATUSES.contains(p.getIsAudit())) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal sumWeightsAfterChange(Integer internshipId, String sourceTable,
                                             Integer changingId, BigDecimal changingWeight) {
        List<InternshipGradeConfigItem> items = gradeConfigDao
                .findByInternshipIdAndSourceTableAndIsDeletedFalseOrderByLevelOrderAsc(
                        internshipId, sourceTable);
        BigDecimal sum = ZERO;
        boolean foundChanging = false;
        for (InternshipGradeConfigItem item : items) {
            if (Objects.equals(item.getId(), changingId)) {
                sum = sum.add(changingWeight);
                foundChanging = true;
            } else {
                sum = sum.add(item.getWeight() == null ? ZERO : item.getWeight());
            }
        }
        if (!foundChanging) {
            sum = sum.add(changingWeight); // 新增
        }
        return sum;
    }

    private Integer resolveInternshipIdForSource(Integer relationId, String sourceTable) {
        if (TABLE_MAIN_DIARY.equals(sourceTable)) {
            return resolveInternshipIdForDiary(relationId);
        }
        return null;
    }

    /**
     * 按 MainVerifyProcess.id 升序取所有 PASS 行，第 i 个 PASS 行 → 第 (i+1) 级。
     * 这与 {@code auditProcessMultiLevelRelationBiz} 推进逻辑一致：每级 1 行，依次往后推。
     * 退回再重审时旧的 PASS 历史还留在表里但 currentVerifyTypeId 已回退；重审后又写新 PASS，
     * 总成绩在"最后一级 PASS"触发时拉到的全是最终生效 PASS 行——id 升序后取每级最后一条 PASS 即可。
     */
    private Map<Integer, MainVerifyProcess> latestPassPerLevel(List<MainVerifyProcess> all) {
        List<MainVerifyProcess> passed = new ArrayList<>();
        for (MainVerifyProcess p : all) {
            if (p.getIsAudit() != null && p.getIsAudit() == Constant.AUDIT_STATUS.PASS) {
                passed.add(p);
            }
        }
        passed.sort(Comparator.comparing(MainVerifyProcess::getId));
        Map<Integer, MainVerifyProcess> map = new HashMap<>();
        // 取末尾 N 条（N = max levelOrder 来自配置），按位置一一对应到 1..N 级
        // 但调用方 computeAndPersistTotalScore 已按 configs 遍历，缺级会得到 null score；
        // 简化：直接按"末尾倒数"映射，从尾向前 levelOrder 递减
        for (int i = passed.size() - 1, level = passed.size(); i >= 0; i--, level--) {
            map.put(level, passed.get(i));
        }
        return map;
    }

    private String resolveVerifyUserName(String verifyUserId) {
        if (verifyUserId == null || verifyUserId.isBlank()) return "";
        if (verifyUserId.startsWith("系统")) return verifyUserId; // 系统占位原样返回
        StringBuilder sb = new StringBuilder();
        for (String piece : verifyUserId.split("\\|")) {
            String t = piece.trim();
            if (t.isEmpty()) continue;
            try {
                String name = tblUserInfoDao.findNameById(Integer.parseInt(t));
                if (name != null) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(name);
                }
            } catch (NumberFormatException ignored) {
                if (sb.length() > 0) sb.append(",");
                sb.append(t);
            }
        }
        return sb.toString();
    }
}
