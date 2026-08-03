package newcms.repository.db;

import newcms.entity.db.ViewExternalInternshipStudentPostBreakdown;
import newcms.entity.db.ViewExternalInternshipStudentPostBreakdownId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 校外实习入项学生选岗明细视图 DAO（复合主键，无 BaseDao）。
 */
@Repository
public interface ViewExternalInternshipStudentPostBreakdownDao extends
        JpaRepository<ViewExternalInternshipStudentPostBreakdown, ViewExternalInternshipStudentPostBreakdownId> {

    @Query("SELECT v.selectionStatus, COUNT(v) FROM ViewExternalInternshipStudentPostBreakdown v "
            + "WHERE v.internshipId = :internshipId AND v.userId IN :userIds "
            + "GROUP BY v.selectionStatus")
    List<Object[]> countGroupBySelectionStatus(@Param("internshipId") Integer internshipId,
                                               @Param("userIds") Collection<Integer> userIds);

    @Query("SELECT v FROM ViewExternalInternshipStudentPostBreakdown v "
            + "WHERE v.internshipId = :internshipId AND v.userId IN :userIds "
            + "ORDER BY v.userId ASC")
    Page<ViewExternalInternshipStudentPostBreakdown> findAllByInternshipAndUsers(
            @Param("internshipId") Integer internshipId,
            @Param("userIds") Collection<Integer> userIds,
            Pageable pageable);

    @Query("SELECT v FROM ViewExternalInternshipStudentPostBreakdown v "
            + "WHERE v.internshipId = :internshipId AND v.userId IN :userIds "
            + "AND v.selectionStatus = :selectionStatus "
            + "ORDER BY v.userId ASC")
    Page<ViewExternalInternshipStudentPostBreakdown> findByInternshipUsersAndStatus(
            @Param("internshipId") Integer internshipId,
            @Param("userIds") Collection<Integer> userIds,
            @Param("selectionStatus") String selectionStatus,
            Pageable pageable);

    @Query("SELECT v FROM ViewExternalInternshipStudentPostBreakdown v "
            + "WHERE v.internshipId = :internshipId AND v.userId IN :userIds "
            + "AND v.selectionStatus IN :selectionStatuses "
            + "ORDER BY v.userId ASC")
    Page<ViewExternalInternshipStudentPostBreakdown> findByInternshipUsersAndStatusIn(
            @Param("internshipId") Integer internshipId,
            @Param("userIds") Collection<Integer> userIds,
            @Param("selectionStatuses") Collection<String> selectionStatuses,
            Pageable pageable);
}
