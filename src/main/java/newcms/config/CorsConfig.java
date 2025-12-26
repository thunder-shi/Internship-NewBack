package newcms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 全局 CORS 跨域配置
 * 解决 allowCredentials=true 时不能使用 allowedOrigins="*" 的问题
 * 使用 allowedOriginPatterns 来支持通配符模式
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // 允许携带凭证（Cookie、Token等）
        config.setAllowCredentials(true);
        
        // 使用 allowedOriginPatterns 支持通配符，兼容 allowCredentials=true
        // 允许所有来源（开发环境）
        config.addAllowedOriginPattern("*");
        
        // 允许所有请求头
        config.addAllowedHeader("*");
        
        // 允许所有请求方法
        config.addAllowedMethod("*");
        
        // 预检请求的有效期，单位为秒
        config.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 对所有路径应用 CORS 配置
        source.registerCorsConfiguration("/**", config);
        
        return new CorsFilter(source);
    }
}

