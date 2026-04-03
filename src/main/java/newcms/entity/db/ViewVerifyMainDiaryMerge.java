package newcms.entity.db;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

/**
 * 实习日志审核综合视图（含完整日志内容）
 * 合并 main_verify_process + view_main_diary，isAllVerified = (isAudit = 1)
 */
@Getter
@Setter
@Entity(name = "view_verify_main_diary_merge")
public class ViewVerifyMainDiaryMerge extends BaseInfo {
    // 来自 main_verify_process
    private Integer relationId;
    private Integer processId;
    private Integer createUserId;
    private String verifyUserId;
    private Integer isAudit;
    private String reason;
    private String tableName;
    private Boolean isAllVerified;

    // 来自 view_main_diary（日记本体）
    private Integer stuInternshipPostId;   // 校外：非空
    private Integer relTitleStudentId;     // 校内：非空
    private Integer periodIndex;
    private String content;
    private String remark;

    // 校外：岗位信息（stuInternshipPostId 非空时有值）
    private String internshipPostCode;
    private String internshipPostName;
    private String internshipPostRemarks;
    private String postCompanyName;

    // 校内：题目信息（relTitleStudentId 非空时有值）
    private Integer titleId;
    private String titleName;
    private Integer teacherId;
    private String teacherName;

    // 通用
    private String internshipName;

    // 来自 view_main_diary（学生信息）
    private Integer studentId;
    private String studentName;
    private Integer schoolId;
    private String studentPhone;
    private String studentDepartmentName;
    private String studentAccount;
    private String studentMajorName;
}
