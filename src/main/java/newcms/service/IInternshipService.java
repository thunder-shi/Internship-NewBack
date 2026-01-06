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

    /**
     * 更新实习项目的流程配置（当模板类型变化时）
     * 先删除旧的流程配置，再复制新模板的流程配置
     * @param internshipId 实习项目ID
     * @param newInternshipTypeId 新的实习类型ID（模板ID）
     */
    void updateProcessFromTemplate(Integer internshipId, Integer newInternshipTypeId);
}
