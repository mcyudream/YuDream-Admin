package online.yudream.base.plugin.skin.interfaces.res;

public record SkinPlayerRes(
        String uuid,
        String ownerId,
        String ownerName,
        String ownerUsername,
        String ownerEmail,
        String name,
        String skinHash,
        String capeHash,
        Long lastModified
) {
}
