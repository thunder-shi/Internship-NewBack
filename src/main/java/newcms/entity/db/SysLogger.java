package newcms.entity.db;

import newcms.entity.base.BaseInfo;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;

/**
 * 角色信息表
 */
@Getter
@Setter
@Entity
public class SysLogger extends BaseInfo {
    @Column(columnDefinition = "integer  unsigned not null")
    private Integer userId;
    @Column(columnDefinition = "varchar(255) comment ''")
    private String action;
    @Column(columnDefinition = "varchar(1000) comment ''")
    private String detail;


}