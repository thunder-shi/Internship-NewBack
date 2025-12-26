package newcms.entity.db;

import newcms.entity.base.NameRemarkInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.persistence.Entity;
import java.util.Date;


@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public class ViewBaseUser extends NameRemarkInfo {
    private Boolean firstLogin;
    private String phone;
    private String account;
    private String password;
    private String sex;
    private String idCard;
    private Date birth;
    private String address;
    private String postalCode;
    private String JobId;
    private String email;
    //private Date registerTime;
    private String nickName;
    private Integer departmentId;
    private Integer schoolId;
    private String workId;
    private String themeColor;
    private String DepartmentName;
    private String JobName;
}
