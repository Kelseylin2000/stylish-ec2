package com.example.stylish.service;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
public class ImageSaveService {

    private final String bucketName;
    private final String region;
    private final String storageLocation;
    private final S3Client s3Client;

    public ImageSaveService(
            @Value("${aws.s3.bucketName}") String bucketName,
            @Value("${aws.s3.region}") String region,
            @Value("${storage.location}") String storageLocation) {
        this.bucketName = bucketName;
        this.region = region;
        this.storageLocation = storageLocation;
        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .build();
    }

    public String saveImage(MultipartFile file, String type) {
        if (file.isEmpty()) {
            return null;
        }

        try {
            String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
            String newFilename = UUID.randomUUID().toString() + "-" + type + "-" + originalFilename;

            String s3Key = storageLocation + "/" + newFilename;

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            PutObjectResponse response = s3Client.putObject(putObjectRequest, software.amazon.awssdk.core.sync.RequestBody.fromBytes(file.getBytes()));

            if (response.sdkHttpResponse().isSuccessful()) {
                return s3Key;
            } else {
                return null;
            }

        } catch (S3Exception | IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String[] saveImages(MultipartFile[] files, String type) {
        return Arrays.stream(files)
            .map(file -> saveImage(file, type))
            .toArray(String[]::new);
    }
}