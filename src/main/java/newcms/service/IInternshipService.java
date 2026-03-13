package newcms.service;

import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface IInternshipService {

    // ==================== 实习项目管理（无需审核） ====================

    /**
     * 新增实习项目
     * 创建实习项目并自动从实习类型模板复制流程配置到 RelProcessInternship
     *
     * @param node 实习项目数据，必须包含 internshipTypeId
     * @return 保存后的实习项目实体
     */
    Object addNewInternship(JSONObject node);

    /**
     * 删除实习项目
     * 同时删除关联的 RelProcessInternship 记录
     * 注意：已进入审核流程的项目无法删除
     *
     * @param internshipIds 实习项目ID列表
     * @return 删除结果
     */
    Object deleteNewInternship(List<Integer> internshipIds);

    // ==================== 实习计划流程（需要审核） ====================

    // 已注释：提交实习计划（创建审核记录）方法
    // Object submitInternshipPlan(JSONObject requestJson);

    // ========================= 推进审核步骤 =========================

    /**
     * 推进审核步骤
     * @param node
     * @return 保存后的审核信息
     */
    Object auditProcess(JSONObject node);

    /**
     * 老师申报题目：新增 RelTeacherStudent 后创建首条 MainVerifyProcess。
     * 需审核时 isAudit=-1（保存未提交），无需审核时 isAudit=1（直接通过）。
     */
    void createFirstVerifyProcessForRelTeacherStudent(Integer relationId, Integer internshipId, Integer createUserId);

    /**
     * 根据业务表关联删除其 MainVerifyProcess 记录（如删除题目时清理审核记录）。
     */
    void deleteVerifyProcessByRelationIdAndTableName(Integer relationId, String tableName);

    // /**
    //  * 获取当前进行中的实习项目
    //  * 根据流程类型代码查询当前时间范围内的实习项目
    //  * @param processTypeCode 流程类型代码
    //  * @return 符合条件的实习项目列表
    //  */
    // Object getNowInternship(String processTypeCode);
}
