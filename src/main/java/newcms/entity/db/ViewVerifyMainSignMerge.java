package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

/**
 * 实习打卡审核综合视图：每条打卡关联 main_verify_process 中该 relation下 id 最大的一条（最新审核）。
 * <p>列与数据库视图 {@code view_verify_main_sign_merge} 定义一致。</p>
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

    /** 来自 view_main_sign.post_name */
    @Column(name = "internship_post_name")
    private String internshipPostName;

    private Integer studentId;
    private String studentName;
    private String studentAccount;

    private String address;
    private Integer imgId;

    @Column(name = "type")
    private Byte signType;

    private Integer stuInternshipId;

    private String remarks;
    private Integer currentVerifyTypeId;
}
