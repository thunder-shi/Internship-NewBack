package newcms.utils;

import com.alibaba.fastjson.JSONObject;
import newcms.base.Base;
import newcms.service.ICommonService;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


@Component
public class LogUtil {
    
    private static LogUtil instance;
    
    @Resource
    private ICommonService iCommonService;
    
    public LogUtil() {
        instance = this;
    }

    /**
     * 筛选无效日志，仅打印前 row 行
     */
    public static void error(Logger logger, Exception e) {
        error(logger, e, 99);
    }

    /**
     * 筛选无效日志，仅打印前 row 行
     * @param logger
     * @param e
     * @param row
     */
    public static void error(Logger logger, Exception e, int row) {
        logger.error(IntStream.range(0, Math.min(e.getStackTrace().length, row)).mapToObj(i -> "\n\t" + e.getStackTrace()[i]).collect(Collectors.joining("", "\n" + e.toString(), "\n\t...")));
    }
    
    /**
     * 记录日志
     * @param action 操作动作
     * @param obj 操作对象
     */
    public static void loggerRecord(String action, JSONObject obj) {
        JSONObject json = new JSONObject();
        json.put("userId", Base.getLoginUserId());
        json.put("action", action);
        if (obj.toJSONString().length()>1000) {
            json.put("detail", obj.toJSONString().substring(0, 1000));
        } else {
            json.put("detail", obj.toJSONString());
        }
        // instance.iCommonService.saveOneRecord("SysLogger", json);  
        // 这里暂时注释掉，因为需要修改日志记录方式
    }
}
