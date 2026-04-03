package newcms.repository.db;

import newcms.entity.db.RelStuInternshipPost;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RelStuInternshipPostDao extends BaseDao<RelStuInternshipPost, Integer> {
    List<RelStuInternshipPost> findByInternshipPostIdInAndIsDeletedFalse(Iterable<Integer> internshipPostIds);
}
