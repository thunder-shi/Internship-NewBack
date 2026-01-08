package newcms.repository.db;

import newcms.entity.db.BaseMajor;
import newcms.repository.base.BaseDao;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BaseMajorDao extends BaseDao<BaseMajor, Integer> {
    Object getByCode(String code);
    Object getByCodeAndIsDeletedFalse(String code);
    
    @Query("select t from BaseMajor t where t.name = ?1")
    List<BaseMajor> findByName(String name);
    
    @Query("select t from BaseMajor t where t.name = ?1 and t.isDeleted = false")
    List<BaseMajor> findByNameAndIsDeletedFalse(String name);
}

