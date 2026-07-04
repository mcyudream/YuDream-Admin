package online.yudream.base.plugin.authlib.interfaces.request;

public record AuthenticateRequest(
        Agent agent,
        String username,
        String password,
        String clientToken,
        Boolean requestUser
) {
    public record Agent(String name, Integer version) {
    }
}
