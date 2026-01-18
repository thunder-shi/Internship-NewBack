package newcms.repository.db;

import newcms.entity.db.ViewRelProcessInternship;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ViewRelProcessInternshipDao extends BaseDao<ViewRelProcessInternship, Integer> {
    /**
     * 根据实习项目ID和流程类型名称查询流程配置
     */
    Optional<ViewRelProcessInternship> findByInternshipIdAndProcessTypeNameAndIsDeletedFalse(
            Integer internshipId, String processTypeName);
}
