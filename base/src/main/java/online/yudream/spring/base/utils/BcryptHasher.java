package online.yudream.spring.base.utils;

import lombok.RequiredArgsConstructor;
import online.yudream.spring.base.props.BcryptProps;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(BcryptProps.class)
@RequiredArgsConstructor
public class BcryptHasher {
    private final BcryptProps props;

    /** 生成哈希：自动含salt；返回形如 $2a$12$... 的字符串 */
    public String hash(String raw) {
        String input = withPepper(raw);
        return BCrypt.hashpw(input, BCrypt.gensalt(props.getRounds()));
    }

    /** 验证：用raw+pepper 与存储的哈希比对 */
    public boolean matches(String raw, String hashed) {
        if (hashed == null || hashed.isEmpty()) return false;
        return BCrypt.checkpw(withPepper(raw), hashed);
    }

    private String withPepper(String raw) {
        String pepper = props.getPepper();
        return (pepper == null || pepper.isEmpty()) ? raw : raw + pepper;
    }
}
