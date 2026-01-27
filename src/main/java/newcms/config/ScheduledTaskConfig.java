package newcms.config;

import jakarta.annotation.Resource;
import newcms.service.IVerifyProcessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务配置
 * 用于自动检查并激活已到开始时间的流程
 */
@Component
public class ScheduledTaskConfig {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledTaskConfig.class);

    @Resource
    private IVerifyProcessService iVerifyProcessService;

    /**
     * 系统启动完成后执行一次流程激活检查
     * 使用 ApplicationReadyEvent 确保所有 Bean 都已初始化完成
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        logger.info("系统启动完成，执行流程激活检查...");
        try {
            int count = iVerifyProcessService.activateStartedProcesses();
            logger.info("启动时流程激活检查完成，激活 {} 个流程", count);
        } catch (Exception e) {
            logger.error("启动时流程激活检查失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 每天零点执行流程激活检查
     * cron 表达式: 秒 分 时 日 月 星期
     * "0 0 0 * * ?" 表示每天 00:00:00 执行
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void dailyProcessActivation() {
        logger.info("每日定时任务：执行流程激活检查...");
        try {
            int count = iVerifyProcessService.activateStartedProcesses();
            logger.info("每日流程激活检查完成，激活 {} 个流程", count);
        } catch (Exception e) {
            logger.error("每日流程激活检查失败: {}", e.getMessage(), e);
        }
    }
}
