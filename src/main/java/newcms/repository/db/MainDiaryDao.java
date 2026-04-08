package newcms.repository.db;

import newcms.entity.db.MainDiary;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MainDiaryDao extends BaseDao<MainDiary, Integer> {
    // 按 relationId + tableName 查询（通用，校外传 "RelStuInternshipPost"，校内传 "RelTitleStudent"）
    Optional<MainDiary> findByRelationIdAndTableNameAndPeriodIdAndIsDeletedFalse(Integer relationId, String tableName, Integer periodId);
    List<MainDiary> findByRelationIdAndTableNameAndIsDeletedFalse(Integer relationId, String tableName);

    // 批量：多个 relationId + 同一 tableName + 同一 periodId
    List<MainDiary> findByRelationIdInAndTableNameAndPeriodIdAndIsDeletedFalse(Iterable<Integer> relationIds, String tableName, Integer periodId);
    List<MainDiary> findByRelationIdInAndTableNameAndIsDeletedFalse(Iterable<Integer> relationIds, String tableName);
}
