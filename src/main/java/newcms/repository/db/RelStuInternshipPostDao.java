package newcms.repository.db;

import newcms.entity.db.RelStuInternshipPost;
import newcms.repository.base.BaseDao;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RelStuInternshipPostDao extends BaseDao<RelStuInternshipPost, Integer> {
    List<RelStuInternshipPost> findByInternshipPostIdInAndIsDeletedFalse(Iterable<Integer> internshipPostIds);

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
}
