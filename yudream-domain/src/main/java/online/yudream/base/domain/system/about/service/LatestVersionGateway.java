package online.yudream.base.domain.system.about.service;

import online.yudream.base.domain.system.about.valobj.LatestVersionProbe;
import online.yudream.base.domain.system.about.valobj.LatestVersionTarget;

import java.util.List;

/**
 * 最新版本探测端口：向构件仓库（Nexus Maven/npm）查询契约包的最新发布版本。
 * 实现方必须保证：单个目标失败不影响其他目标，且不向调用方抛异常。
 */
public interface LatestVersionGateway {

    List<LatestVersionProbe> probe(List<LatestVersionTarget> targets);
}
