package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.NameRemarkInfo;

@Getter
@Setter
@Entity
@Table(name = "view_rel_title_student")
public class ViewRelTitleStudent extends NameRemarkInfo {
    @Column(name = "current_verify_type_id")
    private Integer currentVerifyTypeId;

    @Column(name = "stu_id")
    private Integer stuId;

    @Column(name = "title_id")
    private Integer titleId;

    @Column(name = "student_name")
    private String studentName;

    private String name;

    private String remarks;

    @Column(name = "internship_id")
    private Integer internshipId;

    @Column(name = "teacher_id")
    private Integer teacherId;

    @Column(name = "is_limit")
    private Integer isLimit;

    @Column(name = "teacher_name")
    private String teacherName;

    @Column(name = "topic_Reasons")
    private String topicReasons;
}
