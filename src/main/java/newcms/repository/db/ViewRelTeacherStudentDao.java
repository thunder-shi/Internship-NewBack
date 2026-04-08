package newcms.repository.db;

import newcms.entity.db.ViewRelTeacherStudent;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 教师与学生实习关联视图DAO
 */
@Repository
public interface ViewRelTeacherStudentDao extends BaseDao<ViewRelTeacherStudent, Integer> {
    List<ViewRelTeacherStudent> findByInternshipIdAndIsDeletedFalse(Integer internshipId);
    List<ViewRelTeacherStudent> findByTeacherIdAndIsDeletedFalse(Integer teacherId);
    List<ViewRelTeacherStudent> findByStudentIdAndIsDeletedFalse(Integer studentId);
    List<ViewRelTeacherStudent> findByStudentIdAndInternshipIdAndIsDeletedFalse(Integer studentId, Integer internshipId);
}
