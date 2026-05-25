package newcms.repository.db;

import newcms.entity.db.MainDiaryScoreDetail;
import newcms.repository.base.BaseDao;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface MainDiaryScoreDetailDao extends BaseDao<MainDiaryScoreDetail, Integer> {

    /** 查询某日志的全部评分明细，按审核级别升序 */
    List<MainDiaryScoreDetail> findByDiaryIdAndIsDeletedFalseOrderByLevelOrderAsc(Integer diaryId);

    /** 查询某日志某级别的评分明细 */
    List<MainDiaryScoreDetail> findByDiaryIdAndLevelOrderAndIsDeletedFalse(Integer diaryId, Integer levelOrder);

    /** 批量软删除某日志的全部评分明细（重算前清理旧数据） */
    @Transactional(rollbackFor = Exception.class)
    @Modifying
    @Query("update MainDiaryScoreDetail d set d.isDeleted = true where d.diaryId = ?1 and d.isDeleted = false")
    void softDeleteByDiaryId(Integer diaryId);
}
