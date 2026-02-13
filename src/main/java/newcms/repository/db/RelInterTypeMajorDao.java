package newcms.repository.db;
import newcms.entity.db.RelInterTypeMajor;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;


/**
 * 实习项目范围表DAO
 */
@Repository("relInterTypeMajorDao")
public interface RelInterTypeMajorDao extends BaseDao<RelInterTypeMajor, Integer> {
    
}
