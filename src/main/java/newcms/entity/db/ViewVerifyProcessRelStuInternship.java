package newcms.entity.db;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;
import java.time.LocalDateTime;

/**
 * 审核流程-学生实习岗位选择视图
 * 包含审核流程信息、实习项目信息、学生信息和岗位信息
 */
@Getter
@Setter
@Entity
public class ViewVerifyProcessRelStuInternship extends BaseInfo {
    // 来自 main_verify_process 表的字段
    private Integer relationId;
    private Integer processId;
    private Integer createUserId;
    private String verifyUserId;
    private Integer isAudit;
    private String reason;
    private String tableName;

    // 来自 view_rel_process_internship 视图的字段
    private Integer internshipId;
    private Integer processTypeId;
    private Integer verifyTypeId;
    private String internshipCode;
    private String internshipRemarks;
    private String internshipName;
    private String internshipTypeName;
    private String intTypeName;
    private String universityName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String currentVerifyTypeName;
    private String majorIds;
    private String majorNames;

    // 来自 base_user (createbaseuser) 的字段
    private String createUserName;

    // 计算字段：通过子查询获取审核用户姓名
    private String verifyUserName;

    // 来自 view_rel_stu_internship 视图的字段
    private Integer internshipPostId;
    private String internshipPostCode;
    private String internshipPostName;
    private String internshipPostRemarks;
    private Integer allPersonNum;
    private Integer nowPersonNum;
    private String companyName;
    private Integer companyId;
    private String studentName;
    private String studentId;
    private Integer departmentId;
    private String departmentName;
}
