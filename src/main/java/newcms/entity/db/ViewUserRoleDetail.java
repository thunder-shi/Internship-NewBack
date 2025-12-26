package newcms.entity.db;

import newcms.entity.base.BaseInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.persistence.Entity;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public class ViewUserRoleDetail extends BaseInfo {
    private Integer roleId;
    private Integer userId;
    private Integer isAudit;
    private String roleName;
    private String roleCode;
    private String userName;
    private String userPhone;
    private String departmentName;
}
