package com.snapitt.backend_service.modules.event.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * S3-compatible client configuration for CDC event archival.
 *
 * Configured to work with Supabase Storage's S3-compatible API.
 * Uses path-style access (required by most S3-compatible providers)
 * and a custom endpoint URI.
 */
@Slf4j
@Configuration
public class S3Config {

    @Value("${aws.s3.endpoint}")
    private String endpoint;

    @Value("${aws.s3.region:ap-south-1}")
    private String region;

    @Value("${aws.s3.access-key}")
    private String accessKey;

    @Value("${aws.s3.secret-key}")
    private String secretKey;

    @Bean
    public S3Client s3Client() {
        S3Client client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)
                ))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)   // required for Supabase / S3-compatible
                        .chunkedEncodingEnabled(false)   // some providers don't support chunked
                        .build())
                .build();

        log.info("Supabase S3 client initialized (endpoint: {}, region: {})", endpoint, region);
        return client;
    }
}
