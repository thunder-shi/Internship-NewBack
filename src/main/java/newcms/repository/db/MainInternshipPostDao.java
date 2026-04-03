package newcms.repository.db;

import newcms.entity.db.MainInternshipPost;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MainInternshipPostDao extends BaseDao<MainInternshipPost, Integer> {
    List<MainInternshipPost> findByInternshipIdAndIsDeletedFalse(Integer internshipId);
}
