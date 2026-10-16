package online.yudream.base.application.platform.wiki.dto;

/**
 * 公开端知识库资料（含封面与评论开关）。只暴露安全的封面色值/seed/URL 与替代文本，
 * 不回传文件对象 ID、ACL 模式或访问密钥。
 */
public record WikiPublicSpaceProfileDTO(String name, String slug, String description, String coverType,
                                        String coverSeed, String coverUrl, String coverAlt, boolean commentEnabled) {
}
