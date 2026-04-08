package newcms.repository.db;

import newcms.entity.db.MainDiaryPeriod;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MainDiaryPeriodDao extends BaseDao<MainDiaryPeriod, Integer> {
    List<MainDiaryPeriod> findByInternshipIdAndIsDeletedFalseOrderByPeriodIndexAsc(Integer internshipId);
}
