package newcms.repository.base;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;

/**
 * 审核基础Dao（多级审核）
 * isAudit状态说明：
 *   -1：保存未提交
 *    0：未审核（待院系/企业审核）
 *    1：院系/企业审核通过（待教务处审核）
 *    2：院系/企业审核未通过
 *    3：院系/企业审核退回
 *    4：教务处审核通过（待学校审核）
 *    5：教务处审核未通过
 *    6：教务处审核退回
 *    7：学校审核通过（最终通过）
 *    8：学校审核未通过
 *    9：学校审核退回
 *
 * @author wang zhengqi
 */
@NoRepositoryBean
public interface BaseAuditDao<T, ID> extends BaseDao<T, ID> {

    // ==================== 提交审核 ====================

    /**
     * 提交审核（-1 -> 0）
     */
    @Transactional(rollbackFor = SQLException.class)
    @Modifying
    @Query("update #{#entityName} set isAudit = 0, updateTime = current_timestamp where id = ?1 and isAudit = -1")
    int submitAudit(ID id);

    /**
     * 批量提交审核
     */
    @Transactional(rollbackFor = SQLException.class)
    @Modifying
    @Query("update #{#entityName} set isAudit = 0, updateTime = current_timestamp where id in ?1 and isAudit = -1")
    int submitAuditByIdIn(Iterable<ID> ids);

    // ==================== 院系/企业审核（0 -> 1/2/3）====================

    /**
     * 院系/企业审核通过（0 -> 1）
     */
    @Transactional(rollbackFor = SQLException.class)
    @Modifying
    @Query("update #{#entityName} set isAudit = 1, reason = ?2, auditUserId = ?3, auditTime = current_timestamp, updateTime = current_timestamp where id = ?1 and isAudit = 0")
    int deptAuditPass(ID id, String reason, Integer auditUserId);

    /**
     * 院系/企业审核未通过（0 -> 2）
     */
    @Transactional(rollbackFor = SQLException.class)
    @Modifying
    @Query("update #{#entityName} set isAudit = 2, reason = ?2, auditUserId = ?3, auditTime = current_timestamp, updateTime = current_timestamp where id = ?1 and isAudit = 0")
    int deptAuditReject(ID id, String reason, Integer auditUserId);

    /**
     * 院系/企业审核退回（0 -> 3）
     */
    @Transactional(rollbackFor = SQLException.class)
    @Modifying
    @Query("update #{#entityName} set isAudit = 3, reason = ?2, auditUserId = ?3, auditTime = current_timestamp, updateTime = current_timestamp where id = ?1 and isAudit = 0")
    int deptAuditReturn(ID id, String reason, Integer auditUserId);

    /**
     * 批量院系/企业审核
     */
    @Transactional(rollbackFor = SQLException.class)
    @Modifying
    @Query("update #{#entityName} set isAudit = ?2, reason = ?3, auditUserId = ?4, auditTime = current_timestamp, updateTime = current_timestamp where id in ?1 and isAudit = 0")
    int deptAuditByIdIn(Iterable<ID> ids, Integer isAudit, String reason, Integer auditUserId);

    // ==================== 教务处审核（1 -> 4/5/6）====================

    /**
     * 教务处审核通过（1 -> 4）
     */
    @Transactional(rollbackFor = SQLException.class)
    @Modifying
    @Query("update #{#entityName} set isAudit = 4, reason = ?2, auditUserId = ?3, auditTime = current_timestamp, updateTime = current_timestamp where id = ?1 and isAudit = 1")
    int academicAuditPass(ID id, String reason, Integer auditUserId);

    /**
     * 教务处审核未通过（1 -> 5）
     */
    @Transactional(rollbackFor = SQLException.class)
    @Modifying
    @Query("update #{#entityName} set isAudit = 5, reason = ?2, auditUserId = ?3, auditTime = current_timestamp, updateTime = current_timestamp where id = ?1 and isAudit = 1")
    int academicAuditReject(ID id, String reason, Integer auditUserId);

    /**
     * 教务处审核退回（1 -> 6）
     */
    @Transactional(rollbackFor = SQLException.class)
    @Modifying
    @Query("update #{#entityName} set isAudit = 6, reason = ?2, auditUserId = ?3, auditTime = current_timestamp, updateTime = current_timestamp where id = ?1 and isAudit = 1")
    int academicAuditReturn(ID id, String reason, Integer auditUserId);

    /**
     * 批量教务处审核
     */
    @Transactional(rollbackFor = SQLException.class)
    @Modifying
    @Query("update #{#entityName} set isAudit = ?2, reason = ?3, auditUserId = ?4, auditTime = current_timestamp, updateTime = current_timestamp where id in ?1 and isAudit = 1")
    int academicAuditByIdIn(Iterable<ID> ids, Integer isAudit, String reason, Integer auditUserId);

    // ==================== 学校审核（4 -> 7/8/9）====================

    /**
     * 学校审核通过（4 -> 7）
     */
    @Transactional(rollbackFor = SQLException.class)
    @Modifying
    @Query("update #{#entityName} set isAudit = 7, reason = ?2, auditUserId = ?3, auditTime = current_timestamp, updateTime = current_timestamp where id = ?1 and isAudit = 4")
    int schoolAuditPass(ID id, String reason, Integer auditUserId);

    /**
     * 学校审核未通过（4 -> 8）
     */
    @Transactional(rollbackFor = SQLException.class)
    @Modifying
    @Query("update #{#entityName} set isAudit = 8, reason = ?2, auditUserId = ?3, auditTime = current_timestamp, updateTime = current_timestamp where id = ?1 and isAudit = 4")
    int schoolAuditReject(ID id, String reason, Integer auditUserId);

    /**
     * 学校审核退回（4 -> 9）
     */
    @Transactional(rollbackFor = SQLException.class)
    @Modifying
    @Query("update #{#entityName} set isAudit = 9, reason = ?2, auditUserId = ?3, auditTime = current_timestamp, updateTime = current_timestamp where id = ?1 and isAudit = 4")
    int schoolAuditReturn(ID id, String reason, Integer auditUserId);

    /**
     * 批量学校审核
     */
    @Transactional(rollbackFor = SQLException.class)
    @Modifying
    @Query("update #{#entityName} set isAudit = ?2, reason = ?3, auditUserId = ?4, auditTime = current_timestamp, updateTime = current_timestamp where id in ?1 and isAudit = 4")
    int schoolAuditByIdIn(Iterable<ID> ids, Integer isAudit, String reason, Integer auditUserId);

    // ==================== 重新提交（退回后）====================

    /**
     * 院系退回后重新提交（3 -> 0）
     */
    @Transactional(rollbackFor = SQLException.class)
    @Modifying
    @Query("update #{#entityName} set isAudit = 0, updateTime = current_timestamp where id = ?1 and isAudit = 3")
    int resubmitFromDeptReturn(ID id);

    /**
     * 教务处退回后重新提交（6 -> 1，回到待教务处审核）
     */
    @Transactional(rollbackFor = SQLException.class)
    @Modifying
    @Query("update #{#entityName} set isAudit = 1, updateTime = current_timestamp where id = ?1 and isAudit = 6")
    int resubmitFromAcademicReturn(ID id);

    /**
     * 学校退回后重新提交（9 -> 4，回到待学校审核）
     */
    @Transactional(rollbackFor = SQLException.class)
    @Modifying
    @Query("update #{#entityName} set isAudit = 4, updateTime = current_timestamp where id = ?1 and isAudit = 9")
    int resubmitFromSchoolReturn(ID id);

    // ==================== 查询方法 ====================

    /**
     * 根据审核状态查询
     */
    List<T> findByIsAuditAndIsDeletedFalse(Integer isAudit);

    /**
     * 查询保存未提交的数据（-1）
     */
    @Query("select t from #{#entityName} t where t.isAudit = -1 and t.isDeleted = false")
    List<T> findDraft();

    /**
     * 查询待院系/企业审核的数据（0）
     */
    @Query("select t from #{#entityName} t where t.isAudit = 0 and t.isDeleted = false")
    List<T> findPendingDeptAudit();

    /**
     * 查询院系/企业审核通过的数据（1，待教务处审核）
     */
    @Query("select t from #{#entityName} t where t.isAudit = 1 and t.isDeleted = false")
    List<T> findDeptPassed();

    /**
     * 查询院系/企业审核未通过的数据（2）
     */
    @Query("select t from #{#entityName} t where t.isAudit = 2 and t.isDeleted = false")
    List<T> findDeptRejected();

    /**
     * 查询院系/企业审核退回的数据（3）
     */
    @Query("select t from #{#entityName} t where t.isAudit = 3 and t.isDeleted = false")
    List<T> findDeptReturned();

    /**
     * 查询教务处审核通过的数据（4，待学校审核）
     */
    @Query("select t from #{#entityName} t where t.isAudit = 4 and t.isDeleted = false")
    List<T> findAcademicPassed();

    /**
     * 查询教务处审核未通过的数据（5）
     */
    @Query("select t from #{#entityName} t where t.isAudit = 5 and t.isDeleted = false")
    List<T> findAcademicRejected();

    /**
     * 查询教务处审核退回的数据（6）
     */
    @Query("select t from #{#entityName} t where t.isAudit = 6 and t.isDeleted = false")
    List<T> findAcademicReturned();

    /**
     * 查询学校审核通过的数据（7，最终通过）
     */
    @Query("select t from #{#entityName} t where t.isAudit = 7 and t.isDeleted = false")
    List<T> findSchoolPassed();

    /**
     * 查询学校审核未通过的数据（8）
     */
    @Query("select t from #{#entityName} t where t.isAudit = 8 and t.isDeleted = false")
    List<T> findSchoolRejected();

    /**
     * 查询学校审核退回的数据（9）
     */
    @Query("select t from #{#entityName} t where t.isAudit = 9 and t.isDeleted = false")
    List<T> findSchoolReturned();

    /**
     * 查询最终审核通过的数据（7）
     */
    @Query("select t from #{#entityName} t where t.isAudit = 7 and t.isDeleted = false")
    List<T> findFinalPassed();

    /**
     * 查询所有未通过的数据（2, 5, 8）
     */
    @Query("select t from #{#entityName} t where t.isAudit in (2, 5, 8) and t.isDeleted = false")
    List<T> findAllRejected();

    /**
     * 查询所有退回的数据（3, 6, 9）
     */
    @Query("select t from #{#entityName} t where t.isAudit in (3, 6, 9) and t.isDeleted = false")
    List<T> findAllReturned();

    /**
     * 查询某审核人审核过的数据
     */
    @Query("select t from #{#entityName} t where t.auditUserId = ?1 and t.isDeleted = false")
    List<T> findByAuditUserId(Integer auditUserId);

    // ==================== 兼容旧方法（已废弃）====================

    /**
     * @deprecated 使用 deptAuditPass 替代
     */
    @Deprecated
    default int auditPass(ID id, String reason, Integer auditUserId) {
        return deptAuditPass(id, reason, auditUserId);
    }

    /**
     * @deprecated 使用 deptAuditReject 替代
     */
    @Deprecated
    default int auditReject(ID id, String reason, Integer auditUserId) {
        return deptAuditReject(id, reason, auditUserId);
    }

    /**
     * @deprecated 使用 deptAuditReturn 替代
     */
    @Deprecated
    default int auditReturn(ID id, String reason, Integer auditUserId) {
        return deptAuditReturn(id, reason, auditUserId);
    }

    /**
     * @deprecated 使用 findPendingDeptAudit 替代
     */
    @Deprecated
    default List<T> findPendingAudit() {
        return findPendingDeptAudit();
    }

    /**
     * @deprecated 使用 findFinalPassed 或 findSchoolPassed 替代
     */
    @Deprecated
    default List<T> findAuditPassed() {
        return findFinalPassed();
    }

    /**
     * @deprecated 使用 findAllRejected 替代
     */
    @Deprecated
    default List<T> findAuditRejected() {
        return findAllRejected();
    }

    /**
     * @deprecated 使用 findAllReturned 替代
     */
    @Deprecated
    default List<T> findAuditReturned() {
        return findAllReturned();
    }

    /**
     * @deprecated 使用 resubmitFromDeptReturn 替代
     */
    @Deprecated
    default int resubmitAudit(ID id) {
        return resubmitFromDeptReturn(id);
    }

    /**
     * @deprecated 使用 deptAuditByIdIn 替代
     */
    @Deprecated
    default int auditByIdIn(Iterable<ID> ids, Integer isAudit, String reason, Integer auditUserId) {
        return deptAuditByIdIn(ids, isAudit, reason, auditUserId);
    }
}
