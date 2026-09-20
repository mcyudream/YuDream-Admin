package online.yudream.base.bootstrap.installer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 安装器切片冒烟：MongoDB/Redis/SaToken DAO 自动配置全部排除后，
 * 切片必须能在无任何中间件的状态下启动并响应安装状态接口。
 */
@AutoConfigureMockMvc
@SpringBootTest(classes = InstallerApplication.class, properties = {
        // 注解要求编译期常量，此处与 InstallerApplication.AUTOCONFIGURE_EXCLUDES 保持一致
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration,"
                + "cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate",
        "yudream.bootstrap.installer=true"
})
class InstallerSliceSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void statusEndpointRespondsInInstallerMode() throws Exception {
        mockMvc.perform(get("/api/installer/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.installerMode").value(true))
                .andExpect(jsonPath("$.data.setupTokenRequired").value(false));
    }
}
