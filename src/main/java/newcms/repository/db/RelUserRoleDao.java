package newcms.repository.db;

import newcms.entity.db.RelUserRole;
import newcms.repository.base.BaseDao;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RelUserRoleDao extends BaseDao<RelUserRole, Integer> {

    /**
     * 删除某个用户的所有角色
     * */
    @Modifying
    @Query(value = "update RelUserRole t set t.isDeleted=true where t.userId=?1")
    void deleteRoleRel(Integer uid);

    List<RelUserRole> findByRoleIdAndIsDeletedFalse(Integer roleId);
}
