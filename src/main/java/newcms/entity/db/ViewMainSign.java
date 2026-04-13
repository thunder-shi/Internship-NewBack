package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

/**
 * 实习打卡视图：main_sign + rel_stu_internship_post + view_base_user + main_internship_post。
 * <p>对应数据库视图 {@code view_main_sign}，列名需与视图定义一致。</p>
 * <p>未标注 Immutable，以便与 {@link newcms.repository.base.BaseDao} 的逻辑删除等更新共存；
 * 实际是否可写取决于库中该视图是否允许 UPDATE（通常查询为主，写操作宜走 {@link MainSign}）。</p>
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

    private String address;
    private Integer imgId;

    /** 外键，关联 rel_stu_internship_post.id */
    private Integer stuInternshipId;

    /** 视图列名为 {@code type} */
    @Column(name = "type")
    private Byte signType;

    private Integer studentId;

    /** 来自 main_internship_post.name */
    private String postName;

    /** 来自 view_base_user.NAME */
    private String studentName;
}
