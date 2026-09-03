package online.yudream.base.application.platform.preview.service;

import online.yudream.base.plugin.spi.system.storage.PluginStoredFile;

import java.util.Optional;

/**
 * 平台签名预览文件解析端口：校验 HMAC 短时效 token 并读取对应插件文件。
 * 由基础设施层实现（插件预览框架服务），供签名公开文件端点消费；
 *  token 本身即承载凭证（嵌入 URL），本端口不附加登录态。
 */
public interface PluginPreviewFileOpener {

    /**
     * 校验签名 token 并读取插件文件；token 无效/过期或对象不存在时返回空。
     */
    Optional<PluginStoredFile> openSignedFile(String token);
}
