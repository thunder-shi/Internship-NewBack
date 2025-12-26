package newcms.repository.db;

import newcms.entity.db.RelRoleMenu;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RelRoleMenuDao extends BaseDao<RelRoleMenu, Integer> {

    /**
     * 查询角色权限
     *
     * @param roleIdSet
     * @return
     */
    List<RelRoleMenu> findByRoleIdInAndIsDeletedFalse(Iterable<Integer> roleIdSet);

    /**
     * 查询菜单关联角色
     * @param collect
     * @return
     */
    List<RelRoleMenu> findByMenuIdInAndIsDeletedFalse(Iterable<Integer> collect);
}
