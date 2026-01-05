package newcms.repository.db;

import newcms.entity.db.BaseJobPosition;
import newcms.repository.base.BaseDao;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface BaseJobPositionDao extends BaseDao<BaseJobPosition, Integer> {
    Object getByCode(String code);
    Object getByCodeAndIsDeletedFalse(String code);
}

