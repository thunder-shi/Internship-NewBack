package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.NameRemarkInfo;

import java.util.Date;

@Getter
@Setter
@Entity
public class RelTitleStudent extends NameRemarkInfo {
    @Column(columnDefinition = "int unsigned not null comment '外键，关联表32（导师题目）'")
    private Integer titleId;

    @Column(columnDefinition = "int unsigned not null comment '外键，关联表1（学生）'")
    private Integer stuId;

    @Column(columnDefinition = "integer default '1' comment '当前处在的审核级别'")
    private Integer currentVerifyTypeId = 1;

    @Column(columnDefinition = "varchar(50) comment '选题理由'")
    private String topicReasons;

    @Column(columnDefinition = "varchar(32) not null default 'STUDENT_CANDIDATE' comment 'source of title selection'")
    private String sourceType = "STUDENT_CANDIDATE";

    @Column(columnDefinition = "tinyint not null default 0 comment 'whether this row is the final title assignment'")
    private Integer isFinal = 0;

    @Column(columnDefinition = "int unsigned comment 'denormalized internship id from RelTitleTeacher'")
    private Integer internshipId;

    @Column(columnDefinition = "int unsigned comment 'final confirmer user id'")
    private Integer confirmedBy;

    @Column(columnDefinition = "datetime comment 'final confirmation time'")
    private Date confirmedTime;

    @Version
    @Column(columnDefinition = "int default 0 comment '乐观锁版本号'")
    private Integer version;
}
