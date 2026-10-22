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
 * 用户工作区工具：列出与读取（platform:chat:workspace:view）在问答/规划模式提供；
 * 写入与删除（platform:chat:workspace:edit）只在「编码」工作区模式提供，属写入风险工具，每次调用都需用户确认。
 * 只操作 KV 中当前用户自己的工作区，不触及主机文件系统或 shell。
 */
public final class ChatWorkspaceTools {

    public static final String LIST_TOOL = "workspace.list";
    public static final String READ_TOOL = "workspace.read";
    public static final String PERMISSION_CODE = "platform:chat:workspace:view";
    public static final String WRITE_TOOL = "workspace.write";
    public static final String DELETE_TOOL = "workspace.delete";
    public static final String EDIT_PERMISSION_CODE = "platform:chat:workspace:edit";
    /** 模型写入的单文件上限（文本）：远小于工作区 8 MB 上限，避免一次生成巨型文件 */
    private static final int MAX_WRITE_BYTES = 1024 * 1024;
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

    /** 「编码」模式的写入工具：创建/覆盖文本文件与删除文件，均需用户逐次确认 */
    public static List<AiAgentTool> createEditable(ChatWorkspaceStore store, Long userId) {
        if (store == null || userId == null) {
            return List.of();
        }
        return List.of(writeTool(store, userId), deleteTool(store, userId));
    }

    /** 工作区相对路径：去掉首尾空白与开头斜杠，禁止 ..、反斜杠与控制字符，限 200 字 */
    static String safePath(Object raw) {
        String path = raw == null ? "" : String.valueOf(raw).trim().replaceFirst("^/+", "");
        if (path.isEmpty() || path.length() > 200 || path.contains("\\") || path.endsWith("/")
                || java.util.Arrays.asList(path.split("/")).contains("..")
                || path.chars().anyMatch(c -> c < 32 || c == 127)) {
            throw new BizException("工作区文件路径无效：使用不含 .. 的相对路径，如 notes/plan.md");
        }
        return path;
    }

    private static AiAgentTool writeTool(ChatWorkspaceStore store, Long userId) {
        AiAgentToolDescriptor descriptor = new AiAgentToolDescriptor(
                WRITE_TOOL,
                "写入工作区文件",
                "在用户工作区创建或整体覆盖一个文本文件（UTF-8，单文件最多 1 MB）。覆盖前先用 workspace.read 读原文；执行前需用户确认。",
                EDIT_PERMISSION_CODE,
                "编辑我的工作区",
                "AI 助手",
                "允许助手在用户确认后写入用户自己的工作区文件",
                Map.of("path", Map.of("type", "string", "description", "工作区相对路径，如 notes/plan.md"),
                        "content", Map.of("type", "string", "description", "文件完整内容")));
        return new AiAgentTool() {
            @Override
            public AiAgentToolDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public AgentToolRisk risk() {
                return AgentToolRisk.WRITE;
            }

            @Override
            public AiAgentToolResult execute(AiAgentToolCall call) {
                Map<String, Object> args = call.arguments() == null ? Map.of() : call.arguments();
                String path = safePath(args.get("path"));
                Object content = args.get("content");
                if (!(content instanceof String text)) {
                    throw new BizException("content 必须是文本");
                }
                byte[] data = text.getBytes(StandardCharsets.UTF_8);
                if (data.length > MAX_WRITE_BYTES) {
                    throw new BizException("单个文件最多 1 MB，请拆分后写入");
                }
                boolean existed = store.list(userId).stream().anyMatch(entry -> entry.path().equals(path));
                store.writeFile(userId, path, data);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("path", path);
                payload.put("size", data.length);
                payload.put("created", !existed);
                String message = (existed ? "已覆盖 " : "已创建 ") + path;
                payload.put("message", message);
                return new AiAgentToolResult(WRITE_TOOL, "write", EDIT_PERMISSION_CODE, message, payload);
            }
        };
    }

    private static AiAgentTool deleteTool(ChatWorkspaceStore store, Long userId) {
        AiAgentToolDescriptor descriptor = new AiAgentToolDescriptor(
                DELETE_TOOL,
                "删除工作区文件",
                "删除用户工作区中的一个文件；执行前需用户确认。",
                EDIT_PERMISSION_CODE,
                "编辑我的工作区",
                "AI 助手",
                "允许助手在用户确认后删除用户自己的工作区文件",
                Map.of("path", Map.of("type", "string", "description", "要删除的工作区文件路径，来自 workspace.list")));
        return new AiAgentTool() {
            @Override
            public AiAgentToolDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public AgentToolRisk risk() {
                return AgentToolRisk.WRITE;
            }

            @Override
            public AiAgentToolResult execute(AiAgentToolCall call) {
                String path = safePath(call.arguments() == null ? null : call.arguments().get("path"));
                if (store.list(userId).stream().noneMatch(entry -> entry.path().equals(path))) {
                    throw new BizException("工作区中没有该文件：" + path);
                }
                store.delete(userId, path);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("path", path);
                payload.put("message", "已删除 " + path);
                return new AiAgentToolResult(DELETE_TOOL, "delete", EDIT_PERMISSION_CODE, "已删除 " + path, payload);
            }
        };
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
