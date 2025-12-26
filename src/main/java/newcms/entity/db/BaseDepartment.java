package newcms.entity.db;


import newcms.entity.base.BaseTreeInfo;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;


/**
 * 部门信息表
 */
@Getter
@Setter
@Entity
public class BaseDepartment extends BaseTreeInfo<BaseDepartment>{
    @Column(columnDefinition = "varchar(255) comment '详细地址'")
    private String departmentAdd;
    @Column(columnDefinition = "varchar(50) comment '邮政编码'")
    private String departmentPostalCode;
    @Column(columnDefinition = "varchar(50) comment '电话'")
    private String departmentPhone;
    @Column(columnDefinition = "varchar(50) comment '传真'")
    private String departmentFax;
    @Column(columnDefinition = "varchar(50) comment '电子邮箱'")
    private String departmentEmail;
}
