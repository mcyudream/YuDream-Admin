package online.yudream.base.plugin.blessing.domain.aggregate;

public record SkinPlayer(
        String uuid,
        String ownerId,
        String name,
        String nameLower,
        String skinHash,
        String capeHash,
        Long migratedPid,
        Long lastModified
) {
    public SkinPlayer withTextures(String newSkinHash, String newCapeHash) {
        return new SkinPlayer(uuid, ownerId, name, nameLower, newSkinHash, newCapeHash, migratedPid, System.currentTimeMillis());
    }
}
