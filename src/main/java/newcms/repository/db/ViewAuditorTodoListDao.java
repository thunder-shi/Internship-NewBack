package newcms.repository.db;

import newcms.entity.db.ViewAuditorTodoList;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ViewAuditorTodoListDao extends BaseDao<ViewAuditorTodoList, Integer> {
    List<ViewAuditorTodoList> findByIsAudit(Integer isAudit);
}
