package at.fhtw.docflow.document.integration;

import at.fhtw.docflow.configuration.StorageProperties;
import at.fhtw.docflow.configuration.UploadProperties;
import at.fhtw.docflow.document.application.DocumentContentNotFoundException;
import at.fhtw.docflow.document.application.DocumentStorage;
import at.fhtw.docflow.document.application.StorageUnavailableException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.io.ByteArrayInputStream;
import org.springframework.stereotype.Component;

@Component
public class MinioDocumentStorage implements DocumentStorage {
    private final MinioClient client;
    private final StorageProperties properties;
    private final UploadProperties limits;

    public MinioDocumentStorage(MinioClient client, StorageProperties properties, UploadProperties limits) {
        this.client = client;
        this.properties = properties;
        this.limits = limits;
    }

    @Override
    public void put(String key, byte[] content) {
        requireDocumentKey(key);
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build())) {
                try {
                    client.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
                } catch (ErrorResponseException exception) {
                    if (!exception.errorResponse().code().equals("BucketAlreadyOwnedByYou")) {
                        throw exception;
                    }
                }
            }
            client.putObject(PutObjectArgs.builder().bucket(properties.bucket()).object(key)
                    .stream(new ByteArrayInputStream(content), (long) content.length, -1L).contentType("application/pdf").build());
        } catch (Exception exception) {
            throw new StorageUnavailableException(exception);
        }
    }

    @Override
    public byte[] get(String key) {
        requireDocumentKey(key);
        try (var stream = client.getObject(GetObjectArgs.builder().bucket(properties.bucket()).object(key).build())) {
            byte[] content = stream.readNBytes(limits.maxBytes() + 1);
            if (content.length > limits.maxBytes()) {
                throw new StorageUnavailableException();
            }
            return content;
        } catch (ErrorResponseException exception) {
            if (exception.errorResponse().code().equals("NoSuchKey") || exception.errorResponse().code().equals("NoSuchBucket")) {
                throw new DocumentContentNotFoundException();
            }
            throw new StorageUnavailableException(exception);
        } catch (Exception exception) {
            throw new StorageUnavailableException(exception);
        }
    }

    @Override
    public void delete(String key) {
        requireDocumentKey(key);
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(properties.bucket()).object(key).build());
        } catch (ErrorResponseException exception) {
            if (!exception.errorResponse().code().equals("NoSuchBucket") && !exception.errorResponse().code().equals("NoSuchKey")) {
                throw new StorageUnavailableException(exception);
            }
        } catch (Exception exception) {
            throw new StorageUnavailableException(exception);
        }
    }

    @Override
    public boolean available() {
        try {
            client.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build());
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private void requireDocumentKey(String key) {
        if (key == null || !key.matches("documents/[A-Za-z0-9-]+\\.pdf")) {
            throw new IllegalArgumentException("Invalid document storage key");
        }
    }
}
