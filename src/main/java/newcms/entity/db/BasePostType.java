package newcms.entity.db;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import newcms.entity.base.NameRemarkOrderInfo;

@Getter
@Setter
@Entity
public class BasePostType extends NameRemarkOrderInfo {
    @Column(columnDefinition = "Integer unsigned comment '企业Id'")
    private Integer companyId;

    @Column(columnDefinition = "Integer unsigned comment '岗位薪资'")
    private Integer salary;

    @Column(columnDefinition = "varchar(255) comment '岗位地点'")
    private String address;
}
