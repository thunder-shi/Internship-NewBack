package newcms.repository.db;

import newcms.entity.db.MainEnterpriseInfo;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MainEnterpriseInfoDao extends BaseDao<MainEnterpriseInfo, Integer> {
    List<MainEnterpriseInfo> findByCompanyIdAndIsDeletedFalseOrderByVersionNoDescIdDesc(Integer companyId);

    List<MainEnterpriseInfo> findByCompanyIdAndAuditStatusInAndIsDeletedFalseOrderByVersionNoDescIdDesc(
            Integer companyId, Collection<Integer> auditStatuses);

    MainEnterpriseInfo findFirstByCompanyIdAndIsCurrentTrueAndIsDeletedFalseOrderByVersionNoDescIdDesc(Integer companyId);

    /**
     * 同企业、指定审核状态下的一条记录：按「通过审核时间」倒序，其次版本号、主键（与「PASS 集合里时间上最新一条为有效」一致）。
     */
    MainEnterpriseInfo findFirstByCompanyIdAndAuditStatusAndIsDeletedFalseOrderByApprovedTimeDescVersionNoDescIdDesc(
            Integer companyId, Integer auditStatus);

    List<MainEnterpriseInfo> findBySchoolIdAndAuditStatusAndIsDeletedFalse(Integer schoolId, Integer auditStatus);
}
