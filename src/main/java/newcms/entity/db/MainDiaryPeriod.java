package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.NameRemarkInfo;

import java.time.LocalDateTime;

/**
 * 实习日志时间表（定义每个实习项目的日志提交周期）
 * 公共字段：BaseInfo(组一) + NameRemarkInfo(组二)
 */
@Getter
@Setter
@Entity
public class MainDiaryPeriod extends NameRemarkInfo {

    @Column(columnDefinition = "int unsigned not null comment '外键，关联表16（MainInternship）'")
    private Integer internshipId;

    @Column(columnDefinition = "datetime not null comment '该期开始时间'")
    private LocalDateTime beginTime;

    @Column(columnDefinition = "datetime not null comment '该期结束时间'")
    private LocalDateTime endTime;

    @Column(columnDefinition = "int unsigned not null comment '期数（第几期，1-based）'")
    private Integer periodIndex;
}
