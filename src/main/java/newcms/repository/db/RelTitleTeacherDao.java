package newcms.repository.db;

import newcms.entity.db.RelTitleTeacher;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

/**
 * 教师职务关联题目表DAO
 */
@Repository
public interface RelTitleTeacherDao extends BaseDao<RelTitleTeacher, Integer> {
}

