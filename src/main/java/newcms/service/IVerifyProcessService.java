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
     * @param verifyFirstRoleId 一级审核角色ID
     * @param createUserId 当前创建用户ID
     * @return 审核用户ID字符串，用竖线分隔（格式：12|14|17）
     */
    String GetVerifyUserId(Integer verifyFirstRoleId, Integer createUserId);
}
