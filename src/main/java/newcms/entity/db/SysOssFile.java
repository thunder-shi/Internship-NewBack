package newcms.entity.db;

import newcms.entity.base.BaseInfo;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;

/**
 * OSS文件信息表
 */
@Getter
@Setter
@Entity
@Accessors(chain = true)
public class SysOssFile extends BaseInfo {
    @Column(columnDefinition = "int unsigned not null comment '上传人Id'")
    private Integer userId;

    @Column(columnDefinition = "varchar(50) comment '文件夹名称'")
    private String bucketName;

    @Column(columnDefinition = "varchar(255) comment '文件名'")
    private String fileName;

    @Column(columnDefinition = "varchar(255) comment '文件在OSS上的具体路径'")
    private String ossPath;

    @Column(columnDefinition = "varchar(50) comment '后缀'")
    private String suffix;

    @Column(columnDefinition = "varchar(50) comment '文件大小'")
    private String fileSize;

    @Column(name = "RELATION_ID", columnDefinition = "int unsigned not null default 0")
    private Integer relationId = 0;  // 历史字段，保留以满足 NOT NULL 约束，业务逻辑使用 relationIds

    @Column(columnDefinition = "int unsigned not null comment '关联表id'")
    private Integer relationIds;

    @Column(columnDefinition = "varchar(50) comment '关联表名'")
    private String tableName;
}
