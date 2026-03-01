package newcms.entity.db;

import newcms.entity.base.BaseInfo;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;

/**
 * 用户角色表
 */
@Getter
@Setter
@Entity
public class RelUserRole extends BaseInfo {
    @Column(columnDefinition = "integer  unsigned not null")
    private Integer userId;
    @Column(columnDefinition = "integer unsigned not null")
    private Integer roleId;
}