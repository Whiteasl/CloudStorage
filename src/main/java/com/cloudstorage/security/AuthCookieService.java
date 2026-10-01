package com.cloudstorage.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.cloudstorage.model.entity.User;
import com.cloudstorage.util.JwtTokenUtil;

@Component
public class AuthCookieService {
    private final JwtTokenUtil jwtTokenUtil;

    @Value("${cloudstorage.cookie.name:CS_TOKEN}")
    private String cookieName;

    @Value("${cloudstorage.cookie.secure:false}")
    private boolean cookieSecurity;

    public AuthCookieService(JwtTokenUtil jwtTokenUtil) {
        this.jwtTokenUtil = jwtTokenUtil;
    }

    /**
     * 发行Cookie令牌
     * 
     * @param user User - 发行的用户
     * @return ResponseCookie - 返回Cookie令牌
     */
    public ResponseCookie issue(User user) {

        return ResponseCookie.from(this.cookieName, jwtTokenUtil.generateToken(user))
                .maxAge(Duration.ofSeconds(jwtTokenUtil.getExpirationSecond())).secure(this.cookieSecurity)
                .httpOnly(true).sameSite("Strict").path("/").build();
    }

    /**
     * 让令牌过期
     * 除了 value 和 Max-Age = 0 以外，属性与 issue() 方法相同
     * 
     * @return ResponseCookie - 返回空的过期令牌
     */
    public ResponseCookie expire() {

        return ResponseCookie.from(this.cookieName, "")
                .maxAge(Duration.ZERO).secure(this.cookieSecurity)
                .httpOnly(true).sameSite("Strict").path("/").build();
    }

    /**
     * 返回 Cookie 名字
     * 暴露 Cookie 名字给 Filter 使用
     * 
     * @return String - Cookie Name
     */
    public String getCookieName() {

        return this.cookieName;
    }
}
