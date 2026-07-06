package online.yudream.base.plugin.wallet.interfaces.res;

public record WalletUserRes(
        String id,
        String username,
        String nickname,
        String email,
        String avatar
) {
}
