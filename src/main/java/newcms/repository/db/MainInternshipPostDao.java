package newcms.repository.db;

import newcms.entity.db.MainInternshipPost;
import newcms.repository.base.BaseDao;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MainInternshipPostDao extends BaseDao<MainInternshipPost, Integer> {
    List<MainInternshipPost> findByInternshipIdAndIsDeletedFalse(Integer internshipId);

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
}
