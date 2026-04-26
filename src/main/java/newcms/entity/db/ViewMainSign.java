package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

/**
 * 实习打卡视图：{@code main_sign} 关联 {@code rel_stu_internship_post}、{@code view_base_user}、{@code main_internship_post}。
 * <p>列与数据库视图 {@code view_main_sign} 定义一致。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "view_main_sign")
public class ViewMainSign extends BaseInfo {

    private Integer verifyTypeId;
    private Integer verifyFirstRoleId;
    private Integer verifySecondRoleId;
    private Integer verifyThirdRoleId;
    private Integer verifyFourthRoleId;
    private Integer verifyFifthRoleId;
    private Integer internshipPostId;
    private String address;
    private Integer imgId;
    private Integer stuInternshipId;

    @Column(name = "type")
    private Byte signType;

    private Integer studentId;

    /** 来自 main_internship_post.name */
    @Column(name = "post_name")
    private String postName;

    /** 来自 view_base_user.NAME */
    @Column(name = "student_name")
    private String studentName;

    /** 来自 view_base_user.ACCOUNT */
    @Column(name = "student_account")
    private String studentAccount;

    private String remarks;
    private Integer currentVerifyTypeId;
}
