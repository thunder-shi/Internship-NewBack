package newcms.entity.db;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * 复合主键：校外实习项目 × 所属学院（department_id = base_internship_type.university_id）。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ViewExternalInternshipCollegeStatsId implements Serializable {
    private Integer internshipId;
    private Integer departmentId;
}
