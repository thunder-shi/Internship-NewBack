package newcms.repository.db;

import newcms.entity.db.ViewMainNotice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ViewMainNoticeDao extends JpaRepository<ViewMainNotice, Integer>,
        JpaSpecificationExecutor<ViewMainNotice> {

    Page<ViewMainNotice> findByPublisherIdAndIsDeletedFalse(Integer publisherId, Pageable pageable);

    Page<ViewMainNotice> findByPublisherIdAndInternshipIdAndIsDeletedFalse(
            Integer publisherId, Integer internshipId, Pageable pageable);
}
