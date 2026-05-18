package newcms.repository.db;

import newcms.entity.db.RelTitleStudent;
import newcms.repository.base.BaseDao;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 学生职务关联题目表DAO
 */
@Repository
public interface RelTitleStudentDao extends BaseDao<RelTitleStudent, Integer> {
    List<RelTitleStudent> findByTitleIdAndIsDeletedFalse(Integer titleId);

    List<RelTitleStudent> findByStuIdAndIsDeletedFalse(Integer stuId);

    List<RelTitleStudent> findByStuIdAndInternshipIdAndIsDeletedFalse(Integer stuId, Integer internshipId);

    List<RelTitleStudent> findByTitleIdAndIsFinalAndIsDeletedFalse(Integer titleId, Integer isFinal);

    List<RelTitleStudent> findByStuIdAndInternshipIdAndIsFinalAndIsDeletedFalse(
            Integer stuId, Integer internshipId, Integer isFinal);

    List<RelTitleStudent> findByTitleIdAndStuIdAndIsDeletedFalse(Integer titleId, Integer stuId);

    /** 评分配置查询：列出某实习项目下全部师生关联 */
    List<RelTitleStudent> findByInternshipIdAndIsDeletedFalse(Integer internshipId);
}

