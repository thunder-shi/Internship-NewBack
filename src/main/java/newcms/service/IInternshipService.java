package newcms.service;

import org.springframework.stereotype.Service;

@Service
public interface IInternshipService {
    /**
     * 根据实习类型模板复制流程配置到实习项目
     * @param internshipId 实习项目ID
     * @param internshipTypeId 实习类型ID（模板ID）
     */
    void copyProcessFromTemplate(Integer internshipId, Integer internshipTypeId);
}
