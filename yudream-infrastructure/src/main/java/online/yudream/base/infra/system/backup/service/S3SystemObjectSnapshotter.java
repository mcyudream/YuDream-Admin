package online.yudream.base.infra.system.backup.service;

import online.yudream.base.domain.system.backup.service.SystemObjectSnapshotter;
import online.yudream.base.domain.system.file.valobj.StoredObject;
import online.yudream.base.infra.system.integration.StorageClientProvider;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

import java.util.function.Consumer;

/**
 * 对象存储全量快照：遍历桶内全部对象（用户上传 + 插件资产）逐个流式交给消费方。
 * 跳过目录占位对象（0 字节且以 / 结尾）。
 */
@Service
public class S3SystemObjectSnapshotter implements SystemObjectSnapshotter {

    private final StorageClientProvider storage;

    public S3SystemObjectSnapshotter(StorageClientProvider storage) {
        this.storage = storage;
    }

    @Override
    public void streamObjects(Consumer<StoredObject> consumer) {
        String bucket = storage.config().bucket();
        var client = storage.client();
        var paginator = client.listObjectsV2Paginator(
                ListObjectsV2Request.builder().bucket(bucket).build());
        paginator.contents().forEach(summary -> {
            String key = summary.key();
            if (summary.size() == 0 && key.endsWith("/")) {
                return;
            }
            ResponseInputStream<GetObjectResponse> stream =
                    client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
            GetObjectResponse headers = stream.response();
            consumer.accept(new StoredObject(key, headers.contentType(), summary.size(), stream));
        });
    }
}
