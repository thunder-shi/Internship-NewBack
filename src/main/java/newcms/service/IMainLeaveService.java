package newcms.service;

/**
 * 请假（MainLeave）业务。
 */
public interface IMainLeaveService {

    /**
     * 为一条请假记录创建首条待审核的 {@code MainVerifyProcess}。
     * <ul>
     *   <li>{@code MainVerifyProcess.relationId} = {@code MainLeave.id}</li>
     *   <li>{@code MainVerifyProcess.tableName} = {@code MainLeave}</li>
     *   <li>若已存在「提交待审核」或「审核通过」记录则直接返回（幂等）</li>
     *   <li>审核人：仅按 {@code verifyFirstRoleId} 解析；解析不到则系统自动通过</li>
     * </ul>
     *
     * @param leaveId        请假主键 {@code main_leave.id}
     * @param currentUserId 当前登录用户（一般为学生）
     */
    void ensureSubmitVerifyProcess(Integer leaveId, Integer currentUserId);

    /**
     * Creates the first leave audit record and binds it to a process config when available.
     *
     * @param leaveId         {@code main_leave.id}
     * @param currentUserId   current login user
     * @param processId       {@code rel_process_internship.id}, optional
     * @param processTypeCode process type code, optional
     */
    void ensureSubmitVerifyProcess(Integer leaveId, Integer currentUserId, Integer processId, String processTypeCode);
}
