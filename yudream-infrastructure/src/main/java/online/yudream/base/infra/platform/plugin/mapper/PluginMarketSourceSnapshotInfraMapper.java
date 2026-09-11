package online.yudream.base.infra.platform.plugin.mapper;

import online.yudream.base.domain.platform.plugin.valobj.PluginMarketSourceSnapshot;
import online.yudream.base.domain.platform.plugin.valobj.PluginStoreCatalogEntry;
import online.yudream.base.domain.platform.plugin.valobj.PluginStoreCatalogVersion;
import online.yudream.base.infra.platform.plugin.dataobj.PluginMarketSourceSnapshotDO;

import java.util.ArrayList;
import java.util.List;

public class PluginMarketSourceSnapshotInfraMapper {

    private PluginMarketSourceSnapshotInfraMapper() {
    }

    public static PluginMarketSourceSnapshotDO toDataObj(PluginMarketSourceSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        PluginMarketSourceSnapshotDO dataObj = new PluginMarketSourceSnapshotDO();
        dataObj.setId(null);
        dataObj.setSourceId(snapshot.sourceId());
        dataObj.setSyncedAt(snapshot.syncedAt());
        List<PluginMarketSourceSnapshotDO.Entry> entries = new ArrayList<>();
        for (PluginStoreCatalogEntry entry : snapshot.entries()) {
            List<PluginMarketSourceSnapshotDO.Version> versions = new ArrayList<>();
            for (PluginStoreCatalogVersion version : entry.versions()) {
                versions.add(new PluginMarketSourceSnapshotDO.Version(version.releaseVersion(), version.descriptorUrl()));
            }
            entries.add(new PluginMarketSourceSnapshotDO.Entry(entry.code(), entry.indexUrl(),
                    entry.latestDescriptorJson(), versions));
        }
        dataObj.setEntries(entries);
        return dataObj;
    }

    public static PluginMarketSourceSnapshot toDomain(PluginMarketSourceSnapshotDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        List<PluginStoreCatalogEntry> entries = new ArrayList<>();
        if (dataObj.getEntries() != null) {
            for (PluginMarketSourceSnapshotDO.Entry entry : dataObj.getEntries()) {
                List<PluginStoreCatalogVersion> versions = new ArrayList<>();
                if (entry.getVersions() != null) {
                    for (PluginMarketSourceSnapshotDO.Version version : entry.getVersions()) {
                        versions.add(new PluginStoreCatalogVersion(version.getReleaseVersion(), version.getDescriptorUrl()));
                    }
                }
                entries.add(new PluginStoreCatalogEntry(entry.getCode(), entry.getIndexUrl(),
                        entry.getLatestDescriptorJson(), versions));
            }
        }
        return new PluginMarketSourceSnapshot(dataObj.getSourceId(), dataObj.getSyncedAt(), entries);
    }
}
