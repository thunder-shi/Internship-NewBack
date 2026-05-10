package newcms.repository.db;

import newcms.entity.db.MainInternshipPost;
import newcms.repository.base.BaseDao;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MainInternshipPostDao extends BaseDao<MainInternshipPost, Integer> {
    List<MainInternshipPost> findByInternshipIdAndIsDeletedFalse(Integer internshipId);

    /** 按 internshipId + code 找第一条未删除岗位（自主实习虚拟岗位查重用）。 */
    Optional<MainInternshipPost> findFirstByInternshipIdAndCodeAndIsDeletedFalse(Integer internshipId, String code);

    /**
     * 原子性扣减 nowPersonNum（当前人数 > 0 时才更新），返回影响行数。
     */
    @Modifying
    @Query("UPDATE MainInternshipPost p SET p.nowPersonNum = p.nowPersonNum - 1 WHERE p.id = :id AND p.nowPersonNum > 0 AND p.isDeleted = false")
    int decrementNowPersonNum(@Param("id") Integer id);

    /**
     * 原子性增加 nowPersonNum（当前人数 < allPersonNum 时才更新），返回影响行数。
     * 影响行数 = 0 表示岗位已满。
     */
    @Modifying
    @Query("UPDATE MainInternshipPost p SET p.nowPersonNum = p.nowPersonNum + 1 WHERE p.id = :id AND p.nowPersonNum < p.allPersonNum AND p.isDeleted = false")
    int incrementNowPersonNumIfNotFull(@Param("id") Integer id);

    /**
     * 无条件原子性增加 nowPersonNum（审核通过后调用），返回影响行数。
     * clearAutomatically = true 确保后续读取不使用 JPA 一级缓存的旧值（避免 cancelPendingApplicationsIfPostFull 误判）。
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE MainInternshipPost p SET p.nowPersonNum = p.nowPersonNum + 1 WHERE p.id = :id AND p.isDeleted = false")
    int incrementNowPersonNum(@Param("id") Integer id);
}
