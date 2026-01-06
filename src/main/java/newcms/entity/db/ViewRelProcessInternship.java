package newcms.entity.db;

import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import newcms.entity.base.BaseInfo;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public class ViewRelProcessInternship extends BaseInfo {
    private Integer internshipTypeId;
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
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
