package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.VerifyConfigInfo;

import java.time.LocalDateTime;

/**
 * 实习项目流程关联表
 */

@Getter
@Setter
@Entity
public class RelProcessInternship extends VerifyConfigInfo {
    @Column(columnDefinition = "integer unsigned comment '实习项目id，外键，关联表BaseInternship'")
    private Integer internshipId;
    @Column(columnDefinition = "integer unsigned comment '流程类型id，外键，关联表BaseProcessType'")
    private Integer processTypeId;
    @Column(columnDefinition = "datetime comment '流程开始时间'")
    private LocalDateTime startTime;
    @Column(columnDefinition = "datetime comment '流程结束时间'")
    private LocalDateTime endTime;

    @Version
    @Column(columnDefinition = "int default 0 comment '乐观锁版本号'")
    private Integer version;
}
