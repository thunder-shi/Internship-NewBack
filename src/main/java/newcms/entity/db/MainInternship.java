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
    @Column(columnDefinition = "int unsigned comment '实习类型'")
    private Integer internshipTypeId;
    @Column(columnDefinition = "int unsigned comment '创建人id'")
    private Integer creatorId;
    @Column(columnDefinition = "varchar(20) comment '实习报告周期'")
    private String cron;
    @Column(columnDefinition = "int unsigned comment '已选学生人数'")
    private Integer studentNum;

}
