package online.yudream.base.domain.platform.plugin.port;

import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginDetail;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginDescriptor;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface PluginStoreGateway {

    List<PluginStorePluginInfo> list();

    Optional<PluginStorePluginDetail> detail(String code);

    void downloadJar(PluginStorePluginDescriptor descriptor, Path target);
}
