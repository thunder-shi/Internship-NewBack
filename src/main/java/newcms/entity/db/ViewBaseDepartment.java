package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseTreeInfo;

@Getter
@Setter
@Entity
public class ViewBaseDepartment extends BaseTreeInfo<ViewBaseDepartment> {
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

    @Column(nullable = false, columnDefinition = "int unsigned comment '外键,关联表 10'")
    private Integer areaId;

    @Column(nullable = false, columnDefinition = "TINYINT comment '1.企业 2.学校 3.班级'")
    private Integer departmentType;

    @Column(nullable = false, columnDefinition = "int unsigned comment '外码 (关联表 6, 如果当前节点表示班级, 否则为 0)'")
    private Integer majorId;

    @Column(columnDefinition = "int unsigned comment '入学年份, 班级需要, 否则为空'")
    private Integer startYear;
    private String majorName;
    private String areaName;
}
