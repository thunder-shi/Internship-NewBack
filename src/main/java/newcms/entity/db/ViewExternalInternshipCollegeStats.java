package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 只读视图：本学院校外实习报名汇总（与 SQL view_external_internship_college_stats 对应）。
 */
@Getter
@Setter
@Entity
@Table(name = "view_external_internship_college_stats")
@IdClass(ViewExternalInternshipCollegeStatsId.class)
@Immutable
public class ViewExternalInternshipCollegeStats implements Serializable {

    @Id
    @Column(name = "internship_id", nullable = false)
    private Integer internshipId;

    @Id
    @Column(name = "department_id", nullable = false)
    private Integer departmentId;

    @Column(name = "internship_name")
    private String internshipName;

    @Column(name = "internship_create_time")
    private Date internshipCreateTime;

    @Column(name = "signup_student_count")
    private Integer signupStudentCount;

    @Column(name = "signup_teacher_count")
    private Integer signupTeacherCount;

    @Column(name = "post_signup_count")
    private Integer postSignupCount;

    /** MySQL SUM 返回 DECIMAL，JPA 常映射为 BigDecimal */
    @Column(name = "total_recruitment_headcount")
    private BigDecimal totalRecruitmentHeadcount;

    @Column(name = "pending_audit_post_count")
    private Integer pendingAuditPostCount;

    @Column(name = "student_with_post_selection_count")
    private Integer studentWithPostSelectionCount;
}
