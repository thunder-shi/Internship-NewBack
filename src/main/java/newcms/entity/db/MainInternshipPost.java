package newcms.entity.db;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.NameRemarkOrderInfo;

@Getter
@Setter
@Entity
public class MainInternshipPost extends NameRemarkOrderInfo {
    @Column(columnDefinition = "integer unsigned comment '岗位类型id,外键，关联表BasePostType'")
    private Integer basePostTypeId;

    @Column(columnDefinition = "integer unsigned default '0' comment '岗位人数'")
    private Integer allPersonNum;

    @Column(columnDefinition = "int unsigned default '0' comment '已选人数'")
    private Integer nowPersonNum = 0;

    @Column(columnDefinition = "integer unsigned comment '实习项目id，外键，关联表MainInternship'")
    private Integer mainInternshipId;

}
