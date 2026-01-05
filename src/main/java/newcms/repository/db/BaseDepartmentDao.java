package newcms.repository.db;

import newcms.entity.db.BaseDepartment;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

@Repository
public interface BaseDepartmentDao extends BaseDao<BaseDepartment, Integer> {
    Object getByCode(String code);
    Object getByCodeAndIsDeletedFalse(String code);
}

