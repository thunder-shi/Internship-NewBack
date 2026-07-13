package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

/**
 * 实习项目范围表
 */
@Getter
@Setter
@Entity
public class RelCounselorClass extends BaseInfo {
    @Column(columnDefinition = "integer unsigned not null comment '辅导员id，外键，关联base_user'")
    private Integer counselorId;

    @Column(columnDefinition = "integer unsigned not null comment '班级id，外键，关联表base_department'")
    private Integer classId;
}
