package newcms.entity.db;

import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import newcms.entity.base.NameRemarkOrderInfo;

import java.time.LocalDateTime;

/**
 * 实习项目审核情况视图
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public class ViewInternshipVerifyProcess extends NameRemarkOrderInfo {
    // 来自 main_verify_process
    private Integer createUserId;
    private Integer isAudit;
    private String reason;
    private Integer relationId;
    private String tableName;
    private String verifyUserId;

    // 来自 base_user
    private String createUserName;

    // 来自 view_rel_process_internship
    private Integer internshipId;
    private Integer processTypeId;
    private Integer verifyTypeId;
    private Integer verifyFirstRoleId;
    private Integer verifySecondRoleId;
    private Integer verifyThirdRoleId;
    private Integer verifyFourthRoleId;
    private Integer verifyFifthRoleId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String verifyTypeName;
    private String verifyFirstRoleName;
    private String verifySecondRoleName;
    private String verifyThirdRoleName;
    private String verifyFourthRoleName;
    private String verifyFifthRoleName;
    private String processTypeName;
    private String internshipName;
    private Integer internshipTypeId;
    private String internshipTypeName;
    private String universityName;
    private String typeName;
}
