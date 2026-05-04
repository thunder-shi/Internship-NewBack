package newcms.repository.db;

import newcms.entity.db.ViewRelProcessInternship;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ViewRelProcessInternshipDao extends BaseDao<ViewRelProcessInternship, Integer> {
    /**
     * 根据实习项目ID和流程类型名称查询流程配置
     */
    Optional<ViewRelProcessInternship> findByInternshipIdAndProcessTypeNameAndIsDeletedFalse(
            Integer internshipId, String processTypeName);

    /**
     * 按实习项目与流程类型代码查询流程配置（排序与列表接口一致）
     */
    List<ViewRelProcessInternship> findByInternshipIdAndProcessTypeCodeAndIsDeletedFalseOrderByTheOrderAsc(
            Integer internshipId, String processTypeCode);
}
