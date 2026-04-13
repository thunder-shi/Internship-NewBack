package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

/**
 * 实习打卡审核视图（列表用）
 * 对应 view_verify_main_sign（与 view_verify_main_diary 列结构对齐；打卡扩展 address、img、type）
 */
@Getter
@Setter
@Entity(name = "view_verify_main_sign")
public class ViewVerifyMainSign extends BaseInfo {
    // 来自 main_verify_process
    private Integer relationId;             // main_sign.id
    private Integer createUserId;
    private String verifyUserId;
    private Integer isAudit;
    private String reason;
    private String tableName;               // = "MainSign"

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
    private String remarks;

    private String verifyFirstRoleName;
    private String verifySecondRoleName;
    private String verifyThirdRoleName;
    private String verifyFourthRoleName;
    private String verifyFifthRoleName;

    private String internshipPostName;
    private String postCompanyName;

    private String internshipName;

    private Integer studentId;
    private String studentName;
    private Integer schoolId;
    private String studentDepartmentName;

    // 来自 main_sign / view_main_sign
    private String address;
    private Integer imgId;

    @Column(name = "type")
    private Byte signType;
}
