package online.yudream.base.plugin.skin.domain.valobj;

public record MigrationConfig(
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
