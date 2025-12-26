package newcms.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/")
    public String hello() {
        return "Hello! Spring Boot 应用运行在端口 6080 上！";
    }

    @GetMapping("/hello")
    public String helloPath() {
        return "你好！这是一个测试接口。";
    }

    @GetMapping("/api/test")
    public String test() {
        return "{\"status\":\"success\",\"message\":\"API 测试成功\",\"port\":6080}";
    }
}

