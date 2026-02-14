package newcms.entity.db;

import newcms.entity.base.BaseInfo;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;

/**
 * 岗位专业关联表
 */
@Getter
@Setter
@Entity
public class RelPostMajor extends BaseInfo {
    @Column(columnDefinition = "integer unsigned not null comment '岗位类型id，外键，关联表8'")
    private Integer postTypeId;
    
    @Column(columnDefinition = "integer unsigned not null comment '专业id，外键，关联表6'")
    private Integer majorId;
}
