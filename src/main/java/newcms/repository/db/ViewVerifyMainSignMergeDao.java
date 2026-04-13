package newcms.repository.db;

import newcms.entity.db.ViewVerifyMainSignMerge;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ViewVerifyMainSignMergeDao extends BaseDao<ViewVerifyMainSignMerge, Integer> {
    List<ViewVerifyMainSignMerge> findByRelationIdIn(Iterable<Integer> relationIds);
}
