package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

/**
 * 审核流程-导师学生选择视图
 * 包含审核流程信息、实习项目信息、教师信息和学生信息
 */
@Getter
@Setter
@Entity
public class ViewVerifyProcessRelTeacherStudent extends BaseInfo {
    // 来自 main_verify_process 表的字段
    @jakarta.persistence.Column(columnDefinition = "integer unsigned comment '外键,关联表RelTeacherStudent'")
    private Integer relationId;

    @jakarta.persistence.Column(columnDefinition = "integer unsigned comment '外键,关联表20'")
    private Integer processId;

    @jakarta.persistence.Column(columnDefinition = "integer unsigned comment '外键,关联表1,当前流程创建人'")
    private Integer createUserId;

    @jakarta.persistence.Column(columnDefinition = "varchar(255) comment '审核用户id，格式：12|14|17'")
    private String verifyUserId;

    @jakarta.persistence.Column(columnDefinition = "smallint comment '是否审核：-1-保存未提交，0-未审核，1-审核通过，2-审核退回'")
    private Integer isAudit;

    @jakarta.persistence.Column(columnDefinition = "varchar(50) comment '审核理由'")
    private String reason;

    @jakarta.persistence.Column(columnDefinition = "varchar(50) comment '当前审核操作表名'")
    private String tableName;

    // 来自 rel_teacher_student 表的字段
    @jakarta.persistence.Column(columnDefinition = "varchar(50) comment '题目名称'")
    private String name;

    @jakarta.persistence.Column(columnDefinition = "int unsigned comment '外键，关联表1（教师）'")
    private Integer teacherId;

    @jakarta.persistence.Column(columnDefinition = "int unsigned comment '外键，关联表16（实习）'")
    private Integer internshipId;

    // @jakarta.persistence.Column(columnDefinition = "int unsigned comment '外键，关联表1（学生）'")
    // private Integer stuId;

    @jakarta.persistence.Column(columnDefinition = "int unsigned comment '外键，关联表11（实习批次）'")
    private Integer relInternshipId;

    // 来自 main_internship 表的字段
    @jakarta.persistence.Column(columnDefinition = "varchar(50) comment '实习项目名称'")
    private String internshipName;

    // 来自 base_user 表的字段（教师）
    @jakarta.persistence.Column(columnDefinition = "varchar(50) comment '教师姓名'")
    private String teacherName;

    // 来自 base_user 表的字段（创建人）
    @jakarta.persistence.Column(columnDefinition = "varchar(50) comment '创建人姓名'")
    private String createUserName;

    // 来自 base_user 表的字段（审核人）
    @jakarta.persistence.Column(columnDefinition = "varchar(50) comment '审核人姓名'")
    private String verifyUserName;

    @Column(columnDefinition = "varchar(1000) comment '备注'")
    private String remarks;
}

