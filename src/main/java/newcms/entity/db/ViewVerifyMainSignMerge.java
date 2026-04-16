package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

/**
 * 实习打卡审核综合视图（每条打卡取最新审核记录）
 * 对应 view_verify_main_sign_merge（与 view_verify_main_diary_merge 列结构对齐）
 */
@Getter
@Setter
@Entity(name = "view_verify_main_sign_merge")
public class ViewVerifyMainSignMerge extends BaseInfo {
    private Integer relationId;
    private Integer createUserId;
    private String verifyUserId;
    private Integer isAudit;
    private String reason;
    private String tableName;

    private String verifyUserName;
    private String createUserName;

    private Integer verifyTypeId;
    private Integer verifyFirstRoleId;
    private Integer verifySecondRoleId;
    private Integer verifyThirdRoleId;
    private Integer verifyFourthRoleId;
    private Integer verifyFifthRoleId;
    private Integer currentVerifyTypeId;
    private Boolean submit;
    private String content;
    private String remarks;

    private String verifyFirstRoleName;
    private String verifySecondRoleName;
    private String verifyThirdRoleName;
    private String verifyFourthRoleName;
    private String verifyFifthRoleName;

    private String currentRoleName;
    private Boolean isAllVerified;

    private String internshipPostCode;
    private String internshipPostName;
    private String internshipPostRemarks;
    private String postCompanyName;

    private String internshipName;

    private Integer studentId;
    private String studentName;
    private Integer schoolId;
    private String studentPhone;
    private String studentDepartmentName;
    private String studentAccount;
    private String studentMajorName;

    private String address;
    private Integer imgId;
    private Integer stuInternshipId;
    @Column(name = "type")
    private Byte signType;
}
