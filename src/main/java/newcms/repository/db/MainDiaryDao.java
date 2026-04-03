package newcms.repository.db;

import newcms.entity.db.MainDiary;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MainDiaryDao extends BaseDao<MainDiary, Integer> {
    // 校外：按 stuInternshipPostId 查询
    Optional<MainDiary> findByStuInternshipPostIdAndPeriodIndexAndIsDeletedFalse(Integer stuInternshipPostId, Integer periodIndex);
    List<MainDiary> findByStuInternshipPostIdAndIsDeletedFalse(Integer stuInternshipPostId);
    List<MainDiary> findByStuInternshipPostIdInAndPeriodIndexAndIsDeletedFalse(Iterable<Integer> stuInternshipPostIds, Integer periodIndex);

    // 校内：按 relTitleStudentId 查询
    Optional<MainDiary> findByRelTitleStudentIdAndPeriodIndexAndIsDeletedFalse(Integer relTitleStudentId, Integer periodIndex);
    List<MainDiary> findByRelTitleStudentIdAndIsDeletedFalse(Integer relTitleStudentId);
    List<MainDiary> findByRelTitleStudentIdInAndPeriodIndexAndIsDeletedFalse(Iterable<Integer> relTitleStudentIds, Integer periodIndex);
}
