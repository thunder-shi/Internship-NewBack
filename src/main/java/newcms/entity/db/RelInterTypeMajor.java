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
public class RelInterTypeMajor extends BaseInfo {
    @Column(columnDefinition = "integer unsigned not null comment '实习类型id，外键，关联表7'")
    private Integer internshipTypeId;
    
    @Column(columnDefinition = "integer unsigned not null comment '专业id，外键，关联表6'")
    private Integer majorId;
}
