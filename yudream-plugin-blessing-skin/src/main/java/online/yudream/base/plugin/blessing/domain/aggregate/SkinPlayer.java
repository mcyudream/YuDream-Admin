package online.yudream.base.plugin.blessing.domain.aggregate;

import java.util.Locale;

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

    public SkinPlayer withName(String newName) {
        String normalized = newName == null ? null : newName.trim();
        return new SkinPlayer(
                uuid,
                ownerId,
                normalized,
                normalized == null ? null : normalized.toLowerCase(Locale.ROOT),
                skinHash,
                capeHash,
                migratedPid,
                System.currentTimeMillis()
        );
    }
}
