package newcms.entity.db;

import newcms.entity.base.BaseInfo;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;

/**
 * 实习项目范围表
 */
@Getter
@Setter
@Entity
public class RelInterMajor extends BaseInfo {
    @Column(columnDefinition = "integer unsigned not null comment '实习项目id，外键，关联表16'")
    private Integer internshipId;
    
    @Column(columnDefinition = "integer unsigned not null comment '专业id，外键，关联表6'")
    private Integer majorId;
}
