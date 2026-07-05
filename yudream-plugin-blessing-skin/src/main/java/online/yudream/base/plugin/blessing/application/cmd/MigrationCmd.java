package online.yudream.base.plugin.blessing.application.cmd;

public record MigrationCmd(
        String driverClass,
        String jdbcUrl,
        String username,
        String password,
        String textureBaseDir
) {
}
