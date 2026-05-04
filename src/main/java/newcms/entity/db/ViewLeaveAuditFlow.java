package newcms.entity.db;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

/**
 * 请假审核流向视图（按 MainVerifyProcess + BaseVerifyType 聚合）。
 */
@Getter
@Setter
@Entity(name = "view_leave_audit_flow")
public class ViewLeaveAuditFlow extends BaseInfo {

    private Integer leaveId;
    private Integer verifyProcessId;
    private Integer relationId;
    private Integer processId;
    private Integer createUserId;
    private String verifyUserId;
    private Integer isAudit;
    private String reason;
    private String tableName;

    private Integer currentVerifyTypeId;
    private Integer verifyTypeId;
    private String verifyTypeName;
    private Integer verifyTypeOrder;
    private Long nextVerifyLevel;
}
