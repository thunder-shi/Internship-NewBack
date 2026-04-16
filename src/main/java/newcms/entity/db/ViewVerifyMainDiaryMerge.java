package newcms.entity.db;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

import java.time.LocalDateTime;

/**
 * 实习日志审核综合视图（每日志取最新审核记录，含完整内容）
 * 对应 view_verify_main_diary_merge
 * 与其他条目 merge 视图结构对齐：含 is_all_verified、current_role_name
 */
@Getter
@Setter
@Entity(name = "view_verify_main_diary_merge")
public class ViewVerifyMainDiaryMerge extends BaseInfo {
    // 来自 main_verify_process
    private Integer relationId;             // 日志 ID（main_diary.id）
    private Integer createUserId;
    private String verifyUserId;
    private Integer isAudit;
    private String reason;
    private String tableName;               // = "MainDiary"

    // 审核人/提交人名（computed）
    private String verifyUserName;
    private String createUserName;

    // 来自 main_diary（审核配置）
    private Integer verifyTypeId;
    private Integer verifyFirstRoleId;
    private Integer verifySecondRoleId;
    private Integer verifyThirdRoleId;
    private Integer verifyFourthRoleId;
    private Integer verifyFifthRoleId;
    private Integer currentVerifyTypeId;
    private Boolean submit;
    private String title;                   // 日志标题
    private String content;                 // 日志正文
    private String remarks;                 // 老师批阅意见

    // 审核角色名（来自 sys_role）
    private String verifyFirstRoleName;
    private String verifySecondRoleName;
    private String verifyThirdRoleName;
    private String verifyFourthRoleName;
    private String verifyFifthRoleName;

    // 当前正在审核的角色名 + 是否全部通过（CASE WHEN / computed）
    private String currentRoleName;
    private Boolean isAllVerified;

    // 来自 main_diary_period（期次信息）
    private Integer periodId;
    private Integer periodIndex;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;

    // 来自 main_diary（日记关联标识）
    private Integer diaryRelationId;        // main_diary.relation_id
    private String diaryTableName;          // main_diary.table_name

    // 校外：岗位信息（diaryTableName = 'RelStuInternshipPost' 时有值）
    private String internshipPostCode;
    private String internshipPostName;
    private String internshipPostRemarks;
    private String postCompanyName;

    // 校内：题目信息（diaryTableName = 'RelTitleStudent' 时有值）
    private Integer titleId;
    private String titleName;
    private Integer teacherId;
    private String teacherName;

    // 通用
    private String internshipName;

    // 学生信息
    private Integer studentId;
    private String studentName;
    private Integer schoolId;
    private String studentPhone;
    private String studentDepartmentName;
    private String studentAccount;
    private String studentMajorName;
}
