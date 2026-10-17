package online.yudream.base.interfaces.platform.mcp.assembler;

import online.yudream.base.application.platform.mcp.cmd.McpServerSaveCmd;
import online.yudream.base.application.platform.mcp.dto.McpServerDTO;
import online.yudream.base.interfaces.platform.mcp.request.McpServerSaveRequest;
import online.yudream.base.interfaces.platform.mcp.res.McpServerRes;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class McpWebAssembler {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private McpWebAssembler() {
    }

    public static McpServerSaveCmd toCmd(McpServerSaveRequest request) {
        return toCmd(null, request);
    }

    public static McpServerSaveCmd toCmd(Long id, McpServerSaveRequest request) {
        return McpServerSaveCmd.builder()
                .id(id)
                .name(request.getName())
                .code(request.getCode())
                .description(request.getDescription())
                .transport(request.getTransport())
                .endpointUrl(request.getEndpointUrl())
                .headers(request.getHeaders())
                .enabled(request.getEnabled())
                .preload(request.getPreload())
                .timeoutMillis(request.getTimeoutMillis())
                .allowPrivateNetwork(request.getAllowPrivateNetwork())
                .build();
    }

    public static McpServerRes toRes(McpServerDTO dto) {
        if (dto == null) {
            return null;
        }
        List<McpServerRes.Tool> tools = dto.tools().stream()
                .map(tool -> McpServerRes.Tool.builder()
                        .name(tool.name())
                        .title(tool.title())
                        .description(tool.description())
                        .risk(tool.risk())
                        .requiresConfirmation(tool.requiresConfirmation())
                        .platformToolCode(tool.platformToolCode())
                        .build())
                .toList();
        return McpServerRes.builder()
                .id(dto.id())
                .code(dto.code())
                .name(dto.name())
                .description(dto.description())
                .transport(dto.transport())
                .endpointUrl(dto.endpointUrl())
                .headers(dto.headers())
                .enabled(dto.enabled())
                .preload(dto.preload())
                .timeoutMillis(dto.timeoutMillis())
                .allowPrivateNetwork(dto.allowPrivateNetwork())
                .lastStatus(dto.lastStatus())
                .lastError(dto.lastError())
                .lastConnectedAt(format(dto.lastConnectedAt()))
                .protocolVersion(dto.protocolVersion())
                .serverName(dto.serverName())
                .serverVersion(dto.serverVersion())
                .tools(tools)
                .moduleCode(dto.moduleCode())
                .moduleEnabled(dto.moduleEnabled())
                .updateTime(format(dto.updateTime()))
                .build();
    }

    private static String format(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return StringUtils.hasText(value.toString()) ? DATE_TIME.format(value) : "";
    }
}
