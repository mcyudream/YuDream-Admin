package online.yudream.base.application.platform.seo;

/**
 * 单个路由的 SEO 元数据，由 {@link SeoHeadInjector} 注入静态壳 head。
 *
 * @param title       页面标题（覆盖静态壳的「正在加载」）
 * @param description 页面描述（meta description 与 og:description 共用）
 * @param image       社交分享图（og:image，绝对地址，可空）
 * @param noindex     非公开路径注入 noindex,nofollow
 */
public record SeoMeta(String title, String description, String image, boolean noindex) {
}
