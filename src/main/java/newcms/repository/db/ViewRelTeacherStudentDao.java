package newcms.repository.db;

import newcms.entity.db.ViewRelTeacherStudent;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

/**
 * 教师与学生实习关联视图DAO
 */
@Repository
public interface ViewRelTeacherStudentDao extends BaseDao<ViewRelTeacherStudent, Integer> {
}

