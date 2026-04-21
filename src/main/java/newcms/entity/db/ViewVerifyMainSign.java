package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

/**
 * 实习打卡审核视图：{@code main_verify_process} 与 {@code view_main_sign} 按 relation_id 关联，
 * 仅 {@code table_name = 'MainSign'} 且未逻辑删除的审核记录（一条审核一行）。
 * <p>列与数据库视图 {@code view_verify_main_sign} 定义一致。</p>
 */
@Getter
@Setter
@Entity(name = "view_verify_main_sign")
public class ViewVerifyMainSign extends BaseInfo {

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
