package newcms.repository.db;

import newcms.entity.db.RelTeacherStudent;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

/**
 * 教师与学生实习关联表DAO
 */
@Repository
public interface RelTeacherStudentDao extends BaseDao<RelTeacherStudent, Integer> {
}

