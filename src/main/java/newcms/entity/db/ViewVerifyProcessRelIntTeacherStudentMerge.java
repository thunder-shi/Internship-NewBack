package newcms.entity.db;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

/**
 * 校内导师侧：师生审核综合视图（原 ViewVerifyProcessRelTeacherStudentMerge 按类型拆分）。
 */
@Getter
@Setter
@Entity
@Table(name = "view_verify_process_rel_int_teacher_student_merge")
public class ViewVerifyProcessRelIntTeacherStudentMerge extends BaseInfo {
    // 来自 main_verify_process
    private Integer relationId;
    private Integer processId;
    private Integer createUserId;
    private String verifyUserId;
    private Integer isAudit;
    private String reason;
    private String tableName;

    // 来自 rel_teacher_student
    private String name;
    private Integer teacherId;
    private Integer relTeaStuId;
    private Integer internshipId;
    private Integer relInternshipId;
    private String remarks;

    // 来自 main_internship
    private String internshipName;
    private String internshipPostName;

    // 用户姓名
    private String teacherName;
    private String createUserName;
    private String verifyUserName;
    private String studentName;
    private String studentAccount;
    private String jobCode;
    // 来自 view_rel_process_internship
    private Integer currentVerifyTypeId;
    private String currentVerifyTypeName;
    private Integer processTypeId;
    private Integer verifyTypeId;

    // 计算字段
    private String currentRoleName;
    private Boolean isAllVerified;
}
