package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
//import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.NameRemarkInfo;

/**
 * 审核流程-学生选题视图（view_verify_process_rel_title_student）
 * 包含审核流程信息、学生选题信息、题目信息以及相关用户姓名。
 */
@Getter
@Setter
@Entity
//@Table(name = "view_verify_process_rel_title_student")
public class ViewVerifyProcessRelTitleStudent extends NameRemarkInfo {
    // 来自 main_verify_process（mvp）
    @Column(name = "relation_id")
    private Integer relationId;

    @Column(name = "process_id")
    private Integer processId;

    @Column(name = "table_name")
    private String tableName;

    @Column(name = "create_user_id")
    private Integer createUserId;

    @Column(name = "verify_user_id")
    private String verifyUserId;

    @Column(name = "is_audit")
    private Integer isAudit;

    @Column(name = "reason")
    private String reason;

    // 来自 rel_title_student（rts）
    @Column(name = "rel_title_student_id")
    private Integer relTitleStudentId;

    @Column(name = "current_verify_type_id")
    private Integer currentVerifyTypeId;

    @Column(name = "stu_id")
    private Integer stuId;

    @Column(name = "title_id")
    private Integer titleId;

    // 来自 rel_title_teacher（rtt）
    @Column(name = "rel_title_teacher_id")
    private Integer relTitleTeacherId;

    @Column(name = "internship_id")
    private Integer internshipId;

    @Column(name = "teacher_id")
    private Integer teacherId;

    @Column(name = "is_limit")
    private Integer isLimit;

    @Column(name = "code")
    private String code;

    @Column(name = "name")
    private String name;

    @Column(name = "remarks")
    private String remarks;

    // 关联显示字段（base_user）
    @Column(name = "student_name")
    private String studentName;

    @Column(name = "teacher_name")
    private String teacherName;

    @Column(name = "create_user_name")
    private String createUserName;

    @Column(name = "verify_user_name")
    private String verifyUserName;

    // 来自 main_internship（经 rel_process_internship 与 rtt.internship_id 一致约束）
    @Column(name = "m_internship_id")
    private Integer mInternshipId;

    @Column(name = "internship_name")
    private String internshipName;

    @Column(name = "topic_Reasons")
    private String topicReasons;
}

