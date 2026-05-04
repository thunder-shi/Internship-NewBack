package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.NameRemarkInfo;

import java.util.Date;

/**
 * 学生选题审核合并视图（view_verify_process_rel_title_student_merge）。
 * latest 子查询取每个 process_id+relation_id 下最新 mvp，再关联 rts、rtt、view_rel_process_internship 等。
 */
@Getter
@Setter
@Entity

public class ViewVerifyProcessRelTitleStudentMerge extends NameRemarkInfo {

    // 来自 main_verify_process（mvp，最新一条）
    @Column(name = "relation_id")
    private Integer relationId;

    @Column(name = "table_name")
    private String tableName;

    @Column(name = "create_user_id")
    private Integer createUserId;

    @Column(name = "verify_user_id")
    private String verifyUserId;

    @Column(name = "process_id")
    private Integer processId;

    @Column(name = "is_audit")
    private Integer isAudit;

    @Column(name = "reason")
    private String reason;

    @Column(name = "verify_process_id")
    private Integer verifyProcessId;

    @Column(name = "vp_create_time")
    private Date vpCreateTime;

    @Column(name = "vp_update_time")
    private Date vpUpdateTime;

    // 来自 rel_title_teacher（rtt）；code/name/remarks 继承 NameRemarkInfo

    @Column(name = "internship_id")
    private Integer internshipId;

    @Column(name = "teacher_id")
    private Integer teacherId;

    @Column(name = "rel_title_teacher_id")
    private Integer relTitleTeacherId;

    @Column(name = "is_limit")
    private Integer isLimit;

    // 来自 rel_title_student（rts）
    @Column(name = "rel_title_student_id")
    private Integer relTitleStudentId;

    @Column(name = "current_verify_type_id")
    private Integer currentVerifyTypeId;

    @Column(name = "stu_id")
    private Integer stuId;

    @Column(name = "title_id")
    private Integer titleId;

    @Column(name = "name")
    private String name;

    @Column(name = "remarks")
    private String remarks;
    
    // 关联显示
    @Column(name = "create_user_name")
    private String createUserName;

    @Column(name = "internship_name")
    private String internshipName;

    @Column(name = "teacher_name")
    private String teacherName;

    @Column(name = "verify_user_name")
    private String verifyUserName;

    @Column(name = "student_name")
    private String studentName;

    @Column(name = "student_account")
    private String studentAccount;

    @Column(name = "current_verify_type_name")
    private String currentVerifyTypeName;

    @Column(name = "m_internship_id")
    private Integer mInternshipId;

    @Column(name = "current_role_name")
    private String currentRoleName;

    @Column(name = "is_all_verified")
    private Boolean isAllVerified;

    @Column(name = "topic_Reasons")
    private String topicReasons;

    @Column(name = "source_type")
    private String sourceType;

    @Column(name = "is_final")
    private Integer isFinal;

    @Column(name = "confirmed_by")
    private Integer confirmedBy;

    @Column(name = "confirmed_time")
    private Date confirmedTime;
}
