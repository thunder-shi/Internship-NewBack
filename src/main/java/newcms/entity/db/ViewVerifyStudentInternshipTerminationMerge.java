package newcms.entity.db;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

import java.util.Date;

@Getter
@Setter
@Entity
public class ViewVerifyStudentInternshipTerminationMerge extends BaseInfo {
    private Integer verifyProcessId;
    private Integer terminationId;
    private Integer createUserId;
    private String createUserName;
    private String verifyUserId;
    private String verifyUserName;
    private Integer isAudit;
    private String auditReason;
    private Integer processId;
    private Integer internshipId;
    private String internshipName;
    private Integer studentId;
    private String studentName;
    private String studentAccount;
    private Integer departmentId;
    private String departmentName;
    private String relationTable;
    private Integer relationId;
    private String internshipMode;
    private String postName;
    private String titleName;
    private Integer teacherId;
    private String teacherName;
    private Date terminateDate;
    private String reasonType;
    private String reason;
    private String attachmentIds;
    private Integer status;
    private Integer currentVerifyTypeId;
    private String currentVerifyTypeName;
    private String currentRoleName;
    private Boolean isAllVerified;
}
