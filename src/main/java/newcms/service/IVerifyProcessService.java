package newcms.service;

import org.springframework.stereotype.Service;

/**
 * 审核流程服务接口
 */
@Service
public interface IVerifyProcessService {
    /**
     * 根据实习项目ID查找流程关联记录（取第一条）
     * @param internshipId 实习项目ID
     * @return 找到的流程关联记录对象
     */
    Object GetInternshipFoundProcess(Integer internshipId);

    /**
     * 根据审核角色ID和当前用户ID获取审核用户ID字符串
     * @param verifyRoleId 审核角色ID
     * @param createUserId 当前创建用户ID
     * @return 审核用户ID字符串，用竖线分隔（格式：12|14|17）
     */
    String GetVerifyUserId(Integer verifyRoleId, Integer createUserId);

    /**
     * 刷新所有待审核记录的审核用户ID
     * 当用户角色或部门发生变更时调用此方法
     * 会重新计算所有 isAudit=0 的待审核记录的 verifyUserId
     *
     * @return 更新的记录数量
     */
    int refreshPendingVerifyUsers();

    /**
     * 刷新指定用户相关的待审核记录
     * 当特定用户的角色或部门发生变更时调用
     *
     * @param userId 变更的用户ID
     * @return 更新的记录数量
     */
    int refreshPendingVerifyUsersByUser(Integer userId);

    /**
     * 检查并激活已到开始时间的流程
     * 扫描 RelProcessInternship 表，对于 startTime <= 当前时间 且尚未创建审核记录的流程，
     * 自动创建 MainVerifyProcess 记录（isAudit=0）
     *
     * @return 新创建的审核记录数量
     */
    int activateStartedProcesses();

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
}
