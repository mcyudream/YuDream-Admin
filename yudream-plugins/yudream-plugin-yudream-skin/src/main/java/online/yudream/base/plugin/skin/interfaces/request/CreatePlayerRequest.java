package online.yudream.base.plugin.skin.interfaces.request;

public record CreatePlayerRequest(
        String name,
        String ownerId
) {
}
