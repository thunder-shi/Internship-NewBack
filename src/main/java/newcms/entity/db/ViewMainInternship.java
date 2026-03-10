package newcms.entity.db;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.NameRemarkInfo;

@Getter
@Setter
@Entity
public class ViewMainInternship extends NameRemarkInfo {
    private Integer internshipTypeId;
    private String internshipTypeName;
    private String intTypeName;
    private Integer creatorId;
    private String cron;
    private Integer studentNum;
    private String universityName;
    private String majorIds;
    private String majorNames;
    private Integer currentVerifyTypeId;
}
