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
     * 视图每行对应一个校外实习项目，{@code department_id} 即 {@code base_internship_type.university_id}（学院节点）。
     * 报名学生：总数为项目维度全量；{@code deptIds} 为当前统计口径部门子树，用于部门维度人数/选岗/待审岗位统计。
     */
    @Query(
            value = "SELECT v.internship_id, v.internship_name, v.internship_create_time, "
                    + "(SELECT COUNT(DISTINCT m0.user_id) "
                    + "FROM view_verify_process_rel_intership_user_merge m0 "
                    + "WHERE m0.internship_id = v.internship_id AND m0.is_deleted = 0 "
                    + "AND m0.job_code = 'STUDENT') AS signup_student_total_count, "
                    + "(SELECT COUNT(DISTINCT m1.user_id) "
                    + "FROM view_verify_process_rel_intership_user_merge m1 "
                    + "JOIN view_base_user u1 ON u1.ID = m1.user_id "
                    + "WHERE m1.internship_id = v.internship_id AND m1.is_deleted = 0 "
                    + "AND m1.job_code = 'STUDENT' AND u1.DEPARTMENT_ID IN :deptIds) AS signup_student_count, "
                    + "v.signup_teacher_count, "
                    + "v.post_signup_count, v.total_recruitment_headcount, "
                    + "(SELECT COUNT(DISTINCT vmip.internship_post_id) "
                    + "FROM view_verify_process_internship_post_merge vmip "
                    + "JOIN view_base_user u2 ON u2.ID = vmip.create_user_id "
                    + "WHERE vmip.internship_id = v.internship_id AND vmip.is_deleted = 0 "
                    + "AND vmip.is_audit IN (-1, 0) AND vmip.internship_post_id IS NOT NULL "
                    + "AND (vmip.internship_post_code <> 'SELF_INTERNSHIP' OR vmip.internship_post_code IS NULL) "
                    + "AND u2.DEPARTMENT_ID IN :deptIds) AS pending_audit_post_count, "
                    + "(SELECT COUNT(DISTINCT s.student_id) "
                    + "FROM view_verify_process_rel_stu_internship_post_merge s "
                    + "WHERE s.internship_id = v.internship_id AND s.is_deleted = 0 "
                    + "AND s.DEPARTMENT_ID IN :deptIds "
                    + "AND s.student_id IS NOT NULL AND TRIM(s.student_id) <> '') "
                    + "AS student_with_post_selection_count "
                    + "FROM view_external_internship_college_stats v "
                    + "WHERE v.department_id IN :owningCollegeIds "
                    + "ORDER BY v.internship_create_time DESC",
            countQuery = "SELECT COUNT(*) FROM view_external_internship_college_stats v "
                    + "WHERE v.department_id IN :owningCollegeIds",
            nativeQuery = true)
    Page<Object[]> findByOwningCollegeIds(@Param("owningCollegeIds") List<Integer> owningCollegeIds,
                                          @Param("deptIds") List<Integer> deptIds,
                                          Pageable pageable);
}
