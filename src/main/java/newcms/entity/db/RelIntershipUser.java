package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.NameRemarkInfo;

/**
 * 实习与用户关联表
 */
@Getter
@Setter
@Entity
public class RelIntershipUser extends NameRemarkInfo {

    @Column(columnDefinition = "int unsigned not null comment '外键，关联表16（实习）'")
    private Integer internshipId;

    @Column(columnDefinition = "int unsigned not null comment '外键，关联表1（用户）'")
    private Integer userId;

    @Column(columnDefinition = "integer default '1' comment '当前处在的审核级别'")
    private Integer currentVerifyTypeId = 1;
}

