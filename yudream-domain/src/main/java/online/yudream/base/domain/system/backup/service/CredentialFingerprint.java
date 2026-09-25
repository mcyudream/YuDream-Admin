package online.yudream.base.domain.system.backup.service;

/**
 * 主密钥指纹端口：归档清单记录密钥来源指纹，导入时与当前主密钥比对以提示跨主机兼容性。
 */
public interface CredentialFingerprint {

    /** 当前主密钥指纹（16 位十六进制）；未配置主密钥返回空串。 */
    String fingerprint();
}
