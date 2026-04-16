package newcms.entity.db;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

import java.time.LocalDateTime;

/**
 * 实习日志视图
 * 合并 main_diary + main_diary_period + 关联信息（岗位/题目/学生）
 */
@Getter
@Setter
@Entity(name = "view_main_diary")
public class ViewMainDiary extends BaseInfo {
    // 来自 main_diary（NameRemarkInfo）
    private String code;
    private String name;
    private String remarks;         // 老师批阅意见（写回 main_diary.remarks）

    // 来自 main_diary（新字段）
    private Integer relationId;     // 关联 rel_stu_internship_post 或 rel_title_student 的 id
    private String tableName;       // "RelStuInternshipPost" 或 "RelTitleStudent"
    private Integer periodId;       // 关联 main_diary_period.id
    private String title;           // 日志标题
    private String content;         // 日志正文
    private Boolean submit;         // false=草稿，true=已提交
    private Integer verifyTypeId;
    private Integer verifyFirstRoleId;
    private Integer verifySecondRoleId;
    private Integer verifyThirdRoleId;
    private Integer verifyFourthRoleId;
    private Integer verifyFifthRoleId;
    private Integer currentVerifyTypeId;
    // 各级审核角色名（来自 sys_role）
    private String verifyFirstRoleName;
    private String verifySecondRoleName;
    private String verifyThirdRoleName;
    private String verifyFourthRoleName;
    private String verifyFifthRoleName;

    // 来自 main_diary_period（期次信息）
    private Integer periodIndex;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;

    // 校外：岗位信息（tableName = 'RelStuInternshipPost' 时有值）
    private String internshipPostCode;
    private String internshipPostName;
    private String internshipPostRemarks;
    private String postCompanyName;

    // 校内：题目信息（tableName = 'RelTitleStudent' 时有值）
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
