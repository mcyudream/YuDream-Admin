package online.yudream.base.plugin.skin.interfaces.request;

public record MigrationRequest(
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
