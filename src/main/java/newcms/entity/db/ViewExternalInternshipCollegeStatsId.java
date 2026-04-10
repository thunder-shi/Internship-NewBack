package newcms.entity.db;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * 复合主键：校外实习项目 × 学院（与视图 view_external_internship_college_stats 一致）。
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
