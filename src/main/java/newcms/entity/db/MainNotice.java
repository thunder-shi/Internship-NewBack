package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.NameRemarkInfo;

import java.util.Date;

/**
 * 实习通知主表（指导老师发布）
 */
@Getter
@Setter
@Entity
public class MainNotice extends NameRemarkInfo {

    @Column(nullable = false, columnDefinition = "varchar(200) not null comment '通知标题'")
    private String title;

    @Column(nullable = false, columnDefinition = "text not null comment '通知正文'")
    private String content;

    @Column(nullable = false, columnDefinition = "int unsigned not null comment '外键，关联 main_internship'")
    private Integer internshipId;

    @Column(nullable = false, columnDefinition = "int unsigned not null comment '外键，关联 base_user（发布老师）'")
    private Integer publisherId;

    @Column(columnDefinition = "datetime comment '发布时间'")
    private Date publishTime;
}
