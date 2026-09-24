package online.yudream.base.domain.system.about.service;

import online.yudream.base.domain.system.about.valobj.SpiBuildInfo;

import java.util.Optional;

/**
 * SPI 构建信息读取端口：实现方从 SPI 构件自身携带的资源中读取版本，读取不到返回空。
 */
public interface SpiBuildInfoGateway {

    Optional<SpiBuildInfo> read();
}
