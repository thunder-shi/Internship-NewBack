package newcms.entity.db;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

import java.time.LocalDateTime;

/**
 * 导师/审核员待办视图（MainLeave 当前待审记录）。
 */
@Getter
@Setter
@Entity(name = "view_auditor_todo_list")
public class ViewAuditorTodoList extends BaseInfo {

    private Integer leaveId;
    private Integer verifyProcessId;
    private Integer createUserId;
    private String verifyUserId;
    private Integer isAudit;
    private String reason;
    private String tableName;

    private Integer stuInternshipId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String remarks;
    private Integer currentVerifyTypeId;

    private Integer studentId;
    private String studentName;
    private String studentAccount;
    private String internshipMode;

    private Integer teacherId;
    private String teacherName;
}
