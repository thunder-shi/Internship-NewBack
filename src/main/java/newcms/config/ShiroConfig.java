package newcms.config;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import newcms.entity.db.BaseUser;
import newcms.repository.db.BaseUserDao;
import newcms.utils.EncodeUtil;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.spring.security.interceptor.AuthorizationAttributeSourceAdvisor;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.web.filter.authc.FormAuthenticationFilter;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.Resource;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shiro 安全框架配置
 */
@Configuration
public class ShiroConfig {

    @Resource
    private BaseUserDao baseUserDao;

    /**
     * 自定义 Realm
     */
    @Bean
    public AuthorizingRealm shiroRealm() {
        AuthorizingRealm realm = new AuthorizingRealm() {
            @Override
            protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
                UsernamePasswordToken upToken = (UsernamePasswordToken) token;
                String userId = upToken.getUsername();
                String password = new String(upToken.getPassword());

                // 根据 userId 查询用户
                BaseUser user = baseUserDao.findById(Integer.parseInt(userId))
                        .orElseThrow(() -> new UnknownAccountException("用户不存在"));

                // 检查用户是否被删除
                if (user.getIsDeleted() != null && user.getIsDeleted()) {
                    throw new DisabledAccountException("用户已被删除");
                }

                // 手动验证密码（因为我们的加密逻辑已经在 EncodeUtil 中处理）
                String encryptedPassword = EncodeUtil.pwdShiro(password, userId);
                if (!encryptedPassword.equals(user.getPassword())) {
                    throw new IncorrectCredentialsException("密码错误");
                }

                // 返回认证信息
                return new SimpleAuthenticationInfo(
                        user.getId().toString(),  // principal
                        password,                 // credentials (原始密码，已通过验证)
                        getName()                 // realm name
                );
            }

            @Override
            protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
                // 授权信息，暂时返回空，后续可以根据需要添加角色和权限
                SimpleAuthorizationInfo authorizationInfo = new SimpleAuthorizationInfo();
                // 可以在这里添加角色和权限
                // authorizationInfo.addRole("user");
                // authorizationInfo.addStringPermission("user:read");
                return authorizationInfo;
            }
        };
        
        // 不使用 HashedCredentialsMatcher，因为我们已经在 doGetAuthenticationInfo 中手动验证了密码
        // 这样可以保持与现有 EncodeUtil.pwdShiro 的兼容性
        
        return realm;
    }

    /**
     * SecurityManager
     */
    @Bean
    public DefaultWebSecurityManager securityManager() {
        DefaultWebSecurityManager securityManager = new DefaultWebSecurityManager();
        securityManager.setRealm(shiroRealm());
        return securityManager;
    }

    /**
     * 自定义认证过滤器 - 前后端分离返回JSON而非重定向
     */
    private FormAuthenticationFilter jsonAuthcFilter() {
        return new FormAuthenticationFilter() {
            @Override
            protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws Exception {
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.setContentType("application/json;charset=UTF-8");
                try (PrintWriter writer = httpResponse.getWriter()) {
                    writer.write("{\"code\":401,\"msg\":\"未登录或登录已过期，请重新登录\"}");
                    writer.flush();
                }
                return false;
            }
        };
    }

    /**
     * ShiroFilterFactoryBean
     */
    @Bean
    public ShiroFilterFactoryBean shiroFilterFactoryBean() {
        ShiroFilterFactoryBean shiroFilterFactoryBean = new ShiroFilterFactoryBean();
        shiroFilterFactoryBean.setSecurityManager(securityManager());

        // 添加自定义过滤器，返回JSON而非重定向到login.jsp
        Map<String, jakarta.servlet.Filter> filters = new LinkedHashMap<>();
        filters.put("authc", jsonAuthcFilter());
        shiroFilterFactoryBean.setFilters(filters);

        // 配置拦截规则
        Map<String, String> filterChainDefinitionMap = new LinkedHashMap<>();

        // 允许匿名访问的路径
        filterChainDefinitionMap.put("/sign/login", "anon");  // 登录接口
        filterChainDefinitionMap.put("/sign/logout", "anon"); // 登出接口
        filterChainDefinitionMap.put("/common/getKey", "anon"); // 获取加密密钥（唯一允许匿名的公共接口）
        // SEC-03: /common/minio/** 等写操作接口需要认证，不可匿名访问

        // 其他路径需要认证
        filterChainDefinitionMap.put("/**", "authc");

        shiroFilterFactoryBean.setFilterChainDefinitionMap(filterChainDefinitionMap);
        return shiroFilterFactoryBean;
    }

    /**
     * 启用 Shiro 注解支持
     */
    @Bean
    public AuthorizationAttributeSourceAdvisor authorizationAttributeSourceAdvisor() {
        AuthorizationAttributeSourceAdvisor advisor = new AuthorizationAttributeSourceAdvisor();
        advisor.setSecurityManager(securityManager());
        return advisor;
    }
}
