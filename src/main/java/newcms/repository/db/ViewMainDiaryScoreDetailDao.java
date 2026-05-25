package newcms.repository.db;

import newcms.entity.db.ViewMainDiaryScoreDetail;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ViewMainDiaryScoreDetailDao extends BaseDao<ViewMainDiaryScoreDetail, Integer> {

    /** 查询某日志的全部评分明细（含上下文），按审核级别升序 */
    List<ViewMainDiaryScoreDetail> findByDiaryIdAndIsDeletedFalseOrderByLevelOrderAsc(Integer diaryId);

    /** 查询某实习项目下所有日志的评分明细（含上下文），按日志ID + 审核级别升序 */
    List<ViewMainDiaryScoreDetail> findByInternshipIdAndIsDeletedFalseOrderByDiaryIdAscLevelOrderAsc(Integer internshipId);
}
