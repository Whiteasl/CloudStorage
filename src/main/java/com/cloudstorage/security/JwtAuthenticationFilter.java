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
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * JwtAuthenticationFilter
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final String cookieName;

    private final JwtTokenUtil jwtTokenUtil;

    public JwtAuthenticationFilter(JwtTokenUtil jwtTokenUtil, String cookieName) {
        this.jwtTokenUtil = jwtTokenUtil;
        this.cookieName = cookieName;
    }

    /**
     * Filter: 令牌检验
     * 
     * @param request     请求体
     * @param response    响应体
     * @param filterChain Filter字段
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Cookie[] cookies = request.getCookies();

        if (cookies == null || cookies.length == 0) {
            // 未发现令牌，先放行，后续操作由 SecurityConfig 文件中的判断，把用户重定向到登录/注册页面
            filterChain.doFilter(request, response);
            return; // 使用 return 结束方法，避免下面的逻辑被执行
        }

        try {
            // 查找有效令牌
            Claims claims = this.getClaims(cookies, this.cookieName);

            if (claims == null) {
                // 无有效令牌，直接放行
                // 由后方 SecurityConfig 进行判断
                filterChain.doFilter(request, response);
                return;
            }
            // 解析令牌，把字符串转化成令牌对象
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

    private Claims getClaims(Cookie[] cookies, String target) {
        boolean found = false;
        for (Cookie cookie : cookies) {
            // 循环查找有效 Cookie
            if (!target.equals(cookie.getName()))
                continue;

            // 找到对应令牌
            found = true;
            try {
                // 验证令牌有效性，有效则返回
                return jwtTokenUtil.validateAuthToken(cookie.getValue());
            } catch (TokenExpiredException | InvalidTokenException e) {
                // 令牌无效或过期，继续寻找下一个有效令牌
                continue;
            }
        }

        if (found)
            // 找到了令牌，但没有有效的
            throw new InvalidTokenException("登录令牌无效或已过期");

        // 没有找到对应的令牌
        return null;
    }

}
