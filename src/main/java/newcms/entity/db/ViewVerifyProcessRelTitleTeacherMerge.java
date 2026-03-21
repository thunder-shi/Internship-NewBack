package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

@Getter
@Setter
@Entity
public class ViewVerifyProcessRelTitleTeacherMerge extends BaseInfo {
    // 来自 main_verify_process
    private Integer createUserId;
    private Integer isAudit;
    private String reason;
    private Integer relationId;
    private String tableName;
    private String verifyUserId;
    private Integer processId;

    // 来自 rel_title_teacher
    private String name;
    private String remarks;
    private Integer internshipId;
    private Integer teacherId;
    private Integer currentVerifyTypeId;

    // 关联显示字段
    private String createUserName;
    private String internshipName;
    private String teacherName;
    private String verifyUserName;
    private String currentVerifyTypeName;

    @Column(name = "m_internship_id")
    private Integer mInternshipId;

    // 计算字段
    private String currentRoleName;
    private Boolean isAllVerified;
}
