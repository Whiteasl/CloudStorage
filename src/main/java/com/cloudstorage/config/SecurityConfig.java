package com.cloudstorage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.cloudstorage.security.JwtAuthenticationFilter;
import com.cloudstorage.util.JwtTokenUtil;

/**
 * SecurityConfig
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // 方法级别的安全控制 先加上，后续可能有用 对同一个 URL 的不同HTTP方法选用不同的权限 开启 PreAuthorize 注解功能
public class SecurityConfig {
    private final JwtTokenUtil jwtTokenUtil;

    public SecurityConfig(JwtTokenUtil jwtTokenUtil) {
        this.jwtTokenUtil = jwtTokenUtil;
    }

    /**
     * 安全检查 Filter
     * 
     * @param http
     * @return
     * @throws Exception
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 无须进行登录，可以直接访问
                .authorizeHttpRequests(auth -> auth
                        // 对前端页面放行
                        .requestMatchers(HttpMethod.GET, "/", "/login", "/register", "/share/*", "/share", "/files",
                                "/forgot-password/**")
                        .permitAll()
                        // 对忘记密码端点进行放行
                        .requestMatchers(HttpMethod.POST, "/forgot-password/**",
                                "/security-questions", "/verify-answers")
                        .permitAll()
                        .requestMatchers("/css/**", "/js/**", "/index.html", "/assets/**", "/icons.svg", "/favicon.svg")
                        .permitAll() // 前端资源允许所有人访问，防止出现页面无法加载或格式错误异常出现

                        // API 端点
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/login", "/register", "/Share/**").permitAll()
                        .requestMatchers("/share/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/reset-password/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/admin/**").hasRole("ADMIN") // 管理员页面需要验证用户为管理员才能访问
                        .requestMatchers("/file/**").hasAnyRole("USER", "ADMIN") // 文件存储页面需要验证用户登录状态，只有登录的用户才能访问
                        .anyRequest().authenticated()) // 其余所有页面都需要登录才能访问，不限制访问路径
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenUtil), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 密码加密器
     * 
     * @return
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        // 对密码进行加密
        return new BCryptPasswordEncoder();
    }

    /**
     * 认证执行器
     * 
     * @param config
     * @return
     * @throws Exception
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        // 认证执行器 - 拿用户名和密码去检验

        // 认证管理器 - 供登录接口使用
        // SpringBoot 会自动整合 UserDetailsService 和 PasswordEncoder 到 Ioc 容器中
        // 后续组装 Bean 时能直接用
        return config.getAuthenticationManager();

    }
}