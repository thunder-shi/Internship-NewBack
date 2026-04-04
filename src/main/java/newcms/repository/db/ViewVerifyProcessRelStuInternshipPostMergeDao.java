package newcms.repository.db;

import newcms.entity.db.ViewVerifyProcessRelStuInternshipPostMerge;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ViewVerifyProcessRelStuInternshipPostMergeDao extends BaseDao<ViewVerifyProcessRelStuInternshipPostMerge, Integer> {
    List<ViewVerifyProcessRelStuInternshipPostMerge> findByRelationIdInAndIsDeletedFalse(Iterable<Integer> relationIds);
}
