package at.fhtw.docflow.configuration;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({StorageProperties.class, UploadProperties.class})
public class StorageConfiguration {
    @Bean
    MinioClient minioClient(StorageProperties properties) {
        MinioClient client = MinioClient.builder().endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey()).build();
        client.setTimeout(properties.timeoutMillis(), properties.timeoutMillis(), properties.timeoutMillis());
        return client;
    }
}
