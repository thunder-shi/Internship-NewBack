package newcms.repository.db;

import newcms.entity.db.BaseUser;
import newcms.repository.base.BaseDao;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BaseUserDao extends BaseDao<BaseUser, Integer> {

    /**
     * 查询用户
     * @param phone
     * @return
     */
    List<BaseUser> findByPhoneAndIsDeletedFalse(String phone);

    /**
     * 查询account,判重使用
     * */
    @Query(value = "select t.account from BaseUser t where t.isDeleted=false")
    List<String> getAllAccount();
}
