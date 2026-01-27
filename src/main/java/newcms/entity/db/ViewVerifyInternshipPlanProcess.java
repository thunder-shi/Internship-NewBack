package newcms.entity.db;

import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import newcms.entity.base.BaseInfo;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public class ViewVerifyInternshipPlanProcess extends BaseInfo {
    private Integer relationId;
    private Integer createUserId;
    private String createUserName;
    private String verifyUserId;
    private String verifyUserName;
    private Integer isAudit;
    private String reason;
    private String tableName;
    private Integer internshipId;
    private String internshipName;
    private Integer verifyTypeId;
}
