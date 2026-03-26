package newcms.entity.db;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import newcms.entity.base.NameRemarkInfo;

@Getter
@Setter
@Entity
public class RelTitleTeacher extends NameRemarkInfo {
    @Column(columnDefinition = "int unsigned not null comment '外键，关联表1（教师）'")
    private Integer teacherId;

    @Column(columnDefinition = "int unsigned not null comment '外键，关联表16（实习）'")
    private Integer internshipId;

    @Column(columnDefinition = "integer default '1' comment '当前处在的审核级别'")
    private Integer currentVerifyTypeId = 1;

    @Column(columnDefinition = "smallint not null comment '是否限选'")
    private Integer isLimit;
}
