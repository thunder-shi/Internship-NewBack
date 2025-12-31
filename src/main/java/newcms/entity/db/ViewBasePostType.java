package newcms.entity.db;

import newcms.entity.base.NameRemarkOrderInfo;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public class ViewBasePostType extends NameRemarkOrderInfo {
    private Integer companyId;

    private Integer salary;

    private String address;

    private String departmentName;

}
