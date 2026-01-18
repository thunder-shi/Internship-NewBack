package newcms.entity.db;

import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import newcms.entity.base.NameRemarkOrderInfo;

import java.time.LocalDateTime;

/**
 * 实习项目审核情况视图
 * 用于展示实习项目的审核进度历史
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

    /** 审核记录创建人名称 */
    private String createUserName;

    /** 已审核时的审核人名称（isAudit为1/2/3时有值） */
    private String verifyUserName;


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

    // 来自 view_main_internship
    private String cron;
    private Integer studentNum;
    private String creatorName;
}
