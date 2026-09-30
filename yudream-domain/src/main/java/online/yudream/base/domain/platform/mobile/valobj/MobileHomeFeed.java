package online.yudream.base.domain.platform.mobile.valobj;

/**
 * 移动端首页信息流内容源端点声明（plugin.yml mobile.home.feed 的领域镜像）。
 * <p>
 * endpoint 以 {@code /} 开头，指向插件 API 根下的相对端点，宿主 App 据此拉取插件的
 * 真实内容（帖子等）渲染首页信息流；title 为可选的信息流分节标题。
 */
public record MobileHomeFeed(
        String endpoint,
        String title
) {

    public MobileHomeFeed {
        endpoint = endpoint == null ? "" : endpoint;
        title = title == null ? "" : title;
    }
}
