package newcms.repository.db;

import newcms.entity.db.SysRole;
import newcms.repository.base.BaseDao;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SysRoleDao extends BaseDao<SysRole, Integer> {
    @Query("select t from SysRole t where t.name = ?1 and t.isDeleted = false")
    SysRole findByNameAndIsDeletedFalse(String name);
}

