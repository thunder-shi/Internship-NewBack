package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

import java.math.BigDecimal;

/**
 * 审核情况表
 */
@Getter
@Setter
@Entity
public class MainVerifyProcess extends BaseInfo {
    @Column(nullable = false, columnDefinition = "integer unsigned comment '外键,关联表9、11、15、16、21、22、23'")
    private Integer relationId;

    @Column(nullable = true, columnDefinition = "integer unsigned comment '外键,关联表 20（RelProcessInternship），日志等独立流程可为 null'")
    private Integer processId;

    @Column(nullable = false, columnDefinition = "integer unsigned comment '外键,关联表1,当前流程创建人'")
    private Integer createUserId;

    @Column(nullable = false, columnDefinition = "varchar(255) comment '审核用户id，格式：12|14|17'")
    private String verifyUserId;

    @Column(nullable = false, columnDefinition = "smallint comment '是否审核：-1-保存未提交，0-未审核，1-审核通过，2-审核退回'")
    private Integer isAudit;

    @Column(columnDefinition = "varchar(1000) comment '审核理由'")
    private String reason;

    @Column(columnDefinition = "varchar(50) comment '当前审核操作表名（例如 MainDairy）'")
    private String tableName;

    @Column(columnDefinition = "decimal(5,2) comment '该级评分；该级若有 grade_config 配置且本行 PASS 时由审核接口写入，否则为 null'")
    private BigDecimal score;
}
