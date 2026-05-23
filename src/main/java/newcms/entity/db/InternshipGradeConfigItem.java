package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

import java.math.BigDecimal;

/**
 * 实习评分配置项：按 (internshipId, sourceTable, levelOrder) 维度，每级配一项 weight + maxScore。
 * <p>规则：</p>
 * <ul>
 *   <li>一级一行：同 (internshipId, sourceTable, levelOrder) 至多 1 条未软删记录（应用层守卫）</li>
 *   <li>同 (internshipId, sourceTable) 下所有 levelOrder 的 weight 总和 = 100</li>
 *   <li>levelOrder ∈ [1, 5]，且必须 ≤ 该业务实体在该 internship 下的 verifyTypeId - 1</li>
 *   <li>NO_VERIFY 级别（verifyTypeId &lt; ONE_VERIFY）不允许配置</li>
 * </ul>
 * <p>唯一性由 save() 方法的应用层查询守卫保证（查 isDeleted=false 的已有记录），
 * 不使用数据库唯一约束——软删后同 key 的 isDeleted 状态会冲突。</p>
 */
@Getter
@Setter
@Entity
public class InternshipGradeConfigItem extends BaseInfo {

    @Column(nullable = false, columnDefinition = "int unsigned comment '关联 BaseInternship.id（实习项目）'")
    private Integer internshipId;

    @Column(nullable = false, columnDefinition = "varchar(64) comment '评分目标业务表名，如 MainDiary'")
    private String sourceTable;

    @Column(nullable = false, columnDefinition = "int unsigned comment '由第几级审核人打分，对应业务实体 verifyFirstRoleId..verifyFifthRoleId，取值 [1,5]'")
    private Integer levelOrder;

    @Column(nullable = false, columnDefinition = "decimal(5,2) comment '占比 0-100'")
    private BigDecimal weight;

    @Column(nullable = false, columnDefinition = "decimal(5,2) default 100 comment '满分'")
    private BigDecimal maxScore = new BigDecimal("100");

    @Version
    @Column(columnDefinition = "int default 0 comment '乐观锁版本号'")
    private Integer version;
}
