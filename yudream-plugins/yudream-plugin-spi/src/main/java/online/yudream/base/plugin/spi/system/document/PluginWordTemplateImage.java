package online.yudream.base.plugin.spi.system.document;

/**
 * Word 模板图片值：渲染时替换为嵌入图片而不是文本。
 * 放进 render 的 data map 后，段落内容仅由对应占位符构成时整段嵌入图片
 * （推荐放在表格单元格或独立段落里）；旧版宿主引擎不识别该类型，会把值当普通对象转文本。
 * 格式白名单：png/jpeg/jpg/gif/bmp；widthEmu 为空时按图片原始尺寸嵌入并限制最大宽度。
 */
public record PluginWordTemplateImage(byte[] content, String format, Integer widthEmu) {

    private static final int MAX_DEFAULT_WIDTH_EMU = 5_486_400;

    public PluginWordTemplateImage {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("图片内容不能为空");
        }
        format = format == null ? "" : format.trim().toLowerCase(java.util.Locale.ROOT);
        if (!isSupportedFormat(format)) {
            throw new IllegalArgumentException("不支持的图片格式：" + format + "（支持 png/jpeg/jpg/gif/bmp）");
        }
        if (widthEmu != null && widthEmu <= 0) {
            throw new IllegalArgumentException("图片宽度必须为正数");
        }
        if (widthEmu != null && widthEmu > MAX_DEFAULT_WIDTH_EMU * 2) {
            throw new IllegalArgumentException("图片宽度超出页面上限");
        }
    }

    public static boolean isSupportedFormat(String format) {
        String value = format == null ? "" : format.trim().toLowerCase(java.util.Locale.ROOT);
        return "png".equals(value) || "jpeg".equals(value) || "jpg".equals(value)
                || "gif".equals(value) || "bmp".equals(value);
    }

    /** PNG 便捷工厂：默认宽度（原始尺寸，超宽收缩到 6 英寸内）。 */
    public static PluginWordTemplateImage png(byte[] content) {
        return new PluginWordTemplateImage(content, "png", null);
    }

    /** JPEG 便捷工厂。 */
    public static PluginWordTemplateImage jpeg(byte[] content) {
        return new PluginWordTemplateImage(content, "jpeg", null);
    }

    /** 默认最大嵌入宽度：6 英寸。 */
    public static int maxDefaultWidthEmu() {
        return MAX_DEFAULT_WIDTH_EMU;
    }
}
