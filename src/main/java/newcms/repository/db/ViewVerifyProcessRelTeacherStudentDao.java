package newcms.repository.db;

import newcms.entity.db.ViewVerifyProcessRelTeacherStudent;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

/**
 * 审核流程-导师学生选择视图DAO
 */
@Repository
public interface ViewVerifyProcessRelTeacherStudentDao extends BaseDao<ViewVerifyProcessRelTeacherStudent, Integer> {
}

