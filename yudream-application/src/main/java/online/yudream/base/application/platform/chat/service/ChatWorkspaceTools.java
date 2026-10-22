package online.yudream.base.application.platform.chat.service;

import online.yudream.base.application.platform.workspace.dto.WorkspaceEntryDTO;
import online.yudream.base.application.platform.workspace.dto.WorkspaceFileContentDTO;
import online.yudream.base.application.platform.workspace.service.WorkspaceAccess;
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
import java.util.function.Function;

/**
 * 用户工作区工具：列出与读取（platform:chat:workspace:view）在问答/规划模式提供；
 * 写入、移动与删除（platform:chat:workspace:edit）只在「编码」工作区模式提供，属写入风险工具，每次调用都需用户确认。
 * 经 {@link WorkspaceAccess} 操作当前用户自己的工作区（受限应用只见 apps/&lt;code&gt;），不触及主机文件系统或 shell；
 * 删除进入回收站，覆盖保留历史版本，用户可在文件管理器中恢复。
 */
public final class ChatWorkspaceTools {

    public static final String LIST_TOOL = "workspace.list";
    public static final String READ_TOOL = "workspace.read";
    public static final String PERMISSION_CODE = "platform:chat:workspace:view";
    public static final String WRITE_TOOL = "workspace.write";
    public static final String MOVE_TOOL = "workspace.move";
    public static final String DELETE_TOOL = "workspace.delete";
    public static final String EDIT_PERMISSION_CODE = "platform:chat:workspace:edit";
    /** 模型写入的单文件上限（文本）：远小于工作区单文件上限，避免一次生成巨型文件 */
    private static final int MAX_WRITE_BYTES = 1024 * 1024;
    /** 单次读取上限：超出时截断，避免把整篇大文件塞进上下文。 */
    private static final int MAX_READ_CHARS = 60000;
    private static final int MAX_LIST = 500;

    private ChatWorkspaceTools() {
    }

    public static List<AiAgentTool> create(WorkspaceAccess access) {
        if (access == null) {
            return List.of();
        }
        return List.of(listTool(access), readTool(access));
    }

    /** 「编码」模式的写入工具：创建/覆盖文本文件、移动/改名、删除，均需用户逐次确认 */
    public static List<AiAgentTool> createEditable(WorkspaceAccess access) {
        if (access == null) {
            return List.of();
        }
        return List.of(writeTool(access), moveTool(access), deleteTool(access));
    }

    private static AiAgentTool listTool(WorkspaceAccess access) {
        return tool(LIST_TOOL, "列出工作区文件",
                "列出用户工作区中的目录与文件（路径、类型、大小、更新时间、来源）。可传 directory 只看某个目录；需要内容时再用 workspace.read。",
                PERMISSION_CODE, "查看我的工作区", "允许助手读取用户自己的工作区文件清单",
                Map.of("directory", Map.of("type", "string", "description", "可选：只列该目录（相对路径），默认整个工作区")),
                AgentToolRisk.READ, call -> {
                    List<WorkspaceEntryDTO> entries = access.list(text(call, "directory", false));
                    List<Map<String, Object>> items = entries.stream().limit(MAX_LIST).map(entry -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("path", entry.path());
                        item.put("kind", "DIRECTORY".equals(entry.kind()) ? "directory" : "file");
                        if (!"DIRECTORY".equals(entry.kind())) {
                            item.put("size", entry.size());
                        }
                        item.put("updatedAt", String.valueOf(entry.updatedAt()));
                        if (entry.updatedBy() != null) {
                            item.put("updatedBy", entry.updatedBy());
                        }
                        return item;
                    }).toList();
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("count", entries.size());
                    payload.put("entries", items);
                    if (entries.size() > MAX_LIST) {
                        payload.put("truncated", true);
                    }
                    String message = entries.isEmpty() ? "工作区为空，可提示用户上传或在编码模式中创建文件" : "共 " + entries.size() + " 个条目";
                    payload.put("message", message);
                    return new AiAgentToolResult(LIST_TOOL, "list", PERMISSION_CODE, message, payload);
                });
    }

    private static AiAgentTool readTool(WorkspaceAccess access) {
        return tool(READ_TOOL, "读取工作区文件",
                "按路径读取用户工作区中的文件内容；文本直接返回（超长截断），二进制或超过 2 MB 只返回大小与类型。",
                PERMISSION_CODE, "查看我的工作区", "允许助手读取用户自己的工作区文件内容",
                Map.of("path", Map.of("type", "string", "description", "工作区文件路径，来自 workspace.list")),
                AgentToolRisk.READ, call -> {
                    WorkspaceFileContentDTO content = access.read(text(call, "path", true), MAX_READ_CHARS);
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("path", content.path());
                    payload.put("size", content.size());
                    payload.put("contentType", content.contentType());
                    payload.put("revision", content.revision());
                    if (!"text".equals(content.encoding())) {
                        payload.put("encoding", "binary");
                        payload.put("message", "二进制或较大的文件（" + content.size() + " 字节），不返回正文");
                        return new AiAgentToolResult(READ_TOOL, "read", PERMISSION_CODE, "不返回正文：" + content.path(), payload);
                    }
                    payload.put("encoding", "text");
                    payload.put("truncated", content.truncated());
                    payload.put("content", content.content());
                    String message = content.truncated() ? "内容超过 " + MAX_READ_CHARS + " 字符，已截断" : "已读取 " + content.path();
                    payload.put("message", message);
                    return new AiAgentToolResult(READ_TOOL, "read", PERMISSION_CODE, message, payload);
                });
    }

    private static AiAgentTool writeTool(WorkspaceAccess access) {
        return tool(WRITE_TOOL, "写入工作区文件",
                "在用户工作区创建或整体覆盖一个文本文件（UTF-8，单文件最多 1 MB，缺少的目录自动创建）。覆盖前先用 workspace.read 读原文；旧内容保留为历史版本；执行前需用户确认。",
                EDIT_PERMISSION_CODE, "编辑我的工作区", "允许助手在用户确认后写入用户自己的工作区文件",
                Map.of("path", Map.of("type", "string", "description", "工作区相对路径，如 notes/plan.md"),
                        "content", Map.of("type", "string", "description", "文件完整内容")),
                AgentToolRisk.WRITE, call -> {
                    String path = text(call, "path", true);
                    Object content = call.arguments() == null ? null : call.arguments().get("content");
                    if (!(content instanceof String body)) {
                        throw new BizException("content 必须是文本");
                    }
                    if (body.getBytes(StandardCharsets.UTF_8).length > MAX_WRITE_BYTES) {
                        throw new BizException("单个文件最多 1 MB，请拆分后写入");
                    }
                    boolean existed = access.exists(path);
                    WorkspaceEntryDTO entry = access.writeText(path, body);
                    String message = (existed ? "已覆盖 " : "已创建 ") + entry.path();
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("path", entry.path());
                    payload.put("size", entry.size());
                    payload.put("revision", entry.revision());
                    payload.put("created", !existed);
                    payload.put("message", message);
                    return new AiAgentToolResult(WRITE_TOOL, "write", EDIT_PERMISSION_CODE, message, payload);
                });
    }

    private static AiAgentTool moveTool(WorkspaceAccess access) {
        return tool(MOVE_TOOL, "移动或重命名",
                "把工作区中的文件或目录移动/重命名到新路径（目录连同内容一起移动，目标不能已存在）；执行前需用户确认。",
                EDIT_PERMISSION_CODE, "编辑我的工作区", "允许助手在用户确认后移动或重命名用户自己的工作区文件",
                Map.of("from", Map.of("type", "string", "description", "原路径，来自 workspace.list"),
                        "to", Map.of("type", "string", "description", "新路径")),
                AgentToolRisk.WRITE, call -> {
                    String from = text(call, "from", true);
                    WorkspaceEntryDTO moved = access.move(from, text(call, "to", true));
                    String message = "已移动 " + from + " → " + moved.path();
                    return new AiAgentToolResult(MOVE_TOOL, "move", EDIT_PERMISSION_CODE, message,
                            Map.of("from", from, "to", moved.path(), "message", message));
                });
    }

    private static AiAgentTool deleteTool(WorkspaceAccess access) {
        return tool(DELETE_TOOL, "删除工作区文件",
                "删除用户工作区中的一个文件或目录（目录连同内容）；进入回收站，用户可恢复；执行前需用户确认。",
                EDIT_PERMISSION_CODE, "编辑我的工作区", "允许助手在用户确认后删除用户自己的工作区文件",
                Map.of("path", Map.of("type", "string", "description", "要删除的路径，来自 workspace.list")),
                AgentToolRisk.WRITE, call -> {
                    String path = text(call, "path", true);
                    int count = access.delete(path);
                    String message = "已删除 " + path + (count > 1 ? "（共 " + count + " 项）" : "") + "，可在回收站恢复";
                    return new AiAgentToolResult(DELETE_TOOL, "delete", EDIT_PERMISSION_CODE, message,
                            Map.of("path", path, "count", count, "message", message));
                });
    }

    private static String text(AiAgentToolCall call, String key, boolean required) {
        Object value = call.arguments() == null ? null : call.arguments().get(key);
        String text = value == null ? "" : String.valueOf(value).trim();
        if (required && text.isEmpty()) {
            throw new BizException("缺少参数 " + key);
        }
        return text;
    }

    private static AiAgentTool tool(String name, String title, String description, String permission, String permissionName,
                                    String permissionDescription, Map<String, Object> schema, AgentToolRisk risk,
                                    Function<AiAgentToolCall, AiAgentToolResult> executor) {
        AiAgentToolDescriptor descriptor = new AiAgentToolDescriptor(name, title, description, permission, permissionName,
                "AI 助手", permissionDescription, schema);
        return new AiAgentTool() {
            @Override
            public AiAgentToolDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public AgentToolRisk risk() {
                return risk;
            }

            @Override
            public AiAgentToolResult execute(AiAgentToolCall call) {
                return executor.apply(call);
            }
        };
    }
}
