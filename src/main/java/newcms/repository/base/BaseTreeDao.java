package newcms.repository.base;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

@NoRepositoryBean
public interface BaseTreeDao<T, ID> extends BaseDao<T, ID> {
    /**
     * 查询子节点数据
     * @param parentId
     * @return
     */
    List<T> findByParentIdAndIsDeletedFalseOrderByTheOrder(ID parentId);
}
