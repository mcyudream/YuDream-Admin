package online.yudream.base.interfaces.system.log.assembler;

import online.yudream.base.application.system.log.cmd.DockerLogSettingsUpdateCmd;
import online.yudream.base.application.system.log.dto.DockerLogSettingsDTO;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.log.model.SystemLogEntry;
import online.yudream.base.interfaces.system.log.request.DockerLogSettingsUpdateRequest;
import online.yudream.base.interfaces.system.log.res.DockerLogSettingsRes;
import online.yudream.base.interfaces.system.log.res.SystemLogRes;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class SystemLogWebAssembler {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    private SystemLogWebAssembler() {
    }

    public static Set<String> splitModules(String modules) {
        if (modules == null || modules.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(modules.split(",")).map(String::trim).filter(value -> !value.isBlank()).collect(Collectors.toSet());
    }

    public static SystemLogRes toRes(SystemLogEntry entry) {
        return new SystemLogRes(
                entry.sequence(),
                entry.timestamp(),
                TIME.format(Instant.ofEpochMilli(entry.timestamp())),
                entry.level().name(),
                entry.module(),
                entry.thread(),
                entry.traceId(),
                entry.logger(),
                entry.message(),
                entry.throwable());
    }

    public static PageResult<SystemLogRes> toPage(PageResult<SystemLogEntry> page) {
        return new PageResult<>(
                page.getRecords().stream().map(SystemLogWebAssembler::toRes).toList(),
                page.getTotal(), page.getPage(), page.getSize());
    }

    public static DockerLogSettingsRes toRes(DockerLogSettingsDTO dto) {
        return new DockerLogSettingsRes(dto.enabled(), dto.containers(), dto.transport(), dto.socket(), dto.tail());
    }

    public static DockerLogSettingsUpdateCmd toCmd(DockerLogSettingsUpdateRequest request) {
        return new DockerLogSettingsUpdateCmd(request.enabled(), request.containers(), request.transport(), request.socket(), request.tail());
    }

    /** 将日志按时间正序格式化为可下载的纯文本。 */
    public static String toLogText(List<SystemLogEntry> entries) {
        StringBuilder builder = new StringBuilder();
        for (SystemLogEntry entry : entries.reversed()) {
            builder.append(TIME.format(Instant.ofEpochMilli(entry.timestamp())))
                    .append(" [").append(entry.level().name()).append(']')
                    .append(" [").append(entry.module()).append(']')
                    .append(" [").append(entry.thread()).append(']')
                    .append(' ').append(entry.logger())
                    .append(" - ").append(entry.message())
                    .append(System.lineSeparator());
            if (entry.throwable() != null && !entry.throwable().isBlank()) {
                builder.append(entry.throwable()).append(System.lineSeparator());
            }
        }
        return builder.toString();
    }
}
