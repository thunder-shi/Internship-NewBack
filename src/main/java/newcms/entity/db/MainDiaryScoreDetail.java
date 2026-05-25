package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

import java.math.BigDecimal;

/**
 * 实习日志评分明细：每条记录对应某日志在某审核级的实际打分。
 * <p>由最后一级 PASS 时由 {@code computeAndPersistTotalScore} 写入，
 * 替代原先 MainDiary.scoreDetail 的 JSON 快照存储。</p>
 */
@Getter
@Setter
@Entity
public class MainDiaryScoreDetail extends BaseInfo {

    /** 关联日志 ID（MainDiary.id） */
    @Column(nullable = false, columnDefinition = "int unsigned comment '关联日志ID，MainDiary.id'")
    private Integer diaryId;

    /** 审核级别序号，与 InternshipGradeConfigItem.levelOrder 对应 */
    @Column(nullable = false, columnDefinition = "int unsigned comment '审核级别序号 [1,5]'")
    private Integer levelOrder;

    /** 该级别权重占比 */
    @Column(nullable = false, columnDefinition = "decimal(5,2) comment '权重占比 0-100'")
    private BigDecimal weight;

    /** 该级别满分 */
    @Column(nullable = false, columnDefinition = "decimal(5,2) default 100 comment '满分'")
    private BigDecimal maxScore = new BigDecimal("100");

    /** 审核人实际给分 */
    @Column(columnDefinition = "decimal(5,2) comment '审核人实际给分'")
    private BigDecimal score;

    /** 审核人用户ID */
    @Column(columnDefinition = "varchar(200) comment '审核人用户ID'")
    private String verifyUserId;

    /** 审核人姓名 */
    @Column(columnDefinition = "varchar(200) comment '审核人姓名'")
    private String verifyUserName;
}
