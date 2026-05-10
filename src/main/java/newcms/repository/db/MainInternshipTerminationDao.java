package newcms.repository.db;

import newcms.entity.db.MainInternshipTermination;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MainInternshipTerminationDao extends BaseDao<MainInternshipTermination, Integer> {
    List<MainInternshipTermination> findByRelationTableAndRelationIdAndStatusInAndIsDeletedFalse(
            String relationTable, Integer relationId, Collection<Integer> statuses);

    List<MainInternshipTermination> findByRelationTableAndRelationIdAndIsDeletedFalse(
            String relationTable, Integer relationId);
}
