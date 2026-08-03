package newcms.repository.db;

import newcms.entity.db.ViewRelNoticeReceiver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ViewRelNoticeReceiverDao extends JpaRepository<ViewRelNoticeReceiver, Integer>,
        JpaSpecificationExecutor<ViewRelNoticeReceiver> {

    Page<ViewRelNoticeReceiver> findByStudentIdAndIsDeletedFalse(Integer studentId, Pageable pageable);

    Page<ViewRelNoticeReceiver> findByStudentIdAndIsReadAndIsDeletedFalse(
            Integer studentId, Boolean isRead, Pageable pageable);

    Page<ViewRelNoticeReceiver> findByStudentIdAndInternshipIdAndIsDeletedFalse(
            Integer studentId, Integer internshipId, Pageable pageable);

    Page<ViewRelNoticeReceiver> findByStudentIdAndInternshipIdAndIsReadAndIsDeletedFalse(
            Integer studentId, Integer internshipId, Boolean isRead, Pageable pageable);
}
