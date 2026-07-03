package online.yudream.base.infra.system.file.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.file.service.ObjectStorage;
import online.yudream.base.domain.system.file.valobj.StoredObject;
import online.yudream.base.infra.system.file.config.S3StorageProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client s3Client;
    private final S3StorageProperties properties;

    @Override
    public String bucket() {
        return properties.getBucket();
    }

    @Override
    public String put(String objectKey, InputStream inputStream, long contentLength, String contentType) {
        ensureBucket();
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .contentType(contentType)
                    .contentLength(contentLength)
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, contentLength));
            return objectKey;
        }
        catch (S3Exception e) {
            throw new BizException("文件上传失败：" + e.awsErrorDetails().errorMessage());
        }
    }

    @Override
    public StoredObject get(String objectKey) {
        try {
            ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .build());
            GetObjectResponse response = stream.response();
            return new StoredObject(objectKey, response.contentType(), response.contentLength(), stream);
        }
        catch (NoSuchKeyException e) {
            throw new BizException("文件不存在");
        }
        catch (S3Exception e) {
            throw new BizException("文件读取失败：" + e.awsErrorDetails().errorMessage());
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            s3Client.deleteObject(builder -> builder.bucket(properties.getBucket()).key(objectKey));
        }
        catch (S3Exception e) {
            throw new BizException("文件删除失败：" + e.awsErrorDetails().errorMessage());
        }
    }

    private void ensureBucket() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(properties.getBucket()).build());
        }
        catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(properties.getBucket()).build());
        }
        catch (S3Exception e) {
            if (e.statusCode() == 404) {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(properties.getBucket()).build());
                return;
            }
            if (e.statusCode() == 403) {
                log.warn("S3 bucket head check forbidden, continue with object operation. bucket={}", properties.getBucket());
                return;
            }
            throw new BizException("对象存储 Bucket 检查失败：" + e.awsErrorDetails().errorMessage());
        }
    }
}
