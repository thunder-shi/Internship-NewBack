package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseTreeInfo;

@Getter
@Setter
@Entity
public class ViewBaseDepartment extends BaseTreeInfo<ViewBaseDepartment> {
    private String departmentAdd;
    private String departmentPostalCode;
    private String departmentPhone;
    private String departmentFax;
    private String departmentEmail;
    private Integer areaId;
    private Integer typeId;
    private Integer majorId;
    private Integer startYear;
    private String majorName;
    private String areaName;
    private String typeName;
}
