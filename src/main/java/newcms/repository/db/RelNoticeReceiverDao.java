package newcms.repository.db;

import newcms.entity.db.RelNoticeReceiver;
import newcms.repository.base.BaseDao;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RelNoticeReceiverDao extends BaseDao<RelNoticeReceiver, Integer> {

    List<RelNoticeReceiver> findByNoticeIdAndIsDeletedFalse(Integer noticeId);

    List<RelNoticeReceiver> findByNoticeIdAndStudentIdAndIsDeletedFalse(Integer noticeId, Integer studentId);

    @Query("SELECT COUNT(r) FROM RelNoticeReceiver r WHERE r.studentId = :studentId AND r.isDeleted = false AND r.isRead = false "
            + "AND EXISTS (SELECT 1 FROM MainNotice n WHERE n.id = r.noticeId AND n.isDeleted = false "
            + "AND (:internshipId IS NULL OR n.internshipId = :internshipId))")
    long countUnreadByStudentId(@Param("studentId") Integer studentId, @Param("internshipId") Integer internshipId);
}
