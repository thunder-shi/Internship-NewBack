package newcms.entity.db;

import newcms.entity.base.BaseInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.persistence.Entity;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public class ViewRelProcessInternshipType extends BaseInfo {
    private Integer internshipTypeId;
    private Integer processTypeId;
    private Integer verifyTypeId;
    private String processTypeName;
    private String verifyTypeName;
    private String universityName;
    private String typeName;
    private String internshipTypeName;
}

