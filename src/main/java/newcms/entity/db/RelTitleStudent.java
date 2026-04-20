package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.NameRemarkInfo;

@Getter
@Setter
@Entity
public class RelTitleStudent extends NameRemarkInfo {
    @Column(columnDefinition = "int unsigned not null comment '外键，关联表32（导师题目）'")
    private Integer titleId;

    @Column(columnDefinition = "int unsigned not null comment '外键，关联表1（学生）'")
    private Integer stuId;

    @Column(columnDefinition = "integer default '1' comment '当前处在的审核级别'")
    private Integer currentVerifyTypeId = 1;

    @Column(columnDefinition = "varchar(50) comment '选题理由'")
    private String topicReasons;

    @Version
    @Column(columnDefinition = "int default 0 comment '乐观锁版本号'")
    private Integer version;
}
