package newcms.entity.db;
import java.time.LocalDateTime;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import newcms.entity.base.NameRemarkInfo;

/**
 * 实习与用户关联视图
 * 包含实习项目、教师职务等信息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public class ViewRelIntershipUser extends NameRemarkInfo {

    /**
     * 外键，关联表16（实习）
     */
    private Integer internshipId;

    /**
     * 教师职务名称
     */
    private String jobName;
    private Integer jobId;
    /**
     * 教师姓名
     */
    private String userName;

    /**
     * 实习项目名称
     */
    private String internshipName;

    private Integer userId;
    private String tableName;
    private Integer isAudit;
    private String reason;
    private String phone;
    private String currentVerifyTypeId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private String processTypeCode;
    // private Integer verifyProcessId;

    private String verifyUserId;
    private Integer relationId;
    private Integer processId;

    private Integer relIntershipUserId;

  
    private Integer verifyTypeId;

  
    private Integer verifyFirstRoleId ;

 


    private Integer verifyThirdRoleId ;

 
    private Integer verifyFourthRoleId ;


    private Integer verifyFifthRoleId;
    // private Integer processTypeId;

    // private Integer verifyTypeId;

    // private Integer currentVerifyTypeId;

    // private LocalDateTime startTime;

    // private LocalDateTime endTime;
    // private String verifyTypeName;

    // private String isAudit;
}

