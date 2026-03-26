package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import newcms.entity.base.NameRemarkInfo;

/**
 * 教师题目关联视图
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public class ViewRelTitleTeacher extends NameRemarkInfo {

    private Integer internshipId;
    private Integer teacherId;

    /**
     * 题目名称
     */
    private String name;
    /**
     * 题目详情
     */
    private String remarks;
    /**
     * 实习项目名称（main_internship.name）
     */
    @Column(name = "internship_name")
    private String internshipName;

    /**
     * 教师用户ID（base_user.ID）
     */
    @Column(name = "user_id")
    private Integer userId;

    /**
     * 教师姓名（base_user.name）
     */
    @Column(name = "teacher_name")
    private String teacherName;

    /**
     * 当前审核类型ID
     */
    private Integer currentVerifyTypeId;

    /**
     * 当前审核级别名称（base_verify_type.name，与 rel_title_teacher.current_verify_type_id 对应）
     */
    @Column(name = "current_verify_type_name")
    private String currentVerifyTypeName;

    /**
     * 审核状态：-1 待提交、0 待审核、1 审核通过、2 审核不通过、3 审核退回。
     * 来自 MainVerifyProcess（table_name 须为 RelTitleTeacher）；若已全部审完则可为 1。
     */
    @Column(name = "is_audit")
    private Integer isAudit;

    /**
     * 最新一条 MainVerifyProcess.id（同 relation_id、table_name=RelTitleTeacher）。
     */
    @Column(name = "verify_process_id")
    private Integer verifyProcessId;

    @Column(name = "is_limit")
    private Integer isLimit;
}
