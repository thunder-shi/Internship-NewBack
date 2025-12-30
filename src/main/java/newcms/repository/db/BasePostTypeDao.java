package newcms.repository.db;

import newcms.entity.db.BasePostType;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

@Repository
public interface BasePostTypeDao extends BaseDao<BasePostType, Integer> {
}
