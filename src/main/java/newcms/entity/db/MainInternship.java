package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.AuditInfo;

import java.time.LocalDate;

/**
 * 实习项目表
 * @author wang zhengqi
 */

@Entity
@Getter
@Setter
public class MainInternship extends AuditInfo {
    @Column(nullable = false, columnDefinition = "datetime comment '实习开始时间'")
    private LocalDate startDate;
    @Column(nullable = false, columnDefinition = "datetime comment '实习结束时间'")
    private LocalDate endDate;
    @Column(columnDefinition = "int unsigned comment '实习类型'")
    private Integer internshipTypeId;
    @Column(columnDefinition = "int unsigned comment '创建人id'")
    private Integer creatorId;
    @Column(columnDefinition = "varchar(20) comment '实习报告周期'")
    private String cron;
    @Column(columnDefinition = "datetime comment '实习上报开始时间'")
    private LocalDate reportStartDate;
    @Column(columnDefinition = "datetime comment '实习上报结束时间'")
    private LocalDate reportEndDate;
}
