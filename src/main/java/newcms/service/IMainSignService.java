package newcms.service;

/**
 * 实习打卡（MainSign）业务。
 */
public interface IMainSignService {

    /**
     * 为一条打卡记录创建首条待审核的 {@code MainVerifyProcess}（与日志提交后 {@code ensureDiaryVerifyProcess} 逻辑一致）。
     * <ul>
     *   <li>{@code MainVerifyProcess.relationId} = {@code MainSign.id}</li>
     *   <li>{@code MainVerifyProcess.tableName} = {@code MainSign}</li>
     *   <li>若已存在「提交待审核」或「审核通过」记录则直接返回（幂等）</li>
     *   <li>审核人：仅按 {@code verifyFirstRoleId} 解析；解析不到则 {@code verifyUserId = "系统自动通过"}、{@code isAudit = 通过}、{@code reason = "系统自动通过"}</li>
     * </ul>
     *
     * @param signId         打卡主键 {@code main_sign.id}
     * @param internshipId   实习项目 ID，用于在 {@code ViewRelProcessInternship} 中匹配 {@code processTypeCode = STUDENT_SIGN}，得到 {@code processId} 写入 {@code MainVerifyProcess}
     * @param currentUserId 当前登录用户（一般为学生）
     */
    void ensureSubmitVerifyProcess(Integer signId, Integer internshipId, Integer currentUserId);
}
