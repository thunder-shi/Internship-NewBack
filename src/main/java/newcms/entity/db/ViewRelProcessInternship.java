package newcms.entity.db;

import jakarta.persistence.Entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import newcms.entity.base.BaseInfo;


@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public class ViewRelProcessInternship extends BaseInfo {
    private Integer internshipId;
    private Integer processTypeId;
    private Integer verifyTypeId;
    private String processTypeName;
    private String verifyTypeName;
    private String universityName;
    private String typeName;
    private String internshipTypeName;
    private Integer theOrder;
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
