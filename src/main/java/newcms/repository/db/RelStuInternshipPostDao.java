package newcms.repository.db;

import newcms.entity.db.RelStuInternshipPost;
import newcms.repository.base.BaseDao;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RelStuInternshipPostDao extends BaseDao<RelStuInternshipPost, Integer> {
    List<RelStuInternshipPost> findByInternshipPostIdInAndIsDeletedFalse(Iterable<Integer> internshipPostIds);

    List<RelStuInternshipPost> findByInternshipPostIdAndIsDeletedFalse(Integer internshipPostId);

    /**
     * 查询学生在指定岗位集合中的所有未删除报名记录（用于级联删除其他报名）。
     */
    List<RelStuInternshipPost> findByStudentIdAndInternshipPostIdInAndIsDeletedFalse(
            Integer studentId, Iterable<Integer> internshipPostIds);

    /**
     * 统计学生在某实习项目下已审核通过（isAudit=1）的有效报名记录数。
     * 用于报名前校验：若 > 0 则拦截（CONC 兜底）。
     */
    @Query(nativeQuery = true, value =
        "SELECT COUNT(*) FROM rel_stu_internship_post r " +
        "JOIN main_internship_post p ON p.id = r.internship_post_id " +
        "JOIN main_verify_process v ON v.relation_id = r.id AND v.table_name = 'RelStuInternshipPost' " +
        "WHERE r.student_id = :studentId AND p.internship_id = :internshipId " +
        "AND r.is_deleted = 0 AND v.is_audit = 1 AND v.is_deleted = 0")
    long countApprovedPostForStudentInInternship(@Param("studentId") Integer studentId,
                                                 @Param("internshipId") Integer internshipId);

    /**
     * 统计学生在某实习项目下企业岗位（code != 'SELF_INTERNSHIP'）已审核通过的报名记录数。
     * 用于「同一学生在同一校外实习项目下只能有 1 条企业岗位 PASS」互斥校验。
     * 自主实习不计入，不参与互斥。
     */
    @Query(nativeQuery = true, value =
        "SELECT COUNT(*) FROM rel_stu_internship_post r " +
        "JOIN main_internship_post p ON p.id = r.internship_post_id " +
        "JOIN main_verify_process v ON v.relation_id = r.id AND v.table_name = 'RelStuInternshipPost' " +
        "WHERE r.student_id = :studentId AND p.internship_id = :internshipId " +
        "AND (p.code IS NULL OR p.code <> 'SELF_INTERNSHIP') " +
        "AND r.is_deleted = 0 AND v.is_audit = 1 AND v.is_deleted = 0")
    long countApprovedCompanyPostForStudentInInternship(@Param("studentId") Integer studentId,
                                                         @Param("internshipId") Integer internshipId);

    /**
     * 查找学生在某实习项目下的自主实习记录（code='SELF_INTERNSHIP'），取最新一条。
     * 返回 RelStuInternshipPost.id（业务表，含 self_* 字段）。
     * 供 applySelfInternship 判断是新建 / update-in-place / 拒绝。
     */
    @Query(nativeQuery = true, value =
        "SELECT r.id FROM rel_stu_internship_post r " +
        "JOIN main_internship_post p ON p.id = r.internship_post_id " +
        "WHERE r.student_id = :studentId AND p.internship_id = :internshipId " +
        "AND p.code = 'SELF_INTERNSHIP' AND p.is_deleted = 0 AND r.is_deleted = 0 " +
        "ORDER BY r.id DESC LIMIT 1")
    Optional<Integer> findActiveSelfInternshipRelId(@Param("studentId") Integer studentId,
                                                     @Param("internshipId") Integer internshipId);
}
