package newcms.repository.db;

import newcms.entity.db.InternshipGradeConfigItem;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InternshipGradeConfigItemDao extends BaseDao<InternshipGradeConfigItem, Integer> {

    /** 查询某实习项目下某业务表的全部已生效评分项，按 levelOrder + orderNum 排序 */
    List<InternshipGradeConfigItem>
        findByInternshipIdAndSourceTableAndIsDeletedFalseOrderByLevelOrderAscOrderNumAsc(
                Integer internshipId, String sourceTable);

    /** 查同 (internshipId, sourceTable, levelOrder) 下唯一一条 */
    Optional<InternshipGradeConfigItem>
        findByInternshipIdAndSourceTableAndLevelOrderAndIsDeletedFalse(
                Integer internshipId, String sourceTable, Integer levelOrder);

    /** 列举某实习项目下所有已配置的 sourceTable（用于审核进行中守卫的逆向检查不常用，但留口） */
    List<InternshipGradeConfigItem> findByInternshipIdAndIsDeletedFalse(Integer internshipId);
}
