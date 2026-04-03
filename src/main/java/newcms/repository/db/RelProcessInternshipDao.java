package newcms.repository.db;

import newcms.entity.db.RelProcessInternship;
import newcms.repository.base.BaseDao;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RelProcessInternshipDao extends BaseDao<RelProcessInternship, Integer> {
    // /**
    //  * 根据实习项目ID删除流程配置（逻辑删除）
    //  * @param internshipId 实习项目ID
    //  */
    // @Modifying
    // @Query("update RelProcessInternship set isDeleted = true, updateTime = current_timestamp where internshipId = ?1 and isDeleted = false")
    // void deleteByInternshipId(Integer internshipId);

    /**
     * 查找已到开始时间的流程记录
     * @param now 当前时间
     * @return 已到开始时间且未删除的流程列表
     */
    // @Query("SELECT r FROM RelProcessInternship r WHERE r.startTime IS NOT NULL AND r.startTime <= ?1 AND r.isDeleted = false")
    // List<RelProcessInternship> findStartedProcesses(LocalDateTime now);

    List<RelProcessInternship> findByInternshipIdAndIsDeletedFalse(Integer internshipId);
}
