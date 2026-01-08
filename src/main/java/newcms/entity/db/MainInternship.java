package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.NameRemarkOrderInfo;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 实习项目表
 * @author wang zhengqi
 */

@Entity
@Getter
@Setter
public class MainInternship extends NameRemarkOrderInfo {
    // @Column(nullable = false, columnDefinition = "datetime comment '实习开始时间'")
    // private LocalDateTime startTime;
    // @Column(nullable = false, columnDefinition = "datetime comment '实习结束时间'")
    // private LocalDateTime endTime;
    @Column(columnDefinition = "int unsigned comment '实习类型'")
    private Integer internshipTypeId;
    @Column(columnDefinition = "int unsigned comment '创建人id'")
    private Integer creatorId;
    @Column(columnDefinition = "varchar(20) comment '实习报告周期'")
    private String cron;
    // @Column(columnDefinition = "date comment '实习上报开始日期'")
    // private LocalDate reportStartDate;
    // @Column(columnDefinition = "date comment '实习上报结束日期'")
    // private LocalDate reportEndDate;
    @Column(columnDefinition = "int unsigned comment '已选学生人数'")
    private Integer studentNum;

}
