package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

/**
 * 学生实习岗位选择表
 */
@Getter
@Setter
@Entity
public class RelStuInternshipPost extends BaseInfo {
    @Column(nullable = false, columnDefinition = "int unsigned comment '外键，关联表1（学生）'")
    private Integer studentId;

    @Column(nullable = false, columnDefinition = "int unsigned comment '外键，关联表9（实习项目）'")
    private Integer internshipPostId;

    @Column(columnDefinition = "integer default '1' comment '当前处在的审核级别'")
    private Integer currentVerifyTypeId = 1;
}
