package newcms.repository.db;

import newcms.entity.db.MainSign;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MainSignDao extends BaseDao<MainSign, Integer> {

    List<MainSign> findByStuInternshipIdAndIsDeletedFalse(Integer stuInternshipId);
}
