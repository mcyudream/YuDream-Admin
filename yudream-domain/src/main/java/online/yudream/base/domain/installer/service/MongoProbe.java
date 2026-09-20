package online.yudream.base.domain.installer.service;

import online.yudream.base.domain.installer.valobj.MongoProbeResult;
import online.yudream.base.domain.installer.valobj.MongoProbeSpec;

/**
 * MongoDB 连接探针端口：安装向导用于连通性与认证探测，实现不得产生长驻资源。
 */
public interface MongoProbe {

    MongoProbeResult probe(MongoProbeSpec spec);
}
