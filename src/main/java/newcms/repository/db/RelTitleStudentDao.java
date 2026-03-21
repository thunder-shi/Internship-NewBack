package newcms.repository.db;

import newcms.entity.db.RelTitleStudent;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

/**
 * 学生职务关联题目表DAO
 */
@Repository
public interface RelTitleStudentDao extends BaseDao<RelTitleStudent, Integer> {
}

