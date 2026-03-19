package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.NameRemarkInfo;

/**
 * 教师与学生实习关联表
 */
@Getter
@Setter
@Entity
public class RelTeacherStudent extends NameRemarkInfo {

    @Column(columnDefinition = "int unsigned not null comment '外键，关联表1（教师）'")
    private Integer teacherId;

    @Column(columnDefinition = "int unsigned not null comment '外键，关联表16（实习）'")
    private Integer internshipId;

    @Column(columnDefinition = "int unsigned not null comment '外键，关联表1（学生）'")
    private Integer stuId;

    @Column(columnDefinition = "integer default '1' comment '当前处在的审核级别'")
    private Integer currentVerifyTypeId = 1;
}
