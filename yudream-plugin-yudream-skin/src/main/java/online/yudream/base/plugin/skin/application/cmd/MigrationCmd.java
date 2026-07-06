package online.yudream.base.plugin.skin.application.cmd;

public record MigrationCmd(
        String host,
        Integer port,
        String database,
        String username,
        String password,
        String textureBaseDir,
        String textureArchiveBase64,
        String textureArchiveName
) {
}
