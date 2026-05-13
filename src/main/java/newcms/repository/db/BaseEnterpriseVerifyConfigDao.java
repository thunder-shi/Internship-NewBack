package newcms.repository.db;

import newcms.entity.db.BaseEnterpriseVerifyConfig;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

@Repository
public interface BaseEnterpriseVerifyConfigDao extends BaseDao<BaseEnterpriseVerifyConfig, Integer> {
    BaseEnterpriseVerifyConfig findFirstBySchoolIdAndIsDeletedFalse(Integer schoolId);

    BaseEnterpriseVerifyConfig findTopByIsDeletedFalseOrderByIdDesc();
}
