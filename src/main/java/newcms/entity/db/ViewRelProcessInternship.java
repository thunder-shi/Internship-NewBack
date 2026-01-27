package newcms.entity.db;

import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.EqualsAndHashCode;
import newcms.entity.base.OrderInfo;

import java.time.LocalDateTime;
import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public class ViewRelProcessInternship extends OrderInfo {
    private Integer internshipId;
    private Integer processTypeId;
    private Integer verifyTypeId;
    private String processTypeName;
    private String verifyTypeName;
    private String internshipName;  // 来自 view_main_internship.name
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
    private String remarks;
}
