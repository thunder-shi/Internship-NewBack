package newcms.repository.db;

import newcms.entity.db.MainInternship;
import newcms.repository.base.BaseAuditDao;
import org.springframework.stereotype.Repository;

@Repository
public interface MainInternshipDao extends BaseAuditDao<MainInternship, Integer> {
}
