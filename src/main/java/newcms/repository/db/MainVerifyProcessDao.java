package newcms.repository.db;

import newcms.entity.db.MainVerifyProcess;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MainVerifyProcessDao extends BaseDao<MainVerifyProcess, Integer> {
    List<MainVerifyProcess> findByRelationIdAndTableNameAndIsDeletedFalse(Integer relationId, String tableName);
}
