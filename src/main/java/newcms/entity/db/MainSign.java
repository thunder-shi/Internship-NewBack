package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.VerifyConfigInfo;

/**
 * 实习打卡表
 * <p>公共字段：BaseInfo（组一）+ VerifyConfigInfo（组五）</p>
 */
@Getter
@Setter
@Entity
public class MainSign extends VerifyConfigInfo {

    @Column(nullable = false, columnDefinition = "int unsigned not null comment '外键，关联 rel_stu_internship_post.id（view_main_sign 等视图按此关联）'")
    private Integer stuInternshipId;

    @Column(columnDefinition = "varchar(255) comment '打卡地点'")
    private String address;

    /** 数据库列名 type（打卡类型） */
    @Column(name = "type", columnDefinition = "tinyint comment '打卡类型'")
    private Byte signType;

    @Column(columnDefinition = "int unsigned comment '外键，关联 SysOssFile'")
    private Integer imgId;
}
