package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

/**
 * 学生实习岗位选择表
 */
@Getter
@Setter
@Entity
public class RelStuInternshipPost extends BaseInfo {
    @Column(nullable = false, columnDefinition = "int unsigned comment '外键，关联表1（学生）'")
    private Integer studentId;

    @Column(nullable = false, columnDefinition = "int unsigned comment '外键，关联表9（实习项目）'")
    private Integer internshipPostId;

    @Column(columnDefinition = "integer default '1' comment '当前处在的审核级别'")
    private Integer currentVerifyTypeId = 1;

    @Column(columnDefinition = "tinyint not null default 0 comment 'internship status: 0 active, 1 terminating, 2 terminated'")
    private Integer internshipStatus = 0;

    @Column(columnDefinition = "int unsigned default 0 comment 'related MainInternshipTermination id'")
    private Integer terminationId = 0;

    @Column(columnDefinition = "datetime comment 'termination approved time'")
    private java.util.Date terminatedTime;

    @Version
    @Column(columnDefinition = "int default 0 comment '乐观锁版本号'")
    private Integer version;
}
