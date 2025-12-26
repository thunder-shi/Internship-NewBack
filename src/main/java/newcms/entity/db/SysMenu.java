package newcms.entity.db;
import newcms.entity.base.BaseTreeInfo;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;

/**
 * 菜单信息表
 */
@Getter
@Setter
@Entity
public class SysMenu extends BaseTreeInfo<SysMenu> {
    @Column(columnDefinition = "varchar(255) comment '图标'")
    private String icon;
    @Column(columnDefinition = "varchar(255) comment '前端路由地址'")
    private String path;
    @Column(columnDefinition = "varchar(255) comment '前端组件路径 指向文件'")
    private String component;
    @Column(columnDefinition = "bit(1) default b'0' comment '是否隐藏'" )
    private Boolean hidden=false ;
}
