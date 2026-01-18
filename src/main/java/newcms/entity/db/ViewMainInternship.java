package newcms.entity.db;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.NameRemarkOrderInfo;

@Getter
@Setter
@Entity
public class ViewMainInternship extends NameRemarkOrderInfo {
    private Integer internshipTypeId;
    private String internshipTypeName;
    private String typeName;
    private Integer creatorId;
    private String creatorName;
    private String creatorRoleName;
    private String cron;
    private Integer studentNum;
    private String universityName;

    // 审核相关字段
    /** 当前审核状态：-1(保存未提交)、0(待审核)、1(审核通过)、2(审核不通过)、3(审核退回) */
    private Integer isAudit;

    /** 关联的流程实例ID */
    private Integer relProcessInternshipId;

    /** 最新的审核记录ID */
    private Integer mainVerifyProcessId;

    /** 当前待审核角色名称（如"院系负责人"），仅当 isAudit=0 时有值 */
    private String currentVerifyRole;

    /** 审核要求ID（几级审核），来自 RelProcessInternship */
    private Integer verifyTypeId;

    /** 第一级审核角色名称 */
    private String verifyFirstRole;

    /** 第二级审核角色名称 */
    private String verifySecondRole;

    /** 第三级审核角色名称 */
    private String verifyThirdRole;

    /** 第四级审核角色名称 */
    private String verifyFourthRole;

    /** 第五级审核角色名称 */
    private String verifyFifthRole;
}
