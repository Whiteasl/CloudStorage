package com.cloudstorage.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.cloudstorage.exception.InvalidTokenException;
import com.cloudstorage.exception.TokenExpiredException;
import com.cloudstorage.util.JwtTokenUtil;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * JwtAuthenticationFilter
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private JwtTokenUtil jwtTokenUtil;

    public JwtAuthenticationFilter(JwtTokenUtil jwtTokenUtil) {
        this.jwtTokenUtil = jwtTokenUtil;
    }

    /**
     * 令牌检验Filter
     * 
     * @param request     请求体
     * @param response    响应体
     * @param filterChain Filter字段
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String author = request.getHeader("Authorization");

        if (author == null || author.isBlank() || !author.startsWith("Bearer ")) {
            // 未发现令牌，先放行，后续操作由 SecurityConfig 文件中的判断，把用户重定向到登录/注册页面
            filterChain.doFilter(request, response);
            return; // 使用 return 结束方法，避免下面的逻辑被执行
        }

        try {
            // 找到了令牌，对令牌进行检查
            Claims claims = jwtTokenUtil.validateToken(author.substring(7)); // 解析令牌，把字符串转化成令牌对象
            Long userId = claims.get("id", Long.class);
            List<GrantedAuthority> authorities = List
                    .of(new SimpleGrantedAuthority("ROLE_" + claims.get("role", String.class).toUpperCase()));
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userId, null,
                    authorities);
            SecurityContextHolder.getContext().setAuthentication(authToken);
            filterChain.doFilter(request, response);
        } catch (InvalidTokenException | TokenExpiredException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

}
