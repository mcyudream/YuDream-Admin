package online.yudream.base.domain.platform.plugin.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.plugin.aggregate.PluginMarketPublication;
import online.yudream.base.domain.platform.plugin.enumerate.PluginPublicationStatus;

import java.util.List;
import java.util.Optional;

public interface PluginMarketPublicationRepo {

    PluginMarketPublication save(PluginMarketPublication publication);

    Optional<PluginMarketPublication> findById(Long id);

    Optional<PluginMarketPublication> findByCodeAndVersion(String code, String version);

    List<PluginMarketPublication> findByStatus(PluginPublicationStatus status);

    /** 全量发布物，按创建时间倒序。 */
    List<PluginMarketPublication> findAll();

    /**
     * 管理端分页。status / publisherUserId 为空表示不限制。
     */
    PageResult<PluginMarketPublication> page(PluginPublicationStatus status, Long publisherUserId, int page, int size);

    /** 原子递增下载计数，返回递增后的值。 */
    long incrementDownloadCount(Long id);

    void deleteById(Long id);
}
