package newcms.repository.db;

import newcms.entity.db.ViewExternalInternshipCollegeStats;
import newcms.entity.db.ViewExternalInternshipCollegeStatsId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * 只读视图 DAO（无 BaseDao：复合主键且视图无 isDeleted）。
 */
@Repository
public interface ViewExternalInternshipCollegeStatsDao extends
        JpaRepository<ViewExternalInternshipCollegeStats, ViewExternalInternshipCollegeStatsId>,
        JpaSpecificationExecutor<ViewExternalInternshipCollegeStats> {
}
