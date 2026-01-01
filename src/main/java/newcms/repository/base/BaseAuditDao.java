package newcms.repository.base;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;

/**
 * 审核基础Dao
 * isAudit: -1=保存未提交, 0=未审核, 1=审核通过, 2=审核未通过, 3=审核退回
 * @author wang zhengqi
 */
@NoRepositoryBean
public interface BaseAuditDao<T, ID> extends BaseDao<T, ID> {

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

    /**
     * 审核通过
     */
    @Transactional(rollbackFor = SQLException.class)
    @Modifying
    @Query("update #{#entityName} set isAudit = 1, reason = ?2, auditUserId = ?3, auditTime = current_timestamp, updateTime = current_timestamp where id = ?1 and isAudit = 0")
    int auditPass(ID id, String reason, Integer auditUserId);

    /**
     * 审核未通过
     */
    @Transactional(rollbackFor = SQLException.class)
    @Modifying
    @Query("update #{#entityName} set isAudit = 2, reason = ?2, auditUserId = ?3, auditTime = current_timestamp, updateTime = current_timestamp where id = ?1 and isAudit = 0")
    int auditReject(ID id, String reason, Integer auditUserId);

    /**
     * 审核退回
     */
    @Transactional(rollbackFor = SQLException.class)
    @Modifying
    @Query("update #{#entityName} set isAudit = 3, reason = ?2, auditUserId = ?3, auditTime = current_timestamp, updateTime = current_timestamp where id = ?1 and isAudit = 0")
    int auditReturn(ID id, String reason, Integer auditUserId);

    /**
     * 查询保存未提交的数据
     */
    List<T> findByIsAuditAndIsDeletedFalse(Integer isAudit);

    /**
     * 查询待审核数据
     */
    @Query("select t from #{#entityName} t where t.isAudit = 0 and t.isDeleted = false")
    List<T> findPendingAudit();

    /**
     * 查询审核通过的数据
     */
    @Query("select t from #{#entityName} t where t.isAudit = 1 and t.isDeleted = false")
    List<T> findAuditPassed();

    /**
     * 查询审核未通过的数据
     */
    @Query("select t from #{#entityName} t where t.isAudit = 2 and t.isDeleted = false")
    List<T> findAuditRejected();

    /**
     * 查询审核退回的数据
     */
    @Query("select t from #{#entityName} t where t.isAudit = 3 and t.isDeleted = false")
    List<T> findAuditReturned();

    /**
     * 重新提交审核（退回后再次提交：3 -> 0）
     */
    @Transactional(rollbackFor = SQLException.class)
    @Modifying
    @Query("update #{#entityName} set isAudit = 0, updateTime = current_timestamp where id = ?1 and isAudit = 3")
    int resubmitAudit(ID id);

    /**
     * 通用批量审核（可指定审核结果：1=通过, 2=未通过, 3=退回）
     */
    @Transactional(rollbackFor = SQLException.class)
    @Modifying
    @Query("update #{#entityName} set isAudit = ?2, reason = ?3, auditUserId = ?4, auditTime = current_timestamp, updateTime = current_timestamp where id in ?1 and isAudit = 0")
    int auditByIdIn(Iterable<ID> ids, Integer isAudit, String reason, Integer auditUserId);

    /**
     * 查询某审核人审核过的数据
     */
    @Query("select t from #{#entityName} t where t.auditUserId = ?1 and t.isDeleted = false")
    List<T> findByAuditUserId(Integer auditUserId);
}
