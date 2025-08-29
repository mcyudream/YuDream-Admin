package online.yudream.spring.base.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("crypto.bcrypt")
public class BcryptProps {
    /** log rounds，越大越慢越安全 */
    private int rounds = 10;
    /** 全局pepper（可留空） */
    private String pepper = "";
}
