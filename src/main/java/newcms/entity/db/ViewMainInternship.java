package newcms.entity.db;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.NameRemarkOrderInfo;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
public class ViewMainInternship extends NameRemarkOrderInfo {
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer internshipTypeId;
    private String internshipTypeName;
    private Integer intTypeId;
    private String intTypeName;
    private Integer creatorId;
    private String creatorName;
    private String cron;
    private LocalDate reportStartDate;
    private LocalDate reportEndDate;
    private Integer studentNum;
}
