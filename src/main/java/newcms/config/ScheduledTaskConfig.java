package newcms.config;

import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import newcms.base.Constant;
import newcms.service.ICommonService;
import newcms.service.IVerifyProcessService;
import newcms.utils.FastJsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 定时任务配置
 * 用于自动检查并激活已到开始时间的流程
 */
@Component
public class ScheduledTaskConfig {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledTaskConfig.class);

    @Resource
    private IVerifyProcessService iVerifyProcessService;

    @Resource
    private ICommonService iCommonService;

    /**
     * 系统启动完成后执行一次流程激活检查
     * 使用 ApplicationReadyEvent 确保所有 Bean 都已初始化完成
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        logger.info("系统启动完成，执行流程激活检查...");
        try {
            int count = activateStartedProcesses();
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
            int count = activateStartedProcesses();
            logger.info("每日流程激活检查完成，激活 {} 个流程", count);
        } catch (Exception e) {
            logger.error("每日流程激活检查失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 查询并激活已到开始时间的流程
     * @return 激活的流程数量
     */
    @SuppressWarnings("unchecked")
    private int activateStartedProcesses() {
        LocalDateTime now = LocalDateTime.now();
        String nowStr = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // 查询已到开始时间的流程（startTime <= 当前时间 且 endTime >= 当前时间）
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("startTime", nowStr);
        searchKeys.put("endTime", nowStr);

        Map<String, String> regMap = new HashMap<>();
        regMap.put("startTime", Constant.LE); // startTime <= 当前时间
        regMap.put("endTime", Constant.GE);   // endTime >= 当前时间

        Page<Object> pageResult = (Page<Object>) iCommonService.getSomeRecords(
                "ViewRelProcessInternship", searchKeys, regMap, Sort.unsorted());
        List<Object> processList = pageResult.getContent();

        if (processList == null || processList.isEmpty()) {
            logger.debug("未找到已到开始时间的流程");
            return 0;
        }

        int activatedCount = 0;
        for (Object processObj : processList) {
            try {
                JSONObject processJson = FastJsonUtil.toJson(processObj);
                Integer relationId = processJson.getInteger("id");
                Integer processId = processJson.getInteger("processTypeId");
                Integer internshipId = processJson.getInteger("internshipId");

                if (relationId == null || processId == null || internshipId == null) {
                    logger.warn("流程记录缺少必要字段，跳过: {}", processJson);
                    continue;
                }

                // 获取实习项目的创建人ID
                Object internshipObj = iCommonService.getOneRecordById("MainInternship", internshipId);
                if (internshipObj == null) {
                    logger.warn("未找到实习项目 {}，跳过流程 {}", internshipId, relationId);
                    continue;
                }
                JSONObject internshipJson = FastJsonUtil.toJson(internshipObj);
                Integer createUserId = internshipJson.getInteger("creatorId");

                // 构建 node 参数
                JSONObject node = new JSONObject();
                node.put("relationId", relationId);
                node.put("processId", processId);
                node.put("createUserId", createUserId);
                node.put("tableName", "RelProcessInternship");

                // 调用 activateProcess
                Object result = iVerifyProcessService.activateProcess(node);
                if (result != null) {
                    activatedCount++;
                }
            } catch (Exception e) {
                logger.warn("激活流程失败: {}", e.getMessage(), e);
            }
        }

        return activatedCount;
    }
}
