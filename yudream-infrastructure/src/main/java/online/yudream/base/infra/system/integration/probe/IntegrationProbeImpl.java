package online.yudream.base.infra.system.integration.probe;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import online.yudream.base.domain.system.integration.service.IntegrationProbe;
import online.yudream.base.domain.system.integration.valobj.IntegrationProbeResult;
import online.yudream.base.domain.system.integration.valobj.MailServerConfig;
import online.yudream.base.domain.system.integration.valobj.ObjectStorageConfig;
import online.yudream.base.infra.system.integration.MailSenderFactory;
import online.yudream.base.infra.system.integration.StorageClientProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * 集成配置探测实现：对候选配置构建一次性短连接，探测完即关闭。
 */
@Component
public class IntegrationProbeImpl implements IntegrationProbe {

    @Override
    public IntegrationProbeResult sendTestMail(MailServerConfig config, String to) {
        if (!config.configured()) {
            return IntegrationProbeResult.fail("SMTP 主机不能为空");
        }
        if (!StringUtils.hasText(to)) {
            return IntegrationProbeResult.fail("测试收件地址不能为空");
        }
        JavaMailSenderImpl sender = MailSenderFactory.build(config);
        try {
            MimeMessage mimeMessage = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setFrom(StringUtils.hasText(config.from()) ? config.from() : config.username());
            helper.setTo(to.trim());
            helper.setSubject("YuDream Admin 邮件配置测试");
            helper.setText("这是一封测试邮件：当前 SMTP 配置验证通过。", false);
            sender.send(mimeMessage);
            return IntegrationProbeResult.ok("测试邮件已发送至 " + to.trim());
        }
        catch (MessagingException e) {
            return IntegrationProbeResult.fail("邮件构造失败：" + e.getMessage());
        }
        catch (MailException e) {
            return IntegrationProbeResult.fail("发送失败：" + e.getMessage());
        }
    }

    @Override
    public IntegrationProbeResult checkStorage(ObjectStorageConfig config, boolean autoCreate) {
        if (!config.configured()) {
            return IntegrationProbeResult.fail("对象存储 Endpoint 不能为空");
        }
        try (S3Client client = StorageClientProvider.build(config)) {
            try {
                client.headBucket(HeadBucketRequest.builder().bucket(config.bucket()).build());
                return IntegrationProbeResult.ok("Bucket「" + config.bucket() + "」连接成功");
            }
            catch (NoSuchBucketException e) {
                if (!autoCreate) {
                    return IntegrationProbeResult.fail("Bucket「" + config.bucket() + "」不存在");
                }
                client.createBucket(CreateBucketRequest.builder().bucket(config.bucket()).build());
                return IntegrationProbeResult.ok("Bucket「" + config.bucket() + "」不存在，已自动创建并连接成功");
            }
            catch (S3Exception e) {
                if (e.statusCode() == 404 && autoCreate) {
                    client.createBucket(CreateBucketRequest.builder().bucket(config.bucket()).build());
                    return IntegrationProbeResult.ok("Bucket「" + config.bucket() + "」不存在，已自动创建并连接成功");
                }
                return IntegrationProbeResult.fail("连接失败：" + e.awsErrorDetails().errorMessage());
            }
        }
        catch (Exception e) {
            return IntegrationProbeResult.fail("连接失败：" + e.getMessage());
        }
    }
}
