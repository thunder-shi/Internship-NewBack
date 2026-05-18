package newcms.service;

import com.alibaba.fastjson.JSONObject;

import java.math.BigDecimal;
import java.util.List;

/**
 * 实习评分配置：CRUD + 总成绩计算。
 * <p>评分配置按 (internshipId, sourceTable, levelOrder) 维度配置，每级配一项分；
 * 同 (internshipId, sourceTable) 下所有 levelOrder 的 weight 总和必须等于 100。</p>
 */
public interface IInternshipGradeConfigService {

    /**
     * 列出某实习项目下某业务表的全部评分项 + 给前端 UI 使用的元信息。
     * 返回结构：
     * <pre>
     * { items: [...], maxLevelOrder: 4 | null, locked: true|false,
     *   totalWeight: BigDecimal, valid: bool }
     * </pre>
     * <ul>
     *   <li>maxLevelOrder = 业务实体 verifyTypeId - 1，未生成日志或 NO_VERIFY 时为 null</li>
     *   <li>locked = true 表示该 (internshipId, sourceTable) 存在进行中/已通过的审核行，配置应只读</li>
     * </ul>
     */
    JSONObject list(Integer internshipId, String sourceTable);

    /**
     * 单项新增/编辑评分项。node 含：id?（编辑时必传）、internshipId、sourceTable、levelOrder、itemName、weight、maxScore?、orderNum?
     * <p>会强制校验：</p>
     * <ul>
     *   <li>levelOrder ∈ [1, 业务实体 verifyTypeId]；NO_VERIFY 配置直接拒绝</li>
     *   <li>本次保存后同 (internshipId, sourceTable) 下 SUM(weight) = 100</li>
     *   <li>该 internshipId 下该 sourceTable 若已有 MainVerifyProcess 处于 SUBMIT/PASS，禁止改动</li>
     *   <li>同 (internshipId, sourceTable, levelOrder) 仅允许 1 条未软删记录</li>
     * </ul>
     * @return 保存后的 JSONObject
     */
    JSONObject save(JSONObject node);

    /**
     * 批量替换式保存：在同一事务里软删 (internshipId, sourceTable) 下旧的全部评分项，
     * 再插入 node.items（数组），最后校验 SUM(weight)=100 + 守卫（NO_VERIFY/审核进行中/levelOrder 越界/级别冲突）。
     * <p>入参示例：</p>
     * <pre>
     * { internshipId: 123, sourceTable: 'MainDiary',
     *   items: [{levelOrder, itemName, weight, maxScore?, orderNum?}, ...] }
     * </pre>
     * 用于"≥2 项首次配置"场景，规避单项保存因 SUM≠100 失败的死锁。
     * @return 保存后的列表（同 list 接口的 items）
     */
    JSONObject saveBatch(JSONObject node);

    /** 软删除一条配置项；同样要做审核进行中守卫 */
    void delete(Integer configItemId);

    /**
     * 独立的权重和校验接口（供前端编辑过程中调用）。
     * @return {valid: true/false, sum: BigDecimal, expected: 100, items: [...]}
     */
    JSONObject validateWeights(Integer internshipId, String sourceTable);

    /**
     * 该 internshipId + sourceTable 是否配置了评分项（至少 1 条未软删）。
     * 给 auditProcess 的"PASS 前是否需要 score"判定使用。
     */
    boolean hasConfig(Integer internshipId, String sourceTable);

    /**
     * 某 (internshipId, sourceTable, levelOrder) 是否要求填 score 才能通过；
     * 命中则同步校验 score 必填、范围 [0, maxScore]。校验失败抛 BaseException。
     */
    void requireScoreOnPass(Integer internshipId, String sourceTable, Integer levelOrder, BigDecimal score);

    /**
     * 解析某条 MainDiary 对应的 internshipId（校外走 RelStuInternshipPost → MainInternshipPost；校内走 RelTitleStudent.internshipId）。
     * 解析不到返回 null。
     */
    Integer resolveInternshipIdForDiary(Integer diaryId);

    /**
     * 最后一级 PASS 触发：拉所有级 latestVerifyRecord(isAudit=PASS) 的 score，按 weight 加权计算总分，
     * 把 totalScore / scoreDetail / totalScoreLockTime 写入业务源表。
     * <p>若该 (internshipId, sourceTable) 未配置任何评分项，静默返回（不报错、不写）。</p>
     */
    void computeAndPersistTotalScore(Integer relationId, String sourceTable);
}
