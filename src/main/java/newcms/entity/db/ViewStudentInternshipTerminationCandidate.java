package newcms.entity.db;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

@Getter
@Setter
@Entity
public class ViewStudentInternshipTerminationCandidate extends BaseInfo {
    private Integer internshipId;
    private String internshipName;
    private Integer studentId;
    private String studentName;
    private String studentAccount;
    private Integer departmentId;
    private String departmentName;
    private String relationTable;
    private Integer relationId;
    private String internshipMode;
    private String postName;
    private String titleName;
    private Integer teacherId;
    private String teacherName;
    private Integer internshipStatus;
    private Integer terminationId;
}
