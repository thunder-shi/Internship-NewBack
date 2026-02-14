package newcms.repository.db;

import newcms.entity.db.RelPostMajor;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

/**
 * 岗位专业关联表DAO
 */
@Repository("relPostMajorDao")
public interface RelPostMajorDao extends BaseDao<RelPostMajor, Integer> {
    
}
