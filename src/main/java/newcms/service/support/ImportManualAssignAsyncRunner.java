package newcms.service.support;

import jakarta.annotation.Resource;
import newcms.config.AsyncConfig;
import newcms.service.IInternshipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ImportManualAssignAsyncRunner {

    private static final Logger logger = LoggerFactory.getLogger(ImportManualAssignAsyncRunner.class);

    @Resource
    @Lazy
    private IInternshipService iInternshipService;

    @Async(AsyncConfig.RANDOM_ASSIGN_EXECUTOR)
    public void run(String taskId) {
        try {
            iInternshipService.executeImportManualAssignTeacherStudentByExcel(taskId);
        } catch (Exception e) {
            logger.error("导入分配异步任务异常 taskId={}", taskId, e);
        }
    }
}
