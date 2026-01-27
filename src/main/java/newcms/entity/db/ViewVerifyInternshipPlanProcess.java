package newcms.entity.db;

import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import newcms.entity.base.BaseInfo;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public class ViewVerifyInternshipPlanProcess extends BaseInfo {
    private String processTypeName;
    private Integer relationId;
    private Integer createUserId;
    private String createUserName;
    private String verifyUserId;
    private Integer isAudit;
    private String reason;
    private String tableName;
    private Integer internshipId;
    private String internshipName;
    private Integer verifyTypeId;
    private String remarks;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String verifyFirstRoleName;
    private String verifySecondRoleName;
    private String verifyThirdRoleName;
    private String verifyFourthRoleName;
    private String verifyFifthRoleName;
    private Integer verifyFirstRoleId;
    private Integer verifySecondRoleId;
    private Integer verifyThirdRoleId;
    private Integer verifyFourthRoleId;
    private Integer verifyFifthRoleId;
}
