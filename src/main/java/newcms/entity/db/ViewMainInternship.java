package newcms.entity.db;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.NameRemarkOrderInfo;

import java.time.LocalDate;

@Getter
@Setter
@Entity
public class ViewMainInternship extends NameRemarkOrderInfo {
    private Integer internshipTypeId;
    private String internshipTypeName;
    private String typeName;
    private Integer creatorId;
    private String creatorName;
    private String cron;
    private Integer studentNum;
    private String universityName;
}
