package newcms.entity.db;


import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import newcms.entity.base.OrderInfo;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public class ViewRelStuInternship extends OrderInfo {
    private Integer studentId;
    private Integer postId;
    private Integer internshipId;
    private Integer round;
    private Integer sort;
    private String studentName;
    private String internshipPostName;
    private String internshipName;
}
