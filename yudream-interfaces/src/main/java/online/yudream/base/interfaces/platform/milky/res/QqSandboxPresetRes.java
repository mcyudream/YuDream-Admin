package online.yudream.base.interfaces.platform.milky.res;

import java.util.Map;

public record QqSandboxPresetRes(String code, String name, String description, String conversationType,
                                 String pluginCode, String policyConnectionId, String botId, String userId,
                                 String groupId, String nickname, String avatar, String content,
                                 Map<String, Object> metadata) { }
