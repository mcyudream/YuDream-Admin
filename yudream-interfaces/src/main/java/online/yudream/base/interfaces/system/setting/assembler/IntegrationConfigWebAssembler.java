package online.yudream.base.interfaces.system.setting.assembler;

import online.yudream.base.application.system.setting.cmd.IntegrationConfigSaveCmd;
import online.yudream.base.application.system.setting.cmd.MailConfigCmd;
import online.yudream.base.application.system.setting.cmd.MailTestCmd;
import online.yudream.base.application.system.setting.cmd.StorageConfigCmd;
import online.yudream.base.application.system.setting.cmd.StorageTestCmd;
import online.yudream.base.application.system.setting.dto.IntegrationConfigDTO;
import online.yudream.base.interfaces.system.setting.request.IntegrationConfigSaveRequest;
import online.yudream.base.interfaces.system.setting.request.MailTestRequest;
import online.yudream.base.interfaces.system.setting.request.StorageTestRequest;
import online.yudream.base.interfaces.system.setting.res.IntegrationConfigRes;

/**
 * 系统集成配置接口装配：request -> cmd、DTO -> res。
 */
public final class IntegrationConfigWebAssembler {

    private IntegrationConfigWebAssembler() {
    }

    public static IntegrationConfigSaveCmd toCmd(IntegrationConfigSaveRequest request) {
        IntegrationConfigSaveCmd cmd = new IntegrationConfigSaveCmd();
        if (request.getMail() != null) {
            MailConfigCmd mail = new MailConfigCmd();
            mail.setHost(request.getMail().getHost());
            mail.setPort(request.getMail().getPort());
            mail.setUsername(request.getMail().getUsername());
            mail.setPassword(request.getMail().getPassword());
            mail.setFrom(request.getMail().getFrom());
            mail.setSsl(request.getMail().getSsl());
            mail.setStarttls(request.getMail().getStarttls());
            cmd.setMail(mail);
        }
        if (request.getStorage() != null) {
            StorageConfigCmd storage = new StorageConfigCmd();
            storage.setEndpoint(request.getStorage().getEndpoint());
            storage.setAccessKey(request.getStorage().getAccessKey());
            storage.setSecretKey(request.getStorage().getSecretKey());
            storage.setBucket(request.getStorage().getBucket());
            storage.setRegion(request.getStorage().getRegion());
            storage.setPathStyle(request.getStorage().getPathStyle());
            cmd.setStorage(storage);
        }
        return cmd;
    }

    public static MailTestCmd toCmd(MailTestRequest request) {
        MailTestCmd cmd = new MailTestCmd();
        cmd.setHost(request.getHost());
        cmd.setPort(request.getPort());
        cmd.setUsername(request.getUsername());
        cmd.setPassword(request.getPassword());
        cmd.setFrom(request.getFrom());
        cmd.setSsl(request.getSsl());
        cmd.setStarttls(request.getStarttls());
        cmd.setTo(request.getTo());
        return cmd;
    }

    public static StorageTestCmd toCmd(StorageTestRequest request) {
        StorageTestCmd cmd = new StorageTestCmd();
        cmd.setEndpoint(request.getEndpoint());
        cmd.setAccessKey(request.getAccessKey());
        cmd.setSecretKey(request.getSecretKey());
        cmd.setBucket(request.getBucket());
        cmd.setRegion(request.getRegion());
        cmd.setPathStyle(request.getPathStyle());
        cmd.setAutoCreate(request.getAutoCreate());
        return cmd;
    }

    public static IntegrationConfigRes toRes(IntegrationConfigDTO dto) {
        IntegrationConfigRes.MailSection mail = IntegrationConfigRes.MailSection.builder()
                .host(dto.getMail().getHost())
                .port(dto.getMail().getPort())
                .username(dto.getMail().getUsername())
                .from(dto.getMail().getFrom())
                .ssl(dto.getMail().getSsl())
                .starttls(dto.getMail().getStarttls())
                .passwordSet(dto.getMail().isPasswordSet())
                .source(dto.getMail().getSource())
                .build();
        IntegrationConfigRes.StorageSection storage = IntegrationConfigRes.StorageSection.builder()
                .endpoint(dto.getStorage().getEndpoint())
                .accessKey(dto.getStorage().getAccessKey())
                .bucket(dto.getStorage().getBucket())
                .region(dto.getStorage().getRegion())
                .pathStyle(dto.getStorage().getPathStyle())
                .secretKeySet(dto.getStorage().isSecretKeySet())
                .source(dto.getStorage().getSource())
                .build();
        return IntegrationConfigRes.builder().mail(mail).storage(storage).build();
    }
}
