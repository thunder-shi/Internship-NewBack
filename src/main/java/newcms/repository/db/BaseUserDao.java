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

    /**
     * 根据角色ID和部门ID查询有资格审核的用户ID列表
     * @param roleId 角色ID
     * @param departmentId 部门/院系ID
     * @return 用户ID列表
     */
    @Query("SELECT u.id FROM BaseUser u " +
           "JOIN RelUserRole r ON u.id = r.userId " +
           "WHERE r.roleId = ?1 AND u.departmentId = ?2 " +
           "AND u.isDeleted = false AND r.isDeleted = false")
    List<Integer> findUserIdsByRoleIdAndDepartmentId(Integer roleId, Integer departmentId);

    /**
     * 根据用户ID列表查询用户名称
     * @param userIds 用户ID列表
     * @return 用户名称列表
     */
    @Query("SELECT u.name FROM BaseUser u WHERE u.id IN ?1 AND u.isDeleted = false")
    List<String> findNamesByIds(List<Integer> userIds);

    /**
     * 根据单个用户ID查询用户名称
     * @param userId 用户ID
     * @return 用户名称
     */
    @Query("SELECT u.name FROM BaseUser u WHERE u.id = ?1 AND u.isDeleted = false")
    String findNameById(Integer userId);
}
