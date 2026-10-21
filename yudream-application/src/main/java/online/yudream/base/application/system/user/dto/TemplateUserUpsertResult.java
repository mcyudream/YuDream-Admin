package online.yudream.base.application.system.user.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 模板导入用户 upsert 结果。
 */
@Getter
@Builder
public class TemplateUserUpsertResult {

    /** true=新建用户，false=更新既有用户。 */
    private final boolean created;

    private final Long userId;

    private final String username;

    /**
     * 新建且邮箱可用时为 true（服务端不导出密码，导入声明 emailVerified=true 同样需要邀请首设）：
     * 调用方在事务提交后调用 {@code sendInitialPasswordSetupEmail(userId)} 发出初始密码设置邮件。
     * 更新既有用户恒为 false（保密码不变，如需重发初始化邮件走管理端能力）。
     */
    private final boolean needsInitialPasswordEmail;
}
