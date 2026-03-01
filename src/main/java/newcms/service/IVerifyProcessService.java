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
}
