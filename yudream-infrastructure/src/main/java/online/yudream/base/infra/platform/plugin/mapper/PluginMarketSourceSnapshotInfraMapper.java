package online.yudream.base.infra.platform.plugin.mapper;

import online.yudream.base.domain.platform.plugin.valobj.PluginMarketSourceSnapshot;
import online.yudream.base.domain.platform.plugin.valobj.PluginStoreCatalogEntry;
import online.yudream.base.domain.platform.plugin.valobj.PluginStoreCatalogVersion;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginDependency;
import online.yudream.base.domain.platform.plugin.valobj.PluginStoreStructuredVersion;
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
            List<PluginMarketSourceSnapshotDO.StructuredVersion> structured = new ArrayList<>();
            for (PluginStoreStructuredVersion version : entry.structuredVersions()) {
                List<PluginMarketSourceSnapshotDO.Dependency> dependencies = new ArrayList<>();
                for (PluginStorePluginDependency dependency : version.dependencies()) {
                    dependencies.add(new PluginMarketSourceSnapshotDO.Dependency(
                            dependency.code(), dependency.range(), dependency.required()));
                }
                structured.add(new PluginMarketSourceSnapshotDO.StructuredVersion(
                        version.releaseVersion(), version.downloadUrl(), version.sha256(), version.main(),
                        version.displayName(), version.description(), version.sizeBytes(), version.category(),
                        version.tags() == null ? List.of() : new ArrayList<>(version.tags()),
                        version.compatibility() == null ? java.util.Map.of() : new java.util.LinkedHashMap<>(version.compatibility()),
                        dependencies));
            }
            entries.add(new PluginMarketSourceSnapshotDO.Entry(entry.code(), entry.indexUrl(),
                    entry.latestDescriptorJson(), versions, structured));
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
                List<PluginStoreStructuredVersion> structured = new ArrayList<>();
                if (entry.getStructuredVersions() != null) {
                    for (PluginMarketSourceSnapshotDO.StructuredVersion version : entry.getStructuredVersions()) {
                        List<PluginStorePluginDependency> dependencies = new ArrayList<>();
                        if (version.getDependencies() != null) {
                            for (PluginMarketSourceSnapshotDO.Dependency dependency : version.getDependencies()) {
                                dependencies.add(new PluginStorePluginDependency(
                                        dependency.getCode(), dependency.getRange(), dependency.isRequired()));
                            }
                        }
                        structured.add(new PluginStoreStructuredVersion(
                                version.getReleaseVersion(), version.getDownloadUrl(), version.getSha256(),
                                version.getMain(), version.getDisplayName(), version.getDescription(),
                                version.getSizeBytes(), version.getCategory(), version.getTags(),
                                version.getCompatibility(), dependencies));
                    }
                }
                entries.add(new PluginStoreCatalogEntry(entry.getCode(), entry.getIndexUrl(),
                        entry.getLatestDescriptorJson(), versions, structured));
            }
        }
        return new PluginMarketSourceSnapshot(dataObj.getSourceId(), dataObj.getSyncedAt(), entries);
    }
}
