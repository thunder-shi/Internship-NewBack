package newcms.service;

import com.alibaba.fastjson.JSONObject;
import org.springframework.data.domain.Sort;
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
     * 幂等创建「校外实习-自主实习虚拟岗位」。
     * <p>行为：</p>
     * <ol>
     *   <li>查 {@code main_internship_post} 是否已存在 {@code internshipId + code='SELF_INTERNSHIP'}：
     *       存在则返回 {@code {postId, created:false}}。</li>
     *   <li>否则插入一条虚拟岗位：{@code code='SELF_INTERNSHIP', name='自主实习',
     *       allPersonNum=-1, companyId=null, currentVerifyTypeId=NO_VERIFY(1)}。</li>
     *   <li>若该项目下存在 {@code EXTERNAL_ENTERPRISE_POST_DECLARATION} 流程节点，则为该岗位追加
     *       一条自动通过的 {@code MainVerifyProcess}；不存在则跳过（不报错）。</li>
     * </ol>
     *
     * @param internshipId 实习项目 id
     * @return {@code {postId, created:boolean}}
     */
    JSONObject createSelfInternshipPost(Integer internshipId);

    /**
     * 学生申请自主实习（幂等 + update-in-place）。
     * <p>规则：同一学生在同一实习项目下，自主实习记录至多 1 条；当前状态
     * {@code SAVE/SUBMIT/PASS/BACK} 时拒绝；{@code NOTPASS} 时
     * 复用原 {@code RelStuInternshipPost.id}，覆盖 self_* 字段、重置审核流程、
     * 并软删除该 relationId 下的所有附件（SysOssFile）。</p>
     * <p>自主实习不与企业岗位报名互斥；审核通过后也不触发级联删除其他企业岗位。</p>
     *
     * @return {@code {relStuInternshipPostId, isAudit, verifyTypeId, created}}
     */
    JSONObject applySelfInternship(Integer internshipId, Integer studentId,
                                   String selfCompanyName, String selfPostName,
                                   String selfAddress, String selfRemarks);

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
     * 推进审核步骤。{@code node} 可为单条 {@link com.alibaba.fastjson.JSONObject}、
     * {@link com.alibaba.fastjson.JSONArray}，或为二者的 JSON 字符串（如 {@code "[{\"id\":1},...]"}）。
     *
     * @param node 单条审核节点、节点数组或对应 JSON 文本
     * @return 单条时为保存后的实体；数组或与顺序一致的保存结果列表
     */
    Object auditProcess(Object node);

    /**
     * 学生端：查询本人最近一条“选题审核不通过”记录（含不通过理由）。
     * 若不存在，返回 null。
     */
    Object getLatestRejectedTitleSelection(Integer stuId);

    /**
     * 学生端：确认已知晓选题不通过后，删除对应 RelTitleStudent 记录及其审核记录。
     */
    Object acknowledgeRejectedTitleSelection(Integer relationId, Integer stuId);

    /**
     * Create a RelTitleStudent row with candidate/final-assignment isolation rules.
     */
    Object createRelTitleStudent(JSONObject node);

    /**
     * Atomically approve one student title candidate as the final title.
     */
    Object confirmStudentTopicSelection(JSONObject node);

    /**
     * 老师申报题目 / 师生互选-学生选题：新增关联记录后创建首条 MainVerifyProcess。
     * RelTitleTeacher、RelTeacherStudent 走「老师申报题目」流程；RelTitleStudent 走 INTERNAL_STUDENT_TEACHER_MATCH。
     * 需审核时 isAudit=-1（保存未提交），无需审核时 isAudit=1（直接通过）。
     */
    void createFirstVerifyProcessForRelTeacherStudent(Integer relationId, Integer internshipId, Integer createUserId, String tableName);

    /**
     * 根据业务表关联删除其 MainVerifyProcess 记录（如删除题目时清理审核记录）。
     */
    void deleteVerifyProcessByRelationIdAndTableName(Integer relationId, String tableName);

    /**
     * 根据实习项目和岗位编码获取可选用户列表（带分页）
     * 从 ViewBaseUser 中筛选尚未在 RelIntershipUser 关联到该实习项目的用户。
     * {@code jobCode=STUDENT} 时按学生过滤；{@code SCHOOL_TEACHER}/{@code COMPANY_TUTOR} 时查所有非学生（jobCode != STUDENT）。
     *
     * @param internshipId  实习项目ID
     * @param jobCode       岗位编码
     * @param departmentIds 部门 id 列表；服务端对每个 id 展开子树后取并集再过滤（与批量初始化接口的「仅末级 id」语义不同）
     * @param page         页码（从1开始）
     * @param size         每页数量
     * @param sort         排序规则
     * @return 可选用户分页结果
     */
    Object getAvailableUsersForInternship(Integer internshipId, String jobCode, List<Integer> departmentIds, Integer page, Integer size, Sort sort);

    /**
     * 按可选用户口径批量创建 RelIntershipUser，并同步创建 MainVerifyProcess(SAVE)。
     * {@code departmentIds} 为末级部门 id 数组，服务端仅按列表 IN 过滤，不展开部门子树（与 {@link #getAvailableUsersForInternship} 分页接口的子树行为不同）。
     */
    Object batchInitRelIntershipUserFromAvailable(Integer internshipId, String jobCode, List<Integer> departmentIds,
                                                  Integer processId, Integer createUserId, Integer verifyRoleId,
                                                  Integer currentVerifyTypeId);

    /**
     * 查询当前实习项目下可参与分配的老师（入项审核通过），按部门过滤。
     *
     * @param jobCode {@link newcms.base.Constant.USER_JOB_CODE#SCHOOL_TEACHER}（查所有非学生）
     *                或 {@link newcms.base.Constant.USER_JOB_CODE#COMPANY_TUTOR}（仅企业导师）
     */
    Object listAssignableTeachers(Integer internshipId, Integer departmentId, String jobCode);

    /**
     * 查询当前实习项目下可参与校内导师分配的学生（岗位审核通过且选岗审核通过），按部门精确过滤。
     * 校内导师已指定 teacherId 的不返回；空老师占位仍返回。
     */
    Object listAssignableStudents(Integer internshipId, Integer departmentId);

    /**
     * 根据实习项目为「校外分配校内导师」流程下仍处于待提交（SAVE）的师生记录均衡分配或重算 teacherId；不新建 MainVerifyProcess。
     * 已提交（非 SAVE）的记录不会进入分配范围。对已存在的 {@code MainVerifyProcess}（同 relationId、processId、RelTeacherStudent）
     * 写入本次传入的 {@code createUserId}、{@code verifyUserId}。
     *
     * @param currentVerifyTypeId 写入 RelTeacherStudent 的 currentVerifyTypeId；不传默认 1
     */
    Object initTeacherStudentByInternshipId(Integer internshipId, Integer processId, Integer createUserId, String verifyUserId,
                                            Integer currentVerifyTypeId);

    /**
     * 与 {@link #initTeacherStudentByInternshipId} 相同。
     */
    Object initInternalTutorByInternshipId(Integer internshipId, Integer processId, Integer createUserId, String verifyUserId,
                                           Integer currentVerifyTypeId);

    /**
     * 手动指定老师与学生：优先更新指定 processId 下已有 SAVE 占位的 teacherId；无占位则新建；已提交则跳过。
     */
    Object manualAssignTeacherStudent(Integer internshipId, Integer processId, Integer createUserId, String verifyUserId,
                                      Integer currentVerifyTypeId, Integer teacherId, List<Integer> studentIds);

    // /**
    //  * 获取当前进行中的实习项目
    //  * 根据流程类型代码查询当前时间范围内的实习项目
    //  * @param processTypeCode 流程类型代码
    //  * @return 符合条件的实习项目列表
    //  */
    // Object getNowInternship(String processTypeCode);

    /**
     * 本学院校外实习项目报名汇总。
     * <p>权限：{@code SUPER_ADMIN}、{@code SCHOOL_ADMIN}、{@code ACADEMIC_AFFAIRS_ADMIN} 可看全校（{@code departmentId} 为 {@code null} 时按用户学校全部部门；超级管理员无 school 时按全部部门）；
     * {@code DEPARTMENT_ADMIN} 仅本院系，忽略传入的 {@code departmentId}。</p>
     * <p>每行含 {@code signupStudentTotalCount}（项目报名学生总数）、{@code signupStudentCount}、
     * {@code pendingAuditPostCount}、{@code studentWithPostSelectionCount}（后三者为当前统计口径部门子树内统计）。</p>
     *
     * @param departmentId 学院/部门节点 ID（校级管理员可选，用于下钻子树）
     * @param page         页码，从 1 开始
     * @param size         每页条数
     */
    Object listExternalInternshipCollegeStats(Integer departmentId, Integer page, Integer size);

    /**
     * 指定校外实习项目下，审核已通过的企业岗位列表（含公司、招聘人数等）。
     *
     * @param internshipId 实习项目 ID
     * @param page         页码，从 1 开始；{@code null} 时与默认页大小见实现
     * @param size         每页条数；{@code null} 时使用默认大小
     */
    Object listApprovedExternalInternshipPosts(Integer internshipId, Integer page, Integer size);

    /**
     * 本学院校内实习项目汇总：报名师生数、题目审核通过数、未提交题目教师数、学生选题三类人数等。
     * <p>权限规则同 {@link #listExternalInternshipCollegeStats(Integer, Integer, Integer)}。</p>
     */
    Object listInternalInternshipCollegeStats(Integer departmentId, Integer page, Integer size);

    /**
     * 校内实习项目学生选题三类名单（与校外选岗 breakdown 用法类似）。
     * <p>{@code departmentId} 与 {@link #listInternalInternshipCollegeStats(Integer, Integer, Integer)} 一致：下钻某学院时传入同一节点；
     * 不传时按当前登录人权限使用全校或本院口径。</p>
     *
     * @param status {@code all}、{@code notSubmitted}、{@code pendingAudit}、{@code titleApproved}
     */
    Object getInternalInternshipTitleSelectionBreakdown(Integer internshipId, Integer page, Integer size, String status,
                                                        Integer departmentId);

    /**
     * 校内实习项目：尚未提交申报题目的教师列表。
     * <p>{@code departmentId} 规则同 {@link #listInternalInternshipCollegeStats(Integer, Integer, Integer)}（子树内报名教师）。</p>
     */
    Object listInternalInternshipTeachersNotSubmittedTopic(Integer internshipId, Integer departmentId, Integer page, Integer size);

    /**
     * 指定校外实习项目：学生选岗情况（视图 view_external_internship_student_post_breakdown；部门权限仍在服务端过滤）。
     * <p>权限规则同 {@link #listExternalInternshipCollegeStats(Integer, Integer, Integer)}。</p>
     *
     * @param status {@code all}、{@code notSelected}（未报名）、{@code selected}（已报名=有任意选岗记录）、
     *               {@code selectedPendingAudit}、{@code postApproved}；{@code counts} 始终含上述细分与 {@code selected}
     */
    Object getExternalInternshipStudentPostBreakdown(Integer internshipId, Integer page, Integer size, String status,
                                                     Integer departmentId);

    /**
     * 校外实习系统分配：将实习项目安排中尚未选岗的学生，随机报名到审核通过且有空位的企业岗位（复用 {@code stuSelPost}）。
     * <p>学生口径与 {@link #getExternalInternshipStudentPostBreakdown} 的 {@code notSelected} 一致。</p>
     */
    Object randomAssignPostsForUnselectedStudents(Integer internshipId);
}
