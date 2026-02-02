package newcms.entity.db;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

@Getter
@Setter
@Entity
public class ViewMainVerifyProcess extends BaseInfo {
    private Integer relationId;
    private Integer createUserId;
    private String verifyUserId;
    private Integer isAudit;
    private String reason;
    private String tableName;
    private Integer internshipId;
    private Integer processTypeId;
    private Integer verifyTypeId;
    private String internshipCode;
    private String internshipName;
    private String internshipRemarks;
    private String internshipTypeName;
    private String intTypeName;
    private String universityName;
}
