package newcms.repository.db;

import newcms.entity.db.ViewVerifyMainDiaryMerge;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ViewVerifyMainDiaryMergeDao extends BaseDao<ViewVerifyMainDiaryMerge, Integer> {
    List<ViewVerifyMainDiaryMerge> findByRelationIdIn(Iterable<Integer> relationIds);
}
