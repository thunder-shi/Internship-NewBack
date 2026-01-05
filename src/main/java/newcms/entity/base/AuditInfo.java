package newcms.entity.base;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.Date;

@MappedSuperclass
@Getter
@Setter
public class AuditInfo extends NameRemarkOrderInfo {
    @Column(nullable = false,columnDefinition = "smallint default '1' comment '是否审核'")
    private Integer isAudit;
    @Column(columnDefinition = "varchar(50) comment '审核理由'")
    private String reason;
    @Column(columnDefinition = "integer unsigned comment '最后审核人id'")
    private Integer auditUserId;
    @Column(columnDefinition = " datetime default CURRENT_TIMESTAMP comment '最后审核时间'")
    private Date auditTime;
}
