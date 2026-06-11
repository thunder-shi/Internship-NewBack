package newcms.repository.db;

import newcms.entity.db.ViewExternalInternshipCollegeStats;
import newcms.entity.db.ViewExternalInternshipCollegeStatsId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 只读视图 DAO（无 BaseDao：复合主键且视图无 isDeleted）。
 */
@Repository
public interface ViewExternalInternshipCollegeStatsDao extends
        JpaRepository<ViewExternalInternshipCollegeStats, ViewExternalInternshipCollegeStatsId>,
        JpaSpecificationExecutor<ViewExternalInternshipCollegeStats> {

    /**
     * 按「部门及其全部子部门」汇总：同一实习在视图中多行（每部门一行），此处按 internship_id 聚合。
     * 人数类 SUM；岗位/待审核/计划人数为实习维度，多行相同，取 MAX。
     * <p>仅返回 {@code base_internship_type.university_id} 属于 {@code owningCollegeIds} 的项目，
     * 避免跨学院项目泄漏到其他学院统计页。</p>
     */
    @Query(
            value = "SELECT v.internship_id, MAX(v.internship_name), MAX(v.internship_create_time), "
                    + "COALESCE(SUM(v.signup_student_count), 0), COALESCE(SUM(v.signup_teacher_count), 0), "
                    + "MAX(v.post_signup_count), MAX(v.total_recruitment_headcount), MAX(v.pending_audit_post_count), "
                    + "COALESCE(SUM(v.student_with_post_selection_count), 0) "
                    + "FROM view_external_internship_college_stats v "
                    + "WHERE v.department_id IN :deptIds "
                    + "AND EXISTS (SELECT 1 FROM main_internship mi "
                    + "JOIN base_internship_type bit ON bit.id = mi.internship_type_id AND bit.is_deleted = 0 "
                    + "WHERE mi.id = v.internship_id AND mi.is_deleted = 0 "
                    + "AND bit.university_id IN :owningCollegeIds) "
                    + "GROUP BY v.internship_id "
                    + "ORDER BY MAX(v.internship_create_time) DESC",
            countQuery = "SELECT COUNT(DISTINCT v.internship_id) FROM view_external_internship_college_stats v "
                    + "WHERE v.department_id IN :deptIds "
                    + "AND EXISTS (SELECT 1 FROM main_internship mi "
                    + "JOIN base_internship_type bit ON bit.id = mi.internship_type_id AND bit.is_deleted = 0 "
                    + "WHERE mi.id = v.internship_id AND mi.is_deleted = 0 "
                    + "AND bit.university_id IN :owningCollegeIds)",
            nativeQuery = true)
    Page<Object[]> findAggregatedByDepartmentIds(@Param("deptIds") List<Integer> deptIds,
                                                 @Param("owningCollegeIds") List<Integer> owningCollegeIds,
                                                 Pageable pageable);
}
