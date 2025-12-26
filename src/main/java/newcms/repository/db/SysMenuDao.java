package newcms.repository.db;

import newcms.entity.db.SysMenu;
import newcms.repository.base.BaseTreeDao;
import org.springframework.stereotype.Repository;

@Repository
public interface SysMenuDao extends BaseTreeDao<SysMenu, Integer> {
}
