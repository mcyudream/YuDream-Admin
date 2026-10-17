package online.yudream.base.application.platform.plugin;

import online.yudream.base.application.platform.plugin.cmd.PluginMarketplaceBatchUpdateCmd;
import online.yudream.base.application.platform.plugin.service.PluginStoreAppService;
import online.yudream.base.domain.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 批量更新入口的参数校验：空清单与缺编码/版本的条目在触达市场与 JAR 前就应被拒绝。 */
class PluginMarketplaceBatchUpdateCmdTest {

    @Test
    void rejectsEmptyBatch() {
        PluginStoreAppService service = new PluginStoreAppService(null, null, null);

        BizException nullCmd = assertThrows(BizException.class, () -> service.updateBatch(null));
        assertTrue(nullCmd.getMessage().contains("更新清单不能为空"));

        PluginMarketplaceBatchUpdateCmd empty = new PluginMarketplaceBatchUpdateCmd();
        empty.setItems(List.of());
        BizException emptyCmd = assertThrows(BizException.class, () -> service.updateBatch(empty));
        assertTrue(emptyCmd.getMessage().contains("更新清单不能为空"));
    }

    @Test
    void rejectsItemsWithoutCodeOrVersion() {
        PluginStoreAppService service = new PluginStoreAppService(null, null, null);

        PluginMarketplaceBatchUpdateCmd noVersion = new PluginMarketplaceBatchUpdateCmd();
        noVersion.setItems(List.of(item("alpha", null)));
        BizException versionError = assertThrows(BizException.class, () -> service.updateBatch(noVersion));
        assertTrue(versionError.getMessage().contains("缺少编码或版本"));

        PluginMarketplaceBatchUpdateCmd noCode = new PluginMarketplaceBatchUpdateCmd();
        noCode.setItems(List.of(item(null, "1.1.0")));
        BizException codeError = assertThrows(BizException.class, () -> service.updateBatch(noCode));
        assertTrue(codeError.getMessage().contains("缺少编码或版本"));

        PluginMarketplaceBatchUpdateCmd nullEntry = new PluginMarketplaceBatchUpdateCmd();
        List<PluginMarketplaceBatchUpdateCmd.Item> items = new ArrayList<>();
        items.add(null);
        nullEntry.setItems(items);
        BizException nullError = assertThrows(BizException.class, () -> service.updateBatch(nullEntry));
        assertTrue(nullError.getMessage().contains("缺少编码或版本"));
    }

    private PluginMarketplaceBatchUpdateCmd.Item item(String code, String releaseVersion) {
        PluginMarketplaceBatchUpdateCmd.Item item = new PluginMarketplaceBatchUpdateCmd.Item();
        item.setCode(code);
        item.setReleaseVersion(releaseVersion);
        return item;
    }
}
