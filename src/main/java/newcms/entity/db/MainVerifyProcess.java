package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

/**
 * 审核情况表
 */
@Getter
@Setter
@Entity
public class MainVerifyProcess extends BaseInfo {
    @Column(nullable = false, columnDefinition = "integer unsigned comment '外键，关联表 11、15、20、21、22、23'")
    private Integer relationId;

    @Column(nullable = false, columnDefinition = "integer unsigned comment '外键，关联表 1，当前流程创建人'")
    private Integer createUserId;

    @Column(nullable = false, columnDefinition = "varchar(255) comment '审核用户id，格式：12|14|17'")
    private String verifyUserId;

    @Column(nullable = false, columnDefinition = "smallint comment '是否审核：-1-保存未提交，0-未审核，1-审核通过，2-审核退回'")
    private Integer isAudit;

    @Column(columnDefinition = "varchar(50) comment '审核理由'")
    private String reason;

    @Column(columnDefinition = "varchar(50) comment '当前审核操作表名（例如 MainDairy）'")
    private String tableName;
}
