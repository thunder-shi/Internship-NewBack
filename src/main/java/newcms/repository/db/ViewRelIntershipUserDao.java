package newcms.repository.db;

import newcms.entity.db.ViewRelIntershipUser;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

/**
 * 实习与用户关联视图DAO
 */
@Repository
public interface ViewRelIntershipUserDao extends BaseDao<ViewRelIntershipUser, Integer> {
}

