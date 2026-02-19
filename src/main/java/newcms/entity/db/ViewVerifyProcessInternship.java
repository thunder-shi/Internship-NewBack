package newcms.entity.db;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity(name = "view_verify_process_Internship")
public class ViewVerifyProcessInternship extends BaseInfo {
    private Integer relationId;
    private Integer processId;
    private Integer createUserId;
    private String verifyUserId;
    private Integer isAudit;
    private String reason;
    private String tableName;
    private Integer internshipId;
    private Integer processTypeId;
    private Integer verifyTypeId;
    private String internshipCode;
    private String internshipName;
    private String internshipRemarks;
    private String internshipTypeName;
    private String intTypeName;
    private String universityName;
    private String createUserName;
    private String verifyUserName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String currentVerifyTypeName;
    private String majorIds;
    private String majorNames;
}
