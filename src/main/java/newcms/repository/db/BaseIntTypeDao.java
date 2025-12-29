package newcms.repository.db;

import newcms.entity.db.BaseIntType;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

@Repository
public interface BaseIntTypeDao extends BaseDao<BaseIntType, Integer> {
}

