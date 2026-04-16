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
     * 从 ViewBaseUser 中筛选出 jobCode 匹配且尚未在 RelIntershipUser 中关联到该实习项目的用户
     *
     * @param internshipId 实习项目ID
     * @param jobCode      岗位编码
     * @param departmentId 部门ID（可选）
     * @param page         页码（从1开始）
     * @param size         每页数量
     * @param sort         排序规则
     * @return 可选用户分页结果
     */
    Object getAvailableUsersForInternship(Integer internshipId, String jobCode, Integer departmentId, Integer page, Integer size, Sort sort);

    /**
     * 查询当前实习项目下可参与系统分配的校内导师（审核通过），按部门树过滤。
     */
    Object listAssignableTeachers(Integer internshipId, Integer departmentId);

    /**
     * 查询当前实习项目下可参与系统分配的学生（岗位审核通过且选岗审核通过），按部门树过滤。
     */
    Object listAssignableStudents(Integer internshipId, Integer departmentId);

    /**
     * 根据 internshipId 批量初始化 RelTeacherStudent 及其审核记录。
     *
     * @param tutorAssignKind 导师类型，见 {@link newcms.base.Constant.TUTOR_ASSIGN_KIND}；
     *                        传 {@code null} 时与 {@link newcms.base.Constant.TUTOR_ASSIGN_KIND#INTERNAL} 相同语义（校内导师）
     * @param currentVerifyTypeId 新建 RelTeacherStudent 的 currentVerifyTypeId；不传默认 1
     */
    Object initTeacherStudentByInternshipId(Integer internshipId, Integer processId, Integer createUserId, String verifyUserId,
                                            Integer tutorAssignKind, Integer currentVerifyTypeId);

    /**
     * 校内导师初始化：支持待审核重分配 + 新增学生增量补建（teacherId 自动分配）。
     */
    Object initInternalTutorByInternshipId(Integer internshipId, Integer processId, Integer createUserId, String verifyUserId,
                                           Integer currentVerifyTypeId);

    /**
     * 企业导师初始化：每次调用自动识别新增学生并增量补建（teacherId=0 占位，后续手动分配）。
     */
    Object initEnterpriseTutorByInternshipId(Integer internshipId, Integer processId, Integer createUserId, String verifyUserId,
                                             Integer currentVerifyTypeId);

    /**
     * 手动指定单个老师和多个学生，批量创建 RelTeacherStudent 及其审核记录。
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
     * 本学院校外实习项目报名汇总（按部门树：含 departmentId 及其全部子部门）。
     *
     * @param departmentId 学院/部门节点 ID（与 BaseDepartment 树一致）
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
     * 指定校外实习项目：学生选岗情况。{@code counts} 始终为三类全量人数。
     * <ul>
     *   <li>{@code status=all}：不分状态，返回本项目内全部学生一条列表并分页；每条含 {@code selectionStatus}。</li>
     *   <li>其余三个值：仅查询并分页返回该状态对应的学生 {@code rows}。</li>
     * </ul>
     *
     * @param internshipId 实习项目 ID
     * @param page         页码，从 1 开始
     * @param size         每页条数
     * @param status       {@code all}、{@code notSelected}、{@code selectedPendingAudit}、{@code postApproved}；{@code null} 视为 {@code all}
     * @param departmentId 可选；若传则只统计用户所属部门为该节点或其下级部门（与 BaseDepartment 树一致）的学生；{@code null} 不按部门过滤
     */
    Object getExternalInternshipStudentPostBreakdown(Integer internshipId, Integer page, Integer size, String status,
                                                     Integer departmentId);
}
