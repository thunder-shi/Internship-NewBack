package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.VerifyConfigInfo;

import java.time.LocalDateTime;

/**
 * 请假记录表（表22）
 * <p>公共字段：BaseInfo（组一）+ VerifyConfigInfo（组五）</p>
 */
@Getter
@Setter
@Entity
public class MainLeave extends VerifyConfigInfo {

    @Column(nullable = false, columnDefinition = "int unsigned not null comment '关联记录ID：校外=rel_stu_internship_post.id，校内=rel_title_student.id'")
    private Integer stuInternshipId;

    @Column(nullable = false, columnDefinition = "datetime not null comment '请假开始时间'")
    private LocalDateTime startTime;

    @Column(nullable = false, columnDefinition = "datetime not null comment '请假结束时间'")
    private LocalDateTime endTime;

    @Column(columnDefinition = "varchar(1000) comment '请假备注/审核意见'")
    private String remarks;

    @Column(columnDefinition = "integer default '1' comment '流程当前处在的审核级别id'")
    private Integer currentVerifyTypeId = 1;

    @Version
    @Column(columnDefinition = "int default 0 comment '乐观锁版本号'")
    private Integer version;
}
