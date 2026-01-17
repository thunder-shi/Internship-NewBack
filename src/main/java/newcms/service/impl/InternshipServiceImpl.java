package newcms.service.impl;

import jakarta.annotation.Resource;
import newcms.entity.db.RelProcessInternship;
import newcms.entity.db.RelProcessInternshipType;
import newcms.repository.db.RelProcessInternshipDao;
import newcms.repository.db.RelProcessInternshipTypeDao;
import newcms.service.IInternshipService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(rollbackFor = Exception.class)
public class InternshipServiceImpl implements IInternshipService {

    @Resource
    private RelProcessInternshipTypeDao relProcessInternshipTypeDao;

    @Resource
    private RelProcessInternshipDao relProcessInternshipDao;

    @Override
    public void copyProcessFromTemplate(Integer internshipId, Integer internshipTypeId) {
        if (internshipId == null || internshipTypeId == null) {
            return;
        }

        // 1. 根据实习类型ID查找对应的流程模板配置
        List<RelProcessInternshipType> processTypeList =
            relProcessInternshipTypeDao.findByInternshipTypeIdAndIsDeletedFalse(internshipTypeId);

        // 2. 将流程模板复制到实习项目流程关联表
        for (RelProcessInternshipType processType : processTypeList) {
            RelProcessInternship processInternship = new RelProcessInternship();
            processInternship.setInternshipId(internshipId);
            processInternship.setProcessTypeId(processType.getProcessTypeId());
            processInternship.setVerifyTypeId(processType.getVerifyTypeId());
            processInternship.setVerifyFirstRoleId(processType.getVerifyFirstRoleId());
            processInternship.setVerifySecondRoleId(processType.getVerifySecondRoleId());
            processInternship.setVerifyThirdRoleId(processType.getVerifyThirdRoleId());
            processInternship.setVerifyFourthRoleId(processType.getVerifyFourthRoleId());
            processInternship.setVerifyFifthRoleId(processType.getVerifyFifthRoleId());
            // 复制流程排序号
            processInternship.setTheOrder(processType.getTheOrder());

            relProcessInternshipDao.save(processInternship);
        }
    }

    @Override
    public void updateProcessFromTemplate(Integer internshipId, Integer newInternshipTypeId) {
        if (internshipId == null) {
            return;
        }

        // 1. 删除该实习项目的旧流程配置（逻辑删除）
        relProcessInternshipDao.deleteByInternshipId(internshipId);

        // 2. 如果有新模板，复制新模板的流程配置
        if (newInternshipTypeId != null) {
            copyProcessFromTemplate(internshipId, newInternshipTypeId);
        }
    }
}
