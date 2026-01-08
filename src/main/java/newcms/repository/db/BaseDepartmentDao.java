package newcms.repository.db;

import newcms.entity.db.BaseDepartment;
import newcms.repository.base.BaseDao;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BaseDepartmentDao extends BaseDao<BaseDepartment, Integer> {
    Object getByCode(String code);
    Object getByCodeAndIsDeletedFalse(String code);
    
    @Query("select t from BaseDepartment t where t.name = ?1")
    List<BaseDepartment> findByName(String name);
    
    @Query("select t from BaseDepartment t where t.name = ?1 and t.isDeleted = false")
    List<BaseDepartment> findByNameAndIsDeletedFalse(String name);
}

