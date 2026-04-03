package newcms.entity.db;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

@Getter
@Setter
@Entity(name = "view_main_diary")
public class ViewMainDiary extends BaseInfo {
    // 来自 main_diary
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
    private Integer studentId;

    // 来自 view_base_user（student 别名）
    private String studentName;
    private Integer schoolId;
    private String studentPhone;
    private String studentDepartmentName;
    private String studentAccount;

    // 来自 base_major
    private String studentMajorName;
}
