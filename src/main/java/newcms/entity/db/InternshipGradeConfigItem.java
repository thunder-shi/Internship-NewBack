package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

import java.math.BigDecimal;

/**
 * 实习评分配置项：按 (internshipId, sourceTable, levelOrder) 维度配置一项分。
 * <p>规则：</p>
 * <ul>
 *   <li>一级一项分：同 (internshipId, sourceTable, levelOrder) 至多 1 条未软删记录</li>
 *   <li>同 (internshipId, sourceTable) 下所有 levelOrder 的 weight 总和 = 100</li>
 *   <li>levelOrder ∈ [1, 5]，且必须 ≤ 该业务实体在该 internship 下的 verifyTypeId</li>
 *   <li>NO_VERIFY 级别（verifyTypeId &lt; ONE_VERIFY）不允许配置任何评分项</li>
 * </ul>
 * <p>软删 + 唯一键：把 isDeleted 纳入唯一约束，避免软删后复用同 key 报 Duplicate。</p>
 */
@Getter
@Setter
@Entity
@Table(uniqueConstraints = {
        @UniqueConstraint(name = "uk_grade_cfg_internship_source_level_deleted",
                columnNames = {"internship_id", "source_table", "level_order", "is_deleted"})
})
public class InternshipGradeConfigItem extends BaseInfo {

    @Column(nullable = false, columnDefinition = "int unsigned comment '关联 BaseInternship.id（实习项目）'")
    private Integer internshipId;

    @Column(nullable = false, columnDefinition = "varchar(64) comment '评分目标业务表名，如 MainDiary'")
    private String sourceTable;

    @Column(nullable = false, columnDefinition = "int unsigned comment '由第几级审核人打分，对应业务实体 verifyFirstRoleId..verifyFifthRoleId 第几级，取值 [1,5]'")
    private Integer levelOrder;

    @Column(nullable = false, columnDefinition = "varchar(64) comment '成绩项名称'")
    private String itemName;

    @Column(nullable = false, columnDefinition = "decimal(5,2) comment '占比 0-100'")
    private BigDecimal weight;

    @Column(nullable = false, columnDefinition = "decimal(5,2) default 100 comment '满分'")
    private BigDecimal maxScore = new BigDecimal("100");

    @Column(columnDefinition = "int unsigned default 0 comment '排序'")
    private Integer orderNum = 0;

    @Version
    @Column(columnDefinition = "int default 0 comment '乐观锁版本号'")
    private Integer version;
}
