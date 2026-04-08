package newcms.entity.db;

import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import newcms.entity.base.NameRemarkInfo;

/**
 * 教师与学生实习关联视图
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public class ViewRelTeacherStudent extends NameRemarkInfo {

    /** 外键，关联表1（教师） */
    private Integer teacherId;

    /** 外键，关联表16（实习） */
    private Integer internshipId;

    /** 外键，关联表11（入项记录 rel_intership_user.id） */
    private Integer relInternshipId;

    /** 当前处在的审核级别 */
    private Integer currentVerifyTypeId;

    /** 教师名称（来自 view_base_user） */
    private String teacherName;

    /** 教师职位编码，SCHOOL_TEACHER=校内导师，COMPANY_TUTOR=企业导师 */
    private String jobCode;

    /** 实习项目名称（来自 main_internship） */
    private String internshipName;

    /** 学生用户ID（来自 rel_intership_user.user_id） */
    private Integer studentId;

    /** 学生姓名（来自 view_base_user） */
    private String studentName;

    /** 学生学号（来自 view_base_user.account） */
    private String studentAccount;

    /** 学生所在班级/部门名称（来自 view_base_user.department_name） */
    private String studentDepartmentName;

    /**
     * 审核状态：-1 待提交、0 待审核、1 审核通过、2 审核未通过、3 审核退回。
     * 来自 main_verify_process 最新记录，若无审核记录则为 null。
     */
    private Integer isAudit;

    /** 对应的审核记录主键ID（MainVerifyProcess.id） */
    private Integer verifyProcessId;
}
