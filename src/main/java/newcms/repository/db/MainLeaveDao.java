package newcms.repository.db;

import newcms.entity.db.MainLeave;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MainLeaveDao extends BaseDao<MainLeave, Integer> {
    List<MainLeave> findByStuInternshipIdAndIsDeletedFalse(Integer stuInternshipId);
}
