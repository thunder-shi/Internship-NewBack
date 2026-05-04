package newcms.repository.db;

import newcms.entity.db.ViewLeaveAuditFlow;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ViewLeaveAuditFlowDao extends BaseDao<ViewLeaveAuditFlow, Integer> {
    List<ViewLeaveAuditFlow> findByLeaveId(Integer leaveId);
}
