package online.yudream.base.plugin.authlib.interfaces.request;

public record SignoutRequest(
        String username,
        String password
) {
}
