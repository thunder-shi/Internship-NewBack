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
public class BaseDepartment extends BaseTreeInfo<BaseDepartment> {
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

    @Column(columnDefinition = "int unsigned comment '外键,关联表 10'")
    private Integer areaId;

    @Column(columnDefinition = "int unsigned default '0' comment '外码 (关联表25)'")
    private Integer typeId;

    @Column(columnDefinition = "int unsigned default '0' comment '外码 (关联表 6, 如果当前节点表示班级, 否则为 0)'")
    private Integer majorId;
    @Column(columnDefinition = "int unsigned comment '入学年份, 班级需要, 否则为空'")
    private Integer startYear;
}
