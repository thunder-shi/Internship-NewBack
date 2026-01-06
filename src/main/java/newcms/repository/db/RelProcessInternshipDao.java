package newcms.repository.db;

import newcms.entity.db.RelProcessInternship;
import newcms.repository.base.BaseDao;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface RelProcessInternshipDao extends BaseDao<RelProcessInternship, Integer> {
    /**
     * 根据实习项目ID删除流程配置（逻辑删除）
     * @param internshipId 实习项目ID
     */
    @Modifying
    @Query("update RelProcessInternship set isDeleted = true, updateTime = current_timestamp where internshipId = ?1 and isDeleted = false")
    void deleteByInternshipId(Integer internshipId);
}
