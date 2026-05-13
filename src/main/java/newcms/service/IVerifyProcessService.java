package newcms.service;

import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Service;

/**
 * 审核流程服务接口
 */
@Service
public interface IVerifyProcessService {
    /**
     * 根据实习项目ID和流程类型代码查找流程关联记录（取第一条）
     * @param internshipId 实习项目ID
     * @param processTypeCode 流程类型代码，如果为null则默认为INTERNSHIP_PLAN_MAKE
     * @return 找到的流程关联记录对象
     */
    Object GetInternshipProcess(Integer internshipId, String processTypeCode);

    /**
     * 根据审核角色ID和当前用户ID获取审核用户ID字符串
     * @param verifyRoleId 审核角色ID
     * @param createUserId 当前创建用户ID
     * @return 审核用户ID字符串，用竖线分隔（格式：12|14|17）
     */
    String GetVerifyUserId(Integer verifyRoleId, Integer createUserId);

    /**
     * 根据审核角色ID和当前用户ID获取审核用户ID字符串（带 internshipId 回落）
     * <p>
     * 当 createUserId 对应的用户没有 schoolId（如企业用户）时，
     * 通过 internshipId 查找实习项目创建者的 schoolId 作为回落，
     * 确保教务处管理员等校级角色能审核企业提交的记录。
     * 企业信息申报等无 internshipId 的场景，请改用四参数重载并传入单据上的合作学校根部门 id。
     * </p>
     * @param verifyRoleId 审核角色ID
     * @param createUserId 当前创建用户ID
     * @param internshipId 实习项目ID（用于回落查找学校，可为 null）
     * @return 审核用户ID字符串，用竖线分隔（格式：12|14|17）
     */
    String GetVerifyUserId(Integer verifyRoleId, Integer createUserId, Integer internshipId);

    /**
     * 与 {@link #GetVerifyUserId(Integer, Integer, Integer)} 相同，但可指定「审核范围学校」根部门 id。
     * <p>
     * 企业信息申请等场景：提交人为企业管理员，其在 view_base_user 上的 schoolId 往往对应企业挂靠组织，
     * 与待匹配的学校侧审核人（如学校管理员）的 schoolId 不一致；此时应传入单据上的合作学校 id
     * （如 main_enterprise_info.school_id），在本参数非 null 时优先仅按该校范围查找审核人。
     * </p>
     *
     * @param hostSchoolScopeId 合作学校根部门 id；为 null 时行为与三参数重载一致
     */
    String GetVerifyUserId(Integer verifyRoleId, Integer createUserId, Integer internshipId, Integer hostSchoolScopeId);

    /**
     * 刷新指定用户相关的待审核记录
     * 当特定用户的角色或部门发生变更时调用
     *
     * @param userId 变更的用户ID
     * @return 更新的记录数量
     */
    int refreshPendingVerifyUsersByUser(Integer userId);

    /**
     * 激活单个流程
     * 根据传入的参数创建审核记录
     *
     * @param node 包含 relationId、processId、createUserId、tableName 的 JSON 对象
     * @return 创建的审核记录对象，如果已存在则返回 null
     */
    Object activateProcess(JSONObject node);

    /**
     * 审核通过后的回调处理
     * 当 editOneNode 将 MainVerifyProcess.isAudit 修改为 1 后自动调用
     * 检查 RelProcessInternship.currentVerifyTypeId 是否小于 verifyTypeId：
     * - 是：currentVerifyTypeId + 1，并创建下一级审核的 MainVerifyProcess 记录
     * - 否：审核全部完成
     *
     * @param verifyProcessId 审核通过的记录ID
     */
    void onVerifyProcessApproved(Integer verifyProcessId);

    /**
     * 根据审核级别从流程记录JSON中获取对应的审核角色ID
     *
     * @param relJson 流程关联记录JSON
     * @param verifyLevel 审核级别（2-6）
     * @return 对应级别的审核角色ID
     */
    Integer getVerifyRoleIdByLevel(JSONObject relJson, Integer verifyLevel);

    /**
     * 当 RelProcessInternship 的审核角色配置变更时，刷新对应的待审核记录的 verifyUserId
     *
     * @param processId RelProcessInternship 的 ID
     * @return 更新的记录数量
     */
    int refreshPendingVerifyUsersByProcess(Integer processId);

    /**
     * 学生某一岗位报名审核全部通过后，级联软删除同实习项目下其余报名记录及其审核记录。
     *
     * @param approvedRelStuPostId 已通过的 RelStuInternshipPost.id（本条不删）
     * @param studentId            学生 ID
     * @param internshipId         实习项目 ID
     */
    void cancelOtherStuPostsOnApproval(Integer approvedRelStuPostId,
                                        Integer studentId,
                                        Integer internshipId);

    /**
     * 当某岗位因一名学生审核通过而已招满时，级联软删除该岗位剩余的待审核报名记录。
     *
     * @param postId               已招满的岗位 ID（MainInternshipPost.id）
     * @param approvedRelStuPostId 已通过的 RelStuInternshipPost.id（本条不删）
     */
    void cancelPendingApplicationsIfPostFull(Integer postId, Integer approvedRelStuPostId);
}
