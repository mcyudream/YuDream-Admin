package online.yudream.base.domain.installer.service;

import online.yudream.base.domain.installer.valobj.RedisProbeResult;
import online.yudream.base.domain.installer.valobj.RedisProbeSpec;

/**
 * Redis 连接探针端口：安装向导用于连通性与认证探测，实现不得产生长驻资源。
 */
public interface RedisProbe {

    RedisProbeResult probe(RedisProbeSpec spec);
}
