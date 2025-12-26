package newcms.entity.base;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
@Getter
@Setter
public class OrderInfo extends BaseInfo {
    @Column(nullable = false,columnDefinition = "smallint default '1' comment '排序号'")
    private Integer theOrder = 1;
}
