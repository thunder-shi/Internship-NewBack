package newcms.repository.db;

import newcms.entity.db.ViewRelTitleTeacherStudent;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ViewRelTitleTeacherStudentDao extends BaseDao<ViewRelTitleTeacherStudent, Integer> {
    List<ViewRelTitleTeacherStudent> findByStuIdAndInternshipIdAndIsDeletedFalse(Integer stuId, Integer internshipId);
    List<ViewRelTitleTeacherStudent> findByStuIdAndIsDeletedFalse(Integer stuId);
    List<ViewRelTitleTeacherStudent> findByRelTitleStudentIdAndIsDeletedFalse(Integer relTitleStudentId);
    List<ViewRelTitleTeacherStudent> findByInternshipIdAndIsDeletedFalse(Integer internshipId);
}
