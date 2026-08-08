package com.cloudstorage.util;

import java.util.Date;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloudstorage.exception.InvalidTokenException;
import com.cloudstorage.exception.TokenExpiredException;
import com.cloudstorage.model.entity.User;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

/**
 * JwtTokenUtil
 */
@Component
public class JwtTokenUtil {
    private final SecretKey key;

    private static Logger log = LoggerFactory.getLogger(JwtTokenUtil.class);

    @Value("${cloudstorage.jwt.expiration-hours}")
    private long expirationHours;

    public JwtTokenUtil(@Value("${cloudstorage.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }

    /**
     * 生成令牌
     * 
     * @param user 用户实体
     * @return 返回一个Token
     */
    public String generateToken(User user) {
        return Jwts.builder()
                .subject(user.getUsername()) // 设置主题 subject ，通常存放用户标示，如：用户名或用户id
                .issuedAt(new Date()) // 签发时间
                .expiration(new Date(System.currentTimeMillis() + expirationHours * 3600 * 1000)) // 过期时间
                .claim("id", user.getId())
                .claim("username", user.getUsername())
                .claim("role", user.getRole())
                .signWith(key) // 使用密钥进行签名
                .compact(); // 压缩并生成最终的令牌字符串
    }

    /**
     * 生成用于 忘记密码 对话的令牌
     * 
     * @param email 用户邮箱
     * @return 令牌
     */
    public String generateForgotPasswordToken(String email) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 20 * 60 * 1000))
                .claim("email", email)
                .signWith(key)
                .compact();
    }

    /**
     * 解析令牌
     * 
     * @param token 令牌
     * @return 返回一个 Claims
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new TokenExpiredException("令牌已过期"); // 令牌过期，让前端跳转到登录页
        } catch (UnsupportedJwtException | MalformedJwtException | IllegalArgumentException e) {
            // 算法错误；格式错误；空值 - 非法请求
            throw new InvalidTokenException("令牌格式错误");
        } catch (SignatureException e) {
            // 签名错误，可能遭到攻击，需要记录到日志中
            log.warn("签名验证错误，可能受到攻击：{}", e.getMessage());
            throw new InvalidTokenException("令牌验证失败");
        }
    }

    /**
     * 从令牌中提取 id
     * 
     * @param token 令牌
     * @return 返回ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = validateToken(token);
        return claims.get("id", Long.class);
    }
}
