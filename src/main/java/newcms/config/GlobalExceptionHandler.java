package newcms.config;

import newcms.base.BaseException;
import newcms.base.BaseResponse;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 捕获 BaseException 并转换为 HTTP 响应
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理自定义异常 BaseException
     */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<Object> handleBaseException(BaseException e) {
        BaseResponse baseResponse = e.getBaseResponse();
        JSONObject response = new JSONObject();
        response.put("status", baseResponse.getStatus());
        response.put("message", baseResponse.getMessage());
        response.put("data", baseResponse.getData());
        response.put("httpStatus", baseResponse.getHttpStatus());
        
        HttpStatus httpStatus = HttpStatus.valueOf(baseResponse.getHttpStatus());
        logger.error("BaseException: {}", baseResponse.getMessage(), e);
        
        return ResponseEntity.status(httpStatus).body(response);
    }

    /**
     * 处理其他未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleException(Exception e) {
        logger.error("未捕获的异常", e);
        
        // 获取详细的异常信息
        String errorMessage = e.getMessage();
        if (errorMessage == null || errorMessage.isEmpty()) {
            errorMessage = e.getClass().getName();
        }
        
        // 如果是 Shiro 相关异常，提供更友好的提示
        if (e.getClass().getName().contains("shiro") || 
            errorMessage != null && errorMessage.toLowerCase().contains("securitymanager")) {
            errorMessage = "Shiro 安全框架未正确配置，请检查 ShiroConfig 配置";
        }
        
        JSONObject response = new JSONObject();
        response.put("status", BaseResponse.notCaptured.getStatus());
        response.put("message", BaseResponse.notCaptured.getMessage() + ": " + errorMessage);
        response.put("data", "");
        response.put("httpStatus", BaseResponse.notCaptured.getHttpStatus());
        
        // 开发环境可以返回更详细的堆栈信息
        if (logger.isDebugEnabled()) {
            response.put("exception", e.getClass().getName());
            response.put("stackTrace", getStackTrace(e));
        }
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * 获取异常堆栈信息（仅用于调试）
     */
    private String getStackTrace(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}

