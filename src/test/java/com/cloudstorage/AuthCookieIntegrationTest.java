package com.cloudstorage;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.cloudstorage.model.entity.User;
import com.cloudstorage.repository.UserRepository;
import com.cloudstorage.security.AuthCookieService;
import com.cloudstorage.util.JwtTokenUtil;

import jakarta.servlet.http.Cookie;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class AuthCookieIntegrationTest {

  @Autowired
  private WebApplicationContext context;
  @Autowired
  private UserRepository userRepository;
  @Autowired
  private AuthCookieService authCookieService;
  @Autowired
  private JwtTokenUtil jwtTokenUtil;

  private MockMvc mockMvc;

  // 对每个测试方法创建唯一实例
  // 避免信息出现重复
  private String username = "cookie_" + UUID.randomUUID().toString().substring(0, 8); // 截取八位随机字符 与 固定字符串组成用户名
  private String password = "TestPass123"; // 固定测试密码
  private String email = username + "@example.com"; // 固定测试邮箱

  /*
   * 在每个测试方法运行前，基于当前 Spring 容器（WebApplicationContext）重新构建一个 MockMvc 对象
   * 这样在测试方法里就可以用 mockMvc.perform(...) 来模拟 HTTP 请求
   * 对 Controller 进行不启动真实服务器的测试
   */
  @BeforeEach
  void setUp() {

    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  /*
   * 每个测试方法跑完后，把测试过程中创建的那个用户从数据库删掉
   * 保证测试之间互不影响、数据干净
   */
  @AfterEach
  void cleanUp() {

    userRepository.findByUsername(username).ifPresent(userRepository::delete);
  }

  /**
   * 检查Cookie格式是否正确
   * 
   * @throws Exception
   */
  @Test
  protected void checkCookie() throws Exception {
    Cookie cookie = this.registerLoginAndGetCookie();

    assertThat(cookie).isNotNull();
    assertThat(cookie.getValue()).isNotBlank();
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.getSecure()).isFalse(); // 明文部署期间暂时使用，方便开发期修改
    assertThat(cookie.getMaxAge()).isEqualTo((int) jwtTokenUtil.getExpirationSecond());
    assertThat(cookie.getPath()).isEqualTo("/");
    assertThat(cookie.getName()).isEqualTo(authCookieService.getCookieName());
    assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");

  }

  /**
   * 带合法有效Cookie访问 me 端点，返回200
   * 
   * @exception Exception
   */
  @Test
  protected void meWithCookie() throws Exception {

    Cookie cookie = this.registerLoginAndGetCookie();

    User user = userRepository.findByUsername(this.username).orElseThrow();

    // 带 Cookie 的请求
    mockMvc.perform(get("/me").cookie(cookie))
        .andExpect(status().isOk())
        // 使用 jsonPath 和 .value 判断返回内容是否正确
        .andExpect(jsonPath("$.id").value(user.getId().intValue()))
        .andExpect(jsonPath("$.username").value(user.getUsername()))
        .andExpect(jsonPath("$.role").value(user.getRole()))
        .andExpect(jsonPath("$.token").doesNotExist());
  }

  /**
   * 不携带Cookie，应触发安全策略，返回 401
   * 
   * @throws Exception
   */
  @Test
  protected void meWithoutCookie() throws Exception {
    // 不带 Cookie 的请求
    mockMvc.perform(get("/me"))
        .andExpect(status().isUnauthorized()); // 返回 401
  }

  /**
   * 携带错误Cookie，应触发安全策略, 返回 401
   * 
   * @throws Exception
   */
  @Test
  protected void meWithErrorCookie() throws Exception {
    // 携带错误 Cookie 的请求
    mockMvc.perform(get("/me")
        .cookie(new Cookie(authCookieService.getCookieName(), "HelloWorld123456")))
        .andExpect(status().isUnauthorized()); // 返回 401
  }

  /**
   * 不带Cookie的下载行为，应触发安全策略返回 401
   * 
   * @throws Exception
   */
  @Test
  protected void downloadWithoutCookie() throws Exception {
    mockMvc.perform(get("/file/download?fileId=999999999")).andExpect(status().isUnauthorized());
  }

  /**
   * 测试无记录文件是否会触发下载
   * 应触发 404 错误
   * 
   * @throws Exception
   */
  @Test
  protected void downloadWithCookie() throws Exception {

    Cookie cookie = this.registerLoginAndGetCookie();

    mockMvc.perform(get("/file/download?fileId=999999").cookie(cookie)).andExpect(status().isNotFound());
  }

  /**
   * 越权访问测试
   * 返回 403
   * 
   * @throws Exception
   */
  @Test
  protected void userAccessesAdmin() throws Exception {
    Cookie cookie = this.registerLoginAndGetCookie();

    mockMvc.perform(get("/admin/users").cookie(cookie)).andExpect(status().isForbidden()); // 返回 403
  }

  /**
   * 未授权访问测试
   * 返回 401
   * 
   * @throws Exception
   */
  @Test
  protected void adminWithoutCookie() throws Exception {
    mockMvc.perform(get("/admin/users")).andExpect(status().isUnauthorized());
  }

  /**
   * 带 Cookie 的登出请求
   * 返回空Cookie
   * 
   * @throws Exception
   */
  @Test
  protected void logoutWithCookie() throws Exception {
    Cookie cookie = this.registerLoginAndGetCookie();

    mockMvc.perform(get("/logout").cookie(cookie)).andExpect(status().isFound())
        .andExpect(header().string(HttpHeaders.LOCATION, "/login"))
        .andExpect(header().string(HttpHeaders.SET_COOKIE,
            allOf(containsString(authCookieService.getCookieName() + "=;"),
                containsString("Path=/"),
                containsString("Max-Age=0"),
                containsString("HttpOnly"),
                containsString("SameSite=Strict"))));

  }

  /**
   * 不带 Cookie 的登出请求
   * 返回空Cookie
   * 
   * @throws Exception
   */
  @Test
  protected void logoutWithoutCookie() throws Exception {
    mockMvc.perform(get("/logout"))
        .andExpect(status().isFound())
        .andExpect(header().string(HttpHeaders.LOCATION, "/login"))
        .andExpect(header().string(HttpHeaders.SET_COOKIE,
            allOf(containsString(authCookieService.getCookieName() + "=;"),
                containsString("Path=/"),
                containsString("Max-Age=0"),
                containsString("HttpOnly"),
                containsString("SameSite=Strict"))));
  }

  /**
   * 测试注册登录功能是否正常
   * 
   * @return Cookie - 返回注册登录均成功的Cookie值
   * @throws Exception
   */
  private Cookie registerLoginAndGetCookie() throws Exception {
    /*
     * 注册 + 登录 + 获取Cookie
     */

    /*
     * 注册
     * 格式：{"username": this.username, "password": this.password, "email":
     * this.email}
     */

    mockMvc.perform(post("/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"" + this.username + "\",\"password\":\"" + this.password + "\",\"email\":\""
            + this.email + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().exists(HttpHeaders.SET_COOKIE)).andReturn();

    /*
     * 登录
     * 格式：{"username": this.username, "password": this.password}
     */
    MvcResult loginResult = mockMvc
        .perform(post("/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + this.username + "\",\"password\":\"" + this.password + "\"}"))
        .andExpect(status().isOk()).andExpect(header().exists(HttpHeaders.SET_COOKIE))
        .andReturn();

    // 获取响应头 Set-Cookie 的值
    Cookie cookie = loginResult.getResponse().getCookie(authCookieService.getCookieName());

    // 返回符合Cookie格式的值
    return cookie;
  }

}
