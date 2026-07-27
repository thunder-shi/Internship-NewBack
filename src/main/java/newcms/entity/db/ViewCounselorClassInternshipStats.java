package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

/**
 * 只读视图：辅导员所辖班级学生实习统计报表。
 */
@Getter
@Setter
@Entity
@Table(name = "view_counselor_class_internship_stats")
public class ViewCounselorClassInternshipStats extends BaseInfo {

    @Column(name = "counselor_id")
    private Integer counselorId;

    @Column(name = "counselor_name")
    private String counselorName;

    @Column(name = "class_id")
    private Integer classId;

    @Column(name = "class_name")
    private String className;

    @Column(name = "student_id")
    private Integer studentId;

    @Column(name = "student_name")
    private String studentName;

    @Column(name = "student_account")
    private String studentAccount;

    @Column(name = "internship_id")
    private Integer internshipId;

    @Column(name = "internship_name")
    private String internshipName;

    @Column(name = "internship_mode")
    private String internshipMode;

    @Column(name = "internship_mode_name")
    private String internshipModeName;

    @Column(name = "relation_id")
    private Integer relationId;

    @Column(name = "relation_table")
    private String relationTable;

    @Column(name = "subject_name")
    private String subjectName;

    @Column(name = "school_teacher_name")
    private String schoolTeacherName;

    @Column(name = "company_teacher_name")
    private String companyTeacherName;

    @Column(name = "sign_count")
    private Integer signCount;

    @Column(name = "sign_passed")
    private Integer signPassed;

    @Column(name = "leave_count")
    private Integer leaveCount;

    @Column(name = "leave_passed")
    private Integer leavePassed;

    @Column(name = "task_total")
    private Integer taskTotal;

    @Column(name = "task_submitted")
    private Integer taskSubmitted;

    @Column(name = "task_passed")
    private Integer taskPassed;

    @Column(name = "termination_id")
    private Integer terminationId;

    @Column(name = "termination_audit_status")
    private Integer terminationAuditStatus;

    @Column(name = "internship_status_code")
    private String internshipStatusCode;

    @Column(name = "internship_status_name")
    private String internshipStatusName;
}
