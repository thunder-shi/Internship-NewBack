package newcms.service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public interface IInternshipService {
    /**
     * 根据实习类型模板复制流程配置到实习项目
     * @param internshipId 实习项目ID
     * @param internshipTypeId 实习类型ID（模板ID）
     */
    void copyProcessFromTemplate(Integer internshipId, Integer internshipTypeId);

    /**
     * 更新实习项目的流程配置（当模板类型变化时）
     * 先删除旧的流程配置，再复制新模板的流程配置
     * @param internshipId 实习项目ID
     * @param newInternshipTypeId 新的实习类型ID（模板ID）
     */
    void updateProcessFromTemplate(Integer internshipId, Integer newInternshipTypeId);

    /**
     * 创建实习项目的审核记录
     * 如果项目流程中存在"实习计划制定"流程且需要审核，则创建审核记录
     * @param internshipId 实习项目ID
     * @param internshipTypeId 实习类型ID（用于获取所属院系）
     * @param createUserId 当前用户ID（创建人）
     * @param isAudit 审核状态（-1:保存未提交, 0:提交待审核, 1:审核通过, 2:审核不通过, 3:审核退回）
     */
    void createVerifyProcessIfNeeded(Integer internshipId, Integer internshipTypeId,
                                      Integer createUserId, Integer isAudit);

    /**
     * 执行审核操作
     * @param mainVerifyProcessId 审核记录ID
     * @param action 操作类型: 1-通过, 2-不通过, 3-退回
     * @param operatorId 操作人ID
     * @param reason 审核意见
     */
    void doVerify(Integer mainVerifyProcessId, Integer action, Integer operatorId, String reason);

    /**
     * 流程创建人重新提交（状态为退回时）
     * @param mainVerifyProcessId 审核记录ID
     * @param operatorId 操作人ID（必须是原流程创建人）
     */
    void resubmit(Integer mainVerifyProcessId, Integer operatorId);

    /**
     * 获取审核进度详情
     * @param internshipId 实习项目ID
     * @return 审核进度信息
     */
    Map<String, Object> getVerifyProgress(Integer internshipId);

    /**
     * 判断用户是否可以对指定审核记录进行操作
     * @param mainVerifyProcessId 审核记录ID
     * @param userId 用户ID
     * @return true-可以操作, false-不可操作
     */
    boolean canOperate(Integer mainVerifyProcessId, Integer userId);
}
