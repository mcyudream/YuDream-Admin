package online.yudream.base.domain.system.about.service;

import online.yudream.base.domain.system.about.valobj.AboutBuildInfo;

import java.util.Optional;

/**
 * 框架构建信息读取端口：实现方从宿主构件自身携带的资源中读取版本，读取不到返回空。
 */
public interface AboutBuildInfoGateway {

    Optional<AboutBuildInfo> read();
}
