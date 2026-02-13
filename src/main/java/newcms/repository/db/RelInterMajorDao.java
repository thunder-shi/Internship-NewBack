package newcms.repository.db;

import newcms.entity.db.RelInterMajor;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

/**
 * 实习项目范围表DAO
 */
@Repository("relInterMajorDao")
public interface RelInterMajorDao extends BaseDao<RelInterMajor, Integer> {
    
}
