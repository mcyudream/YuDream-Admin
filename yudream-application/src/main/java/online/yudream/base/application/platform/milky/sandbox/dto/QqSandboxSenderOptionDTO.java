package online.yudream.base.application.platform.milky.sandbox.dto;

import java.util.List;

/**
 * 沙盒发送人/提及人可选项；userId 为雪花 ID，按全局规则以字符串传递
 */
public record QqSandboxSenderOptionDTO(String qq, String nickname, String userId, List<String> roles) { }
