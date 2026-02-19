package newcms.entity.db;

import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.persistence.Entity;
import newcms.entity.base.OrderInfo;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public class ViewRelProcessInternshipType extends OrderInfo {
    private Integer internshipTypeId;
    private Integer processTypeId;
    private Integer verifyTypeId;
    private String processTypeName;
    private String verifyTypeName;
    private String verifyTypeCode;
    private String internshipTypeName;  // 来自 view_base_internship_type.name
    private Integer verifyFirstRoleId;
    private Integer verifySecondRoleId;
    private Integer verifyThirdRoleId;
    private Integer verifyFourthRoleId;
    private Integer verifyFifthRoleId;
    private String verifyFirstRoleName;
    private String verifySecondRoleName;
    private String verifyThirdRoleName;
    private String verifyFourthRoleName;
    private String verifyFifthRoleName;
}

