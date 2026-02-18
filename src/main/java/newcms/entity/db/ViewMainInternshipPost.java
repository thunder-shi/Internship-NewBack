package newcms.entity.db;


import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.NameRemarkInfo;

@Getter
@Setter
@Entity
public class ViewMainInternshipPost extends NameRemarkInfo {
    private Integer postTypeId;
    private Integer allPersonNum;
    private Integer nowPersonNum;
    private Integer internshipId;

    private String postTypeName;
    private String internshipName;
    private String companyName;

}
