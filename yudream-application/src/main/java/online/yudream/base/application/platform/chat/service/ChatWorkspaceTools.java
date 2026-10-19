package online.yudream.base.application.platform.chat.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户工作区只读工具：列出与读取当前用户工作区文件，权限码与 HTTP 端点同源
 * （platform:chat:workspace:view），写入与删除只经界面端点，模型侧不提供。
 */
public final class ChatWorkspaceTools {

    public static final String LIST_TOOL = "workspace.list";
    public static final String READ_TOOL = "workspace.read";
    public static final String PERMISSION_CODE = "platform:chat:workspace:view";
    /** 单次读取上限：超出时截断并提示分段，避免把整篇大文件塞进上下文。 */
    private static final int MAX_READ_CHARS = 60000;

    private ChatWorkspaceTools() {
    }

    public static List<AiAgentTool> create(ChatWorkspaceStore store, Long userId) {
        if (store == null || userId == null) {
            return List.of();
        }
        return List.of(listTool(store, userId), readTool(store, userId));
    }

    private static AiAgentTool listTool(ChatWorkspaceStore store, Long userId) {
        AiAgentToolDescriptor descriptor = new AiAgentToolDescriptor(
                LIST_TOOL,
                "列出工作区文件",
                "列出用户工作区中的全部文件（路径、大小、更新时间）。需要读取内容时再用 workspace.read。",
                PERMISSION_CODE,
                "查看我的工作区",
                "AI 助手",
                "允许助手读取用户自己的工作区文件清单",
                Map.of());
        return new AiAgentTool() {
            @Override
            public AiAgentToolDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public AgentToolRisk risk() {
                return AgentToolRisk.READ;
            }

            @Override
            public AiAgentToolResult execute(AiAgentToolCall call) {
                List<ChatWorkspaceStore.Entry> entries = store.list(userId);
                List<Map<String, Object>> files = entries.stream().map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<String, Object>();
                    item.put("path", entry.path());
                    item.put("size", entry.size());
                    item.put("updatedAt", entry.updatedAt());
                    return item;
                }).toList();
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("count", files.size());
                payload.put("files", files);
                payload.put("message", files.isEmpty() ? "工作区还没有文件，可提示用户上传" : "工作区共 " + files.size() + " 个文件");
                return new AiAgentToolResult(LIST_TOOL, "list", PERMISSION_CODE,
                        files.isEmpty() ? "工作区为空" : "工作区共 " + files.size() + " 个文件", payload);
            }
        };
    }

    private static AiAgentTool readTool(ChatWorkspaceStore store, Long userId) {
        AiAgentToolDescriptor descriptor = new AiAgentToolDescriptor(
                READ_TOOL,
                "读取工作区文件",
                "按路径读取用户工作区中的文件内容；文本直接返回（超长截断），二进制返回大小与类型说明。",
                PERMISSION_CODE,
                "查看我的工作区",
                "AI 助手",
                "允许助手读取用户自己的工作区文件内容",
                Map.of("path", Map.of("type", "string", "description", "工作区文件路径，来自 workspace.list")));
        return new AiAgentTool() {
            @Override
            public AiAgentToolDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public AgentToolRisk risk() {
                return AgentToolRisk.READ;
            }

            @Override
            public AiAgentToolResult execute(AiAgentToolCall call) {
                Object raw = call.arguments() == null ? null : call.arguments().get("path");
                String path = raw == null ? "" : String.valueOf(raw).trim();
                if (path.isEmpty() || path.contains("../")) {
                    throw new BizException("请提供来自 workspace.list 的工作区文件路径");
                }
                byte[] data = store.readFile(userId, path);
                boolean binary = isBinary(data);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("path", path);
                payload.put("size", data.length);
                if (binary) {
                    payload.put("encoding", "base64");
                    payload.put("message", "二进制文件（" + data.length + " 字节），不返回正文");
                    return new AiAgentToolResult(READ_TOOL, "read", PERMISSION_CODE, "二进制文件不返回正文", payload);
                }
                String text = new String(data, StandardCharsets.UTF_8);
                boolean truncated = text.length() > MAX_READ_CHARS;
                payload.put("encoding", "text");
                payload.put("truncated", truncated);
                payload.put("content", truncated ? text.substring(0, MAX_READ_CHARS) : text);
                payload.put("message", truncated ? "内容超过 " + MAX_READ_CHARS + " 字符，已截断" : "已读取 " + path);
                return new AiAgentToolResult(READ_TOOL, "read", PERMISSION_CODE,
                        truncated ? "已截断读取 " + path : "已读取 " + path, payload);
            }
        };
    }

    private static boolean isBinary(byte[] data) {
        int limit = Math.min(data.length, 8192);
        for (int i = 0; i < limit; i++) {
            if (data[i] == 0) {
                return true;
            }
        }
        return false;
    }
}
