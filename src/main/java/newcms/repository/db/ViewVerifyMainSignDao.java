package newcms.repository.db;

import newcms.entity.db.ViewVerifyMainSign;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ViewVerifyMainSignDao extends BaseDao<ViewVerifyMainSign, Integer> {
    List<ViewVerifyMainSign> findByRelationIdIn(Iterable<Integer> relationIds);
}
