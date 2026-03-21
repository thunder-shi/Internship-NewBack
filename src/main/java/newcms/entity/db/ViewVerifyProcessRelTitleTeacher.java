package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import newcms.entity.base.NameRemarkInfo;


@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public class ViewVerifyProcessRelTitleTeacher extends NameRemarkInfo {
    private Integer createUserId;
    private Integer isAudit;
    private String reason;
    private Integer relationId;
    private String tableName;
    private String verifyUserId;
    private Integer processId;
    private String createUserName;

    private Integer internshipId;
    private Integer teacherId;
    private String internshipName;
    private String teacherName;
    private String verifyUserName;
    private Integer currentVerifyTypeId;

    @Column(name = "m_internship_id")
    private Integer mInternshipId;

    private String currentVerifyTypeName;
}
