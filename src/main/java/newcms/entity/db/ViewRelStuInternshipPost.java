package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

/**
 * 学生实习岗位选择视图
 * 包含学生信息、实习岗位信息和选择信息
 */
@Getter
@Setter
@Entity
public class ViewRelStuInternshipPost extends BaseInfo {
    // 来自 rel_stu_internship_post 表的字段
    @Column(columnDefinition = "int unsigned comment '学生ID'")
    private Integer studentId;

    @Column(columnDefinition = "int unsigned comment '实习岗位ID'")
    private Integer internshipPostId;

    // 来自 view_base_user 表的字段
    @Column(columnDefinition = "varchar(50) comment '学生姓名'")
    private String studentName;

    @Column(columnDefinition = "varchar(50) comment '学生学号'")
    private String studentAccount;

    @Column(columnDefinition = "integer unsigned comment '部门ID'")
    private Integer departmentId;

    @Column(columnDefinition = "varchar(50) comment '部门名称'")
    private String departmentName;

    // 来自 view_main_internship_post 表的字段
    @Column(columnDefinition = "varchar(50) comment '实习岗位编码'")
    private String internshipPostCode;

    @Column(columnDefinition = "varchar(50) comment '实习岗位名称'")
    private String internshipPostName;

    @Column(columnDefinition = "varchar(1000) comment '实习岗位备注'")
    private String internshipPostRemarks;

    @Column(columnDefinition = "int unsigned comment '总人数'")
    private Integer allPersonNum;

    @Column(columnDefinition = "int unsigned comment '当前人数'")
    private Integer nowPersonNum;

    @Column(columnDefinition = "varchar(50) comment '实习项目名称'")
    private String internshipName;

    @Column(columnDefinition = "varchar(50) comment '企业名称'")
    private String companyName;

    @Column(columnDefinition = "integer unsigned comment '企业ID'")
    private Integer companyId;

    @Column(columnDefinition = "varchar(200) comment '自主实习单位名称'")
    private String selfCompanyName;

    @Column(columnDefinition = "varchar(200) comment '自主实习岗位名称'")
    private String selfPostName;

    @Column(columnDefinition = "varchar(500) comment '自主实习地址'")
    private String selfAddress;

    @Column(columnDefinition = "varchar(1000) comment '自主实习备注/说明'")
    private String selfRemarks;
}
