package online.yudream.base.plugin.blessing.interfaces.request;

public record CreatePlayerRequest(
        String name,
        String ownerId
) {
}
