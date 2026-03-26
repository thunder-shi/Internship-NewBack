package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import newcms.entity.base.NameRemarkInfo;

/**
 * 教师题目与学生选择关联视图
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "view_rel_title_teacher_student")
public class ViewRelTitleTeacherStudent extends NameRemarkInfo {

    @Column(name = "internship_id")
    private Integer internshipId;

    @Column(name = "teacher_id")
    private Integer teacherId;

    @Column(name = "teacher_name")
    private String teacherName;

    @Column(name = "is_limit")
    private Integer isLimit;

    @Column(name = "rel_title_student_id")
    private Integer relTitleStudentId;

    @Column(name = "current_verify_type_id")
    private Integer currentVerifyTypeId;

    @Column(name = "stu_id")
    private Integer stuId;

    @Column(name = "student_name")
    private String studentName;

    @Column(name = "title_id")
    private Integer titleId;

    @Column(name = "is_audit")
    private Integer isAudit;
}
