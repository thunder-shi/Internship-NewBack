package newcms.repository.db;

import newcms.entity.db.MainVerifyProcess;
import newcms.repository.base.BaseDao;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MainVerifyProcessDao extends BaseDao<MainVerifyProcess, Integer> {

    /**
     * 根据 relationId 查询所有审核记录，按 id 降序排列
     * @param relationId 关联的流程实例ID
     * @return 审核记录列表
     */
    List<MainVerifyProcess> findByRelationIdAndIsDeletedFalseOrderByIdDesc(Integer relationId);

    /**
     * 根据 relationId 查询最新的审核记录（id 最大的那条）
     * @param relationId 关联的流程实例ID
     * @return 最新的审核记录
     */
    Optional<MainVerifyProcess> findFirstByRelationIdAndIsDeletedFalseOrderByIdDesc(Integer relationId);

    /**
     * 根据 relationId 统计已通过的审核记录数量
     * @param relationId 关联的流程实例ID
     * @return 已通过的审核记录数量
     */
    @Query("SELECT COUNT(m) FROM MainVerifyProcess m WHERE m.relationId = ?1 AND m.isAudit = 1 AND m.isDeleted = false")
    int countPassedByRelationId(Integer relationId);

    /**
     * 根据 relationId 和 tableName 查询所有审核记录
     * @param relationId 关联的流程实例ID
     * @param tableName 表名
     * @return 审核记录列表
     */
    List<MainVerifyProcess> findByRelationIdAndTableNameAndIsDeletedFalseOrderByIdAsc(
            Integer relationId, String tableName);

    /**
     * 检查指定 relationId 和 tableName 是否已存在审核记录
     * @param relationId 关联的流程实例ID
     * @param tableName 表名
     * @return 是否存在
     */
    // boolean existsByRelationIdAndTableNameAndIsDeletedFalse(Integer relationId, String tableName);
}
