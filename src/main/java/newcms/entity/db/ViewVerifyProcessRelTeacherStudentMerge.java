package newcms.entity.db;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

@Getter
@Setter
@Entity
public class ViewVerifyProcessRelTeacherStudentMerge extends BaseInfo {
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
    private Integer internshipId;
    private Integer relInternshipId;
    private String remarks;

    // 来自 main_internship
    private String internshipName;

    // 用户姓名
    private String teacherName;
    private String createUserName;
    private String verifyUserName;

    // 来自 view_rel_process_internship
    private Integer currentVerifyTypeId;
    private String currentVerifyTypeName;

    // 计算字段
    private String currentRoleName;
    private Boolean isAllVerified;
}
