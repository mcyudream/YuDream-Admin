package online.yudream.base.domain.system.about.valobj;

/**
 * SPI JAR 内 spi-build.properties 的内容：当前装载 SPI 的权威版本来源。
 */
public record SpiBuildInfo(String version, String artifact, String buildTime) {
}
