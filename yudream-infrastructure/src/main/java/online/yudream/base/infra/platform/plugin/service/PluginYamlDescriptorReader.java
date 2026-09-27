package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.plugin.spi.core.PluginDescriptor;
import online.yudream.base.plugin.spi.core.PluginMobileHomeCard;
import online.yudream.base.plugin.spi.core.PluginMobileHomeFeed;
import online.yudream.base.plugin.spi.core.PluginMobileSupport;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PluginYamlDescriptorReader {

    public PluginDescriptor read(InputStream inputStream) {
        if (inputStream == null) {
            throw new BizException("插件 JAR 缺少 plugin.yml");
        }
        Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(inputStream);
        if (!(loaded instanceof Map<?, ?> values)) {
            throw new BizException("plugin.yml 必须是 YAML 对象");
        }
        String code = required(values, "name");
        String displayName = value(values, "displayName");
        return new PluginDescriptor(
                code,
                StringUtils.hasText(displayName) ? displayName : code,
                required(values, "version"),
                value(values, "description"),
                required(values, "main"),
                list(values, "depend"),
                list(values, "softdepend"),
                optionalIcon(values),
                optionalGitUrl(values),
                optionalMobileSupport(values)
        );
    }

    private String required(Map<?, ?> values, String key) {
        String value = value(values, key);
        if (!StringUtils.hasText(value)) {
            throw new BizException("plugin.yml 缺少 " + key);
        }
        return value;
    }

    private String value(Map<?, ?> values, String key) {
        Object value = values.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String optionalIcon(Map<?, ?> values) {
        String icon = value(values, "icon");
        if (!StringUtils.hasText(icon)) {
            return null;
        }
        if (icon.contains("..") || icon.startsWith("/") || icon.matches("^[A-Za-z]:.*")) {
            throw new BizException("plugin.yml 的 icon 路径无效");
        }
        return icon;
    }

    /** plugin.yml 可选 git 字段：源码仓库地址，仅接受 http(s) URL。 */
    private String optionalGitUrl(Map<?, ?> values) {
        String gitUrl = value(values, "git");
        if (!StringUtils.hasText(gitUrl)) {
            return null;
        }
        if (gitUrl.length() > 200 || !(gitUrl.startsWith("http://") || gitUrl.startsWith("https://"))
                || gitUrl.contains("..") || gitUrl.matches(".*[\\s<>\"'].*")) {
            throw new BizException("plugin.yml 的 git 必须是合法的 http(s) 仓库地址");
        }
        return gitUrl;
    }

    /**
     * plugin.yml 可选 mobile 块：platforms（缺省 android+ios）、minHostVersion（缺省 1.0.0）、
     * requiredNativeCapabilities（缺省空）；name/description/icon 为移动端展示声明（可选），
     * home.cards 为移动端主页卡片声明（可选，≤10 项），home.feed 为移动端首页信息流
     * 内容源端点声明（可选，至多一个）。注册期校验合法性：platforms 只允许 android/ios。
     */
    private PluginMobileSupport optionalMobileSupport(Map<?, ?> values) {
        Object mobile = values.get("mobile");
        if (mobile == null) {
            return null;
        }
        if (!(mobile instanceof Map<?, ?> mobileValues)) {
            throw new BizException("plugin.yml 的 mobile 必须是 YAML 对象");
        }
        List<String> platforms = list(mobileValues, "platforms");
        for (String platform : platforms) {
            if (!"android".equalsIgnoreCase(platform) && !"ios".equalsIgnoreCase(platform)) {
                throw new BizException("plugin.yml 的 mobile.platforms 仅支持 android/ios：" + platform);
            }
        }
        String minHostVersion = value(mobileValues, "minHostVersion");
        if (StringUtils.hasText(minHostVersion) && !minHostVersion.matches("[0-9]+(\\.[0-9]+){0,3}(-[0-9A-Za-z.-]+)?")) {
            throw new BizException("plugin.yml 的 mobile.minHostVersion 必须是语义化版本：" + minHostVersion);
        }
        String name = optionalDisplayValue(mobileValues, "name", 64);
        String description = optionalDisplayValue(mobileValues, "description", 256);
        String icon = optionalDisplayValue(mobileValues, "icon", 64);
        List<PluginMobileHomeCard> homeCards = optionalHomeCards(mobileValues);
        PluginMobileHomeFeed homeFeed = optionalHomeFeed(mobileValues);
        return new PluginMobileSupport(
                platforms,
                minHostVersion,
                list(mobileValues, "requiredNativeCapabilities"),
                name,
                description,
                icon,
                homeCards,
                homeFeed
        );
    }

    /** mobile 块可选展示字段（name/description/icon）：空白视为缺省，超长拒绝。 */
    private String optionalDisplayValue(Map<?, ?> values, String key, int maxLength) {
        String value = value(values, key);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.length() > maxLength) {
            throw new BizException("plugin.yml 的 mobile." + key + " 长度不能超过 " + maxLength);
        }
        return value;
    }

    /** mobile.home.cards 主页卡片声明：整体可选，声明后逐项校验必填字段与约束。 */
    private List<PluginMobileHomeCard> optionalHomeCards(Map<?, ?> mobileValues) {
        Object home = mobileValues.get("home");
        if (home == null) {
            return List.of();
        }
        if (!(home instanceof Map<?, ?> homeValues)) {
            throw new BizException("plugin.yml 的 mobile.home 必须是 YAML 对象");
        }
        Object cards = homeValues.get("cards");
        if (cards == null) {
            return List.of();
        }
        if (!(cards instanceof List<?> cardList)) {
            throw new BizException("plugin.yml 的 mobile.home.cards 必须是列表");
        }
        if (cardList.size() > 10) {
            throw new BizException("plugin.yml 的 mobile.home.cards 数量不能超过 10");
        }
        List<PluginMobileHomeCard> parsed = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (int index = 0; index < cardList.size(); index++) {
            Object item = cardList.get(index);
            String prefix = "plugin.yml 的 mobile.home.cards[" + index + "]";
            if (!(item instanceof Map<?, ?> cardValues)) {
                throw new BizException(prefix + " 必须是 YAML 对象");
            }
            String id = requiredFieldValue(cardValues, prefix, "id");
            if (!id.matches("[a-z0-9][a-z0-9-]{0,63}")) {
                throw new BizException(prefix + ".id 必须匹配 [a-z0-9-]：" + id);
            }
            if (!seenIds.add(id)) {
                throw new BizException(prefix + ".id 重复：" + id);
            }
            String title = requiredFieldValue(cardValues, prefix, "title");
            if (title.length() > 32) {
                throw new BizException(prefix + ".title 长度不能超过 32");
            }
            String description = optionalFieldValue(cardValues, prefix, "description", 128);
            String icon = optionalFieldValue(cardValues, prefix, "icon", 64);
            String route = requiredFieldValue(cardValues, prefix, "route");
            if (route.length() > 128) {
                throw new BizException(prefix + ".route 长度不能超过 128");
            }
            if (!route.startsWith("/")) {
                throw new BizException(prefix + ".route 必须以 / 开头：" + route);
            }
            parsed.add(new PluginMobileHomeCard(id, title, description, icon, route));
        }
        return List.copyOf(parsed);
    }

    /**
     * mobile.home.feed 首页信息流内容源端点声明：整体可选，至多一个；与 home.cards 可共存。
     * endpoint 必填、以 / 开头、≤128 字符且不含空白；title 可选 ≤32 字符。
     */
    private PluginMobileHomeFeed optionalHomeFeed(Map<?, ?> mobileValues) {
        Object home = mobileValues.get("home");
        if (home == null) {
            return null;
        }
        if (!(home instanceof Map<?, ?> homeValues)) {
            throw new BizException("plugin.yml 的 mobile.home 必须是 YAML 对象");
        }
        Object feed = homeValues.get("feed");
        if (feed == null) {
            return null;
        }
        if (!(feed instanceof Map<?, ?> feedValues)) {
            throw new BizException("plugin.yml 的 mobile.home.feed 必须是 YAML 对象");
        }
        String prefix = "plugin.yml 的 mobile.home.feed";
        String endpoint = requiredFieldValue(feedValues, prefix, "endpoint");
        if (!endpoint.startsWith("/")) {
            throw new BizException(prefix + ".endpoint 必须以 / 开头：" + endpoint);
        }
        if (endpoint.length() > 128) {
            throw new BizException(prefix + ".endpoint 长度不能超过 128");
        }
        if (endpoint.matches(".*\\s.*")) {
            throw new BizException(prefix + ".endpoint 不能包含空白字符：" + endpoint);
        }
        String title = optionalFieldValue(feedValues, prefix, "title", 32);
        return new PluginMobileHomeFeed(endpoint, title);
    }

    private String requiredFieldValue(Map<?, ?> cardValues, String prefix, String key) {
        String value = value(cardValues, key);
        if (!StringUtils.hasText(value)) {
            throw new BizException(prefix + "." + key + " 不能为空");
        }
        return value;
    }

    private String optionalFieldValue(Map<?, ?> cardValues, String prefix, String key, int maxLength) {
        String value = value(cardValues, key);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.length() > maxLength) {
            throw new BizException(prefix + "." + key + " 长度不能超过 " + maxLength);
        }
        return value;
    }

    private List<String> list(Map<?, ?> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new BizException("plugin.yml 的 " + key + " 必须是列表");
        }
        return list.stream()
                .map(item -> item == null ? "" : String.valueOf(item).trim())
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }
}
