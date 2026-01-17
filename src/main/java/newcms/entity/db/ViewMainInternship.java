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
}
