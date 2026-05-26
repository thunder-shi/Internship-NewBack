package newcms.service;

import com.alibaba.fastjson.JSONObject;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;
import java.util.Map;

/**
 * @author hongzhangming
 */
@Service
public interface IUserService {
    /**
     * 用户注册
     */
    //Object register(String phone, String account, String password);
    Object register(JSONObject json);


    public Object getLoginUser();
    public Object getLoginUser(Date date, String userAgent);


    /**
     * 登录
     * @param account
     * @param password
     * @param rememberMe
     */
    void login(String account, String password, Boolean rememberMe);

    /**
     * 修改/重置密码（请求体固定含 userId、oldPassword、password、reset 四字段）
     * @param reset true 时使用 password 重置，忽略 oldPassword，不做弱密码校验
     */
    void editPassword(String userId, String oldPassword, String password, boolean reset);


    /**
     * 查询所有用户
     * @return
     */
    Object userList();

    /**
     * 删除用户
     * @param phone
     */
    void deleteUser(String phone);


    /**
     * 配置用户角色
     * @param roleSet
     * @param userId
     * @return
     */
    Object userRoleSet(Integer[] roleSet, Integer userId);

    /**
     * 获取所有用户信息
     * @param pageInfo
     * @param sort
     * @return
     */
    Object getUserListIsNotDelete(JSONObject pageInfo, JSONObject sort);

    /**编辑用户信息
     *
     * @param tblUserInfo
     * @return
     */
    Object editUserInfo(JSONObject tblUserInfo, Integer userId);

    /**
     *@Des 用户获取List
     *@Author yukai
     *@Date 2020/12/10 16:14
     */
    Object tblUserGetList(String tblName, JSONObject searchKeys, Map<String, String> repMap, Sort sort, Integer page, Integer size);
    Object ViewUserGetList(String tblName, JSONObject searchKeys, Map<String, String> repMap, Sort sort, Integer page, Integer size);

    /**
     *  用户上传头像
     * @param file
     * @return
     */
    Object uploadAvatar( MultipartFile file);


    Object getUserRoles(String userId);
    Object saveUserRoles(String userId, Integer[] roleIds);
    Object getDepartment(String parentId);
}
