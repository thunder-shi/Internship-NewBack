package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.NameRemarkInfo;
import java.time.LocalDateTime;

/**
 * 实习项目表
 * @author wang zhengqi
 */

@Entity
@Getter
@Setter
public class MainInternship extends NameRemarkInfo {
    @Column(columnDefinition = "int unsigned comment '实习类型'")
    private Integer internshipTypeId;
    @Column(columnDefinition = "int unsigned comment '创建人id'")
    private Integer creatorId;
    @Column(columnDefinition = "varchar(20) comment '实习报告周期（cron 表达式，如 DAILY/WEEKLY/MONTHLY）'")
    private String cron;
    @Column(columnDefinition = "int unsigned comment '预估学生人数'")
    private Integer studentNum;
    @Column(columnDefinition = "datetime comment '实习上报开始时间'")
    private LocalDateTime reportStartTime;
    @Column(columnDefinition = "datetime comment '实习上报结束时间'")
    private LocalDateTime reportEndTime;

    @Column(columnDefinition = "integer unsigned default '1' comment '流程当前处在的审核级别id，外键，关联表BaseVerifyType'")
    private Integer currentVerifyTypeId = 1;
}
