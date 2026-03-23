package newcms.entity.db;

import java.time.LocalDateTime;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

@Getter
@Setter
@Entity
public class ViewVerifyProcessRelIntershipUserMerge extends BaseInfo {
    // 来自 main_verify_process
    private Integer createUserId;
    private Integer isAudit;
    private String reason;
    private Integer relationId;
    private String tableName;
    private String verifyUserId;
    private Integer processId;

    // 来自 rel_intership_user
    private Integer internshipId;
    private Integer userId;
    private String code;
    private String name;
    private String remarks;
    private Integer relIntershipUserId;

    // 来自 view_base_user
    private String userName;
    private String jobName;
    private Integer jobId;
    private String phone;

    // 来自 main_internship
    private String internshipName;
    private Integer studentNum;

    // 来自 view_rel_process_internship
    private String processTypeCode;
    private String processTypeName;
    private String internshipTypeName;
    private Integer verifyTypeId;
    private LocalDateTime endTime;
    private LocalDateTime startTime;

    // 来自 rel_intership_user
    private Integer currentVerifyTypeId;

    // 用户姓名
    private String createUserName;
    private String verifyUserName;
    private String verifyTypeName;

    // 计算字段
    private String currentRoleName;
    private Boolean isAllVerified;
}
