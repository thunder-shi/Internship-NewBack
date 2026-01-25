package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.AuditInfo;


@Getter
@Setter
@Entity
public class RelStuInternship extends AuditInfo {
    @Column(columnDefinition = "int unsigned not null comment '外键，关联表1（学生）'")
    private Integer studentId;

    @Column(columnDefinition = "int unsigned not null comment '外键，关联表9（岗位）'")
    private Integer postId;

    @Column(columnDefinition = "int unsigned not null comment '外键，关联表16（实习）'")
    private Integer internshipId;

    @Column(columnDefinition = "int unsigned not null default '1' comment '志愿轮数（第1轮，第2轮）'")
    private Integer round;

    @Column(columnDefinition = "int unsigned not null default '1' comment '志愿排序（第1志愿，第2志愿）'")
    private Integer sort;
}
