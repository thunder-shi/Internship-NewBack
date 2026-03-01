package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;
import java.time.LocalDateTime;

/**
 * 审核流程-学生实习岗位选择视图
 * 包含审核流程信息、实习项目信息、学生信息和岗位信息
 */
@Getter
@Setter
@Entity
public class ViewVerifyProcessRelStuInternship extends BaseInfo {
    // 来自 main_verify_process 表的字段
    @Column(columnDefinition = "integer unsigned comment '关联记录ID'")
    private Integer relationId;

    @Column(columnDefinition = "integer unsigned comment '流程ID'")
    private Integer processId;

    @Column(columnDefinition = "integer unsigned comment '创建人ID'")
    private Integer createUserId;

    @Column(columnDefinition = "varchar(255) comment '审核用户ID，格式：12|14|17'")
    private String verifyUserId;

    @Column(columnDefinition = "smallint comment '是否审核：-1-保存未提交，0-未审核，1-审核通过，2-审核退回'")
    private Integer isAudit;

    @Column(columnDefinition = "varchar(50) comment '审核理由'")
    private String reason;

    @Column(columnDefinition = "varchar(50) comment '当前审核操作表名'")
    private String tableName;

    // 来自 view_rel_process_internship 视图的字段
    @Column(columnDefinition = "integer unsigned comment '实习项目ID'")
    private Integer internshipId;

    @Column(columnDefinition = "integer unsigned comment '流程类型ID'")
    private Integer processTypeId;

    @Column(columnDefinition = "integer unsigned comment '审核类型ID'")
    private Integer verifyTypeId;

    @Column(columnDefinition = "varchar(50) comment '实习项目编码'")
    private String internshipCode;

    @Column(columnDefinition = "varchar(1000) comment '实习项目备注'")
    private String internshipRemarks;

    @Column(columnDefinition = "varchar(50) comment '实习项目名称'")
    private String internshipName;

    @Column(columnDefinition = "varchar(50) comment '实习类型名称'")
    private String internshipTypeName;

    @Column(columnDefinition = "varchar(50) comment '实习类型简称'")
    private String intTypeName;

    @Column(columnDefinition = "varchar(50) comment '学校名称'")
    private String universityName;

    @Column(columnDefinition = "datetime comment '流程开始时间'")
    private LocalDateTime startTime;

    @Column(columnDefinition = "datetime comment '流程结束时间'")
    private LocalDateTime endTime;

    @Column(columnDefinition = "varchar(50) comment '当前审核级别名称'")
    private String currentVerifyTypeName;

    @Column(columnDefinition = "varchar(255) comment '专业ID列表'")
    private String majorIds;

    @Column(columnDefinition = "varchar(255) comment '专业名称列表'")
    private String majorNames;

    // 来自 base_user (createbaseuser) 的字段
    @Column(columnDefinition = "varchar(50) comment '创建人姓名'")
    private String createUserName;

    // 计算字段：通过子查询获取审核用户姓名
    @Column(columnDefinition = "varchar(255) comment '审核用户姓名（多个用逗号分隔）'")
    private String verifyUserName;

    // 来自 view_rel_stu_internship 视图的字段
    @Column(columnDefinition = "int unsigned comment '实习岗位ID'")
    private Integer internshipPostId;

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

    @Column(columnDefinition = "varchar(50) comment '企业名称'")
    private String companyName;

    @Column(columnDefinition = "integer unsigned comment '企业ID'")
    private Integer companyId;

    @Column(columnDefinition = "varchar(50) comment '学生姓名'")
    private String studentName;

    @Column(columnDefinition = "varchar(50) comment '学生Id'")
    private String studentId;

    @Column(columnDefinition = "integer unsigned comment '部门ID'")
    private Integer departmentId;

    @Column(columnDefinition = "varchar(50) comment '部门名称'")
    private String departmentName;
}
