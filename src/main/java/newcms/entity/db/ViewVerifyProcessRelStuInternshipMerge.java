package newcms.entity.db;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
public class ViewVerifyProcessRelStuInternshipMerge extends BaseInfo {
    // 来自 main_verify_process
    private Integer relationId;
    private Integer processId;
    private Integer createUserId;
    private String verifyUserId;
    private Integer isAudit;
    private String reason;
    private String tableName;

    // 来自 view_rel_process_internship
    private Integer internshipId;
    private Integer processTypeId;
    private Integer verifyTypeId;
    private String internshipCode;
    private String internshipRemarks;
    private String internshipName;
    private String internshipTypeName;
    private String intTypeName;
    private String universityName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String majorIds;
    private String majorNames;
    private String processTypeCode;
    private Integer currentVerifyTypeId;

    // 来自 base_user
    private String createUserName;
    private String verifyUserName;

    // 来自 view_rel_stu_internship
    private Integer internshipPostId;
    private String internshipPostCode;
    private String internshipPostName;
    private String internshipPostRemarks;
    private Integer allPersonNum;
    private Integer nowPersonNum;
    private String companyName;
    private Integer companyId;
    private String studentName;
    private String studentId;
    private Integer departmentId;
    private String departmentName;

    // 计算字段
    private String currentRoleName;
    private Boolean isAllVerified;
}
