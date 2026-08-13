package online.yudream.base.application.system.log.dto;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Docker 容器日志采集配置。
 */
public record DockerLogSettingsDTO(boolean enabled, List<String> containers, String transport, String socket, long tail) {

    public DockerLogSettingsDTO {
        containers = containers == null ? List.of()
                : containers.stream().map(String::trim).filter(value -> !value.isBlank()).toList();
        transport = transport == null || transport.isBlank() ? "auto" : transport.trim().toLowerCase(Locale.ROOT);
        socket = socket == null || socket.isBlank() ? "/var/run/docker.sock" : socket;
        tail = Math.max(tail, 1);
    }

    public static DockerLogSettingsDTO defaults(boolean enabled, String containersCsv, String transport, String socket, long tail) {
        return new DockerLogSettingsDTO(enabled, split(containersCsv), transport, socket, tail);
    }

    private static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(value -> !value.isBlank()).toList();
    }
}
