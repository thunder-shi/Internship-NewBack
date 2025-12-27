package newcms.repository.db;

import newcms.entity.db.BaseMajor;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

@Repository
public interface BaseMajorDao extends BaseDao<BaseMajor, Integer> {
    Object getByCode(String code);
    Object getByCodeAndIsDeletedFalse(String code);
}

