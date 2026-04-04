package newcms.repository.db;

import newcms.entity.db.ViewRelStuInternshipPost;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ViewRelStuInternshipPostDao extends BaseDao<ViewRelStuInternshipPost, Integer> {
    List<ViewRelStuInternshipPost> findByStudentIdAndIsDeletedFalse(Integer studentId);
}
