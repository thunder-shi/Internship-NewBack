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
public class InitTutorAssignAsyncRunner {

    private static final Logger logger = LoggerFactory.getLogger(InitTutorAssignAsyncRunner.class);

    @Resource
    @Lazy
    private IInternshipService iInternshipService;

    @Async(AsyncConfig.RANDOM_ASSIGN_EXECUTOR)
    public void run(String taskId) {
        try {
            iInternshipService.executeInitTeacherStudentByInternshipId(taskId);
        } catch (Exception e) {
            logger.error("系统分配校内导师异步任务异常 taskId={}", taskId, e);
        }
    }
}
