package newcms.entity.db;


import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.NameRemarkOrderInfo;

@Getter
@Setter
@Entity
public class ViewMainInternshipPost extends NameRemarkOrderInfo {
    private Integer basePostTypeId;
    private Integer allPersonNum;
    private Integer nowPersonNum;
    private Integer mainInternshipId;

    private String basePostTypeName;
    private String mainInternshipName;

}
