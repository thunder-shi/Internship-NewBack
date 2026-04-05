package newcms.repository.db;

import newcms.entity.db.RelIntershipUser;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * 实习与用户关联表DAO
 */
@Repository
public interface RelIntershipUserDao extends BaseDao<RelIntershipUser, Integer> {
    List<RelIntershipUser> findByUserIdAndIsDeletedFalse(Integer userId);
}

