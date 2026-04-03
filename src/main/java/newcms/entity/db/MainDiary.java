package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

/**
 * 实习日志表
 */
@Getter
@Setter
@Entity
public class MainDiary extends BaseInfo {
    @Column(columnDefinition = "int unsigned comment '外键，关联表rel_stu_internship_post（校外实习）'")
    private Integer stuInternshipPostId;

    @Column(columnDefinition = "int unsigned comment '外键，关联表rel_title_student（校内实习）'")
    private Integer relTitleStudentId;

    @Column(columnDefinition = "int unsigned not null comment '所属期数（第几期，1-based）'")
    private Integer periodIndex;

    @Column(columnDefinition = "text comment '日记文字内容'")
    private String content;

    @Column(columnDefinition = "varchar(500) comment '老师批阅意见'")
    private String remark;
}
