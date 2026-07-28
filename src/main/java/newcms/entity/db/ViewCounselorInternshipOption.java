package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

/**
 * 只读视图：辅导员可见实习项目选项。
 */
@Getter
@Setter
@Entity
@Table(name = "view_counselor_internship_option")
public class ViewCounselorInternshipOption extends BaseInfo {

    @Column(name = "counselor_id")
    private Integer counselorId;

    @Column(name = "counselor_name")
    private String counselorName;

    @Column(name = "internship_id")
    private Integer internshipId;

    @Column(name = "internship_name")
    private String internshipName;
}
