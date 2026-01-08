package newcms.entity.db;

import newcms.entity.base.BaseInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.persistence.Entity;
import newcms.entity.base.NameRemarkOrderInfo;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public class ViewRelProcessInternshipType extends NameRemarkOrderInfo {
    private Integer internshipTypeId;
    private Integer processTypeId;
    private Integer verifyTypeId;
    private String processTypeName;
    private String verifyTypeName;
    private String universityName;
    private String typeName;
    private String internshipTypeName;
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

