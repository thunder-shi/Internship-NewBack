package newcms.entity.db;

import newcms.entity.base.BaseInfo;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;

/**
 * 网站基本信息表
 */
@Getter
@Setter
@Entity
public class BaseMain extends BaseInfo {
    @Column(columnDefinition = "varchar(255) comment '本站名称'")
    private String mainName;
    @Column(columnDefinition = "varchar(255) comment '本站地址'")
    private String mainUrl;
}
