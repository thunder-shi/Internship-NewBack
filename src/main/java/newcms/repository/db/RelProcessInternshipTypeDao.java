package newcms.repository.db;

import newcms.entity.db.RelProcessInternshipType;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RelProcessInternshipTypeDao extends BaseDao<RelProcessInternshipType, Integer> {
    /**
     * 根据实习类型ID查询流程配置
     * @param internshipTypeId 实习类型ID
     * @return 流程配置列表
     */
    List<RelProcessInternshipType> findByInternshipTypeIdAndIsDeletedFalse(Integer internshipTypeId);
}

