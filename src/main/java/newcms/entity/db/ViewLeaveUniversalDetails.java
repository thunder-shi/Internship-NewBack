package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

import java.time.LocalDateTime;

/**
 * 请假业务全量视图（抹平校内/校外差异）。
 */
@Getter
@Setter
@Entity(name = "view_leave_universal_details")
public class ViewLeaveUniversalDetails extends BaseInfo {

    private Integer leaveId;
    private Integer stuInternshipId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String remarks;

    private Integer studentId;
    private String studentName;
    private String studentAccount;

    /** EXTERNAL / INTERNAL */
    private String internshipMode;

    private Integer teacherId;
    private String teacherName;

    @Column(name = "relation_table")
    private String relationTable;
}
