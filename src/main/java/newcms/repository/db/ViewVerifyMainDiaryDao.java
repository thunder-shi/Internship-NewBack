package newcms.repository.db;

import newcms.entity.db.ViewVerifyMainDiary;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ViewVerifyMainDiaryDao extends BaseDao<ViewVerifyMainDiary, Integer> {
    List<ViewVerifyMainDiary> findByRelationIdIn(Iterable<Integer> relationIds);
    List<ViewVerifyMainDiary> findByCreateUserIdAndIsDeletedFalse(Integer createUserId);
}
