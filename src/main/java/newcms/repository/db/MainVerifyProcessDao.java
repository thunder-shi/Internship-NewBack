package newcms.repository.db;

import newcms.entity.db.MainVerifyProcess;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MainVerifyProcessDao extends BaseDao<MainVerifyProcess, Integer> {
    List<MainVerifyProcess> findByRelationIdAndTableNameAndIsDeletedFalse(Integer relationId, String tableName);

    List<MainVerifyProcess> findByRelationIdAndTableNameAndIsAuditInAndIsDeletedFalse(
            Integer relationId, String tableName, Collection<Integer> isAudits);

    /** 批量按业务记录主键 + 表名查询审核行（避免 N+1） */
    List<MainVerifyProcess> findByRelationIdInAndTableNameAndIsDeletedFalse(
            Collection<Integer> relationIds, String tableName);
}
