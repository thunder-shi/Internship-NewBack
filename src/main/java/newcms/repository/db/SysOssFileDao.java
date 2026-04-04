package newcms.repository.db;

import newcms.entity.db.SysOssFile;
import newcms.repository.base.BaseDao;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SysOssFileDao extends BaseDao<SysOssFile, Integer> {

    List<SysOssFile> getByIdInAndIsDeletedFalse(Iterable<Integer> fileIds);

    Optional<SysOssFile> findByIdAndIsDeletedFalse(Integer fileId);

    /** 按业务记录 + 表名查询该记录下的所有文件 */
    List<SysOssFile> findByRelationIdsAndTableNameAndIsDeletedFalse(Integer relationIds, String tableName);

    /** 按业务记录查询所有文件（不限表） */
    List<SysOssFile> findByRelationIdsAndIsDeletedFalse(Integer relationIds);

    /** 批量按业务记录查询 */
    List<SysOssFile> findByRelationIdsInAndIsDeletedFalse(List<Integer> relationIds);

    @Modifying
    @Query(nativeQuery = true, value = "update sys_oss_file set is_deleted = 1 where id in ?1")
    void updateByIds(List<Integer> ids);
}
