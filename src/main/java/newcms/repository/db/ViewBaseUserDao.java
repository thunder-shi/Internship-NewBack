package newcms.repository.db;

import newcms.entity.db.ViewBaseUser;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ViewBaseUserDao extends BaseDao<ViewBaseUser, Integer> {
    List<ViewBaseUser> getByIdInAndIsDeletedFalse(Iterable<Integer> ids);
}

