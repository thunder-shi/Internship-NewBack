package newcms.entity.db;

import newcms.entity.base.NameRemarkInfo;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public class ViewBaseInternshipType extends NameRemarkInfo {
    private Integer universityId;
    private Integer intTypeId;
    private String universityName;
    private String typeName;
}
