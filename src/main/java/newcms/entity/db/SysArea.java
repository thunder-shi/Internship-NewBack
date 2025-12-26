package newcms.entity.db;

import newcms.entity.base.BaseTreeInfo;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Entity;

/**
 * 区域信息表
 */
@Getter
@Setter
@Entity
public class SysArea extends BaseTreeInfo<SysArea> {
}
