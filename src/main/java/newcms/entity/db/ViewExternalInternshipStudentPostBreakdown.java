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

/**
 * 只读视图：校外实习入项学生选岗明细（一人一行）。
 * <p>对应 SQL {@code view_external_internship_student_post_breakdown}。
 * {@code selectionStatus}：{@code notSelected} / {@code selectedPendingAudit} / {@code postApproved}；
 * 接口「已报名 selected」= 后两者。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "view_external_internship_student_post_breakdown")
@IdClass(ViewExternalInternshipStudentPostBreakdownId.class)
@Immutable
public class ViewExternalInternshipStudentPostBreakdown implements Serializable {

    @Id
    @Column(name = "internship_id", nullable = false)
    private Integer internshipId;

    @Id
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "internship_name")
    private String internshipName;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "account")
    private String account;

    @Column(name = "department_id")
    private Integer departmentId;

    @Column(name = "department_name")
    private String departmentName;

    @Column(name = "selection_status")
    private String selectionStatus;

    @Column(name = "verify_process_id")
    private Integer verifyProcessId;

    @Column(name = "is_audit")
    private Integer isAudit;

    @Column(name = "internship_post_id")
    private Integer internshipPostId;

    @Column(name = "internship_post_name")
    private String internshipPostName;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "rel_stu_internship_post_id")
    private Integer relStuInternshipPostId;
}
