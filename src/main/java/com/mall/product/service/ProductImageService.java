package com.mall.product.service;

import com.mall.common.exception.BusinessException;
import com.mall.common.result.ResultCode;
import com.mall.config.storage.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 商品图片上传
 */
@Service
public class ProductImageService {

    private static final Logger log = LoggerFactory.getLogger(ProductImageService.class);

    /** 单张图片大小上限 5MB */
    private static final long MAX_SIZE = 5 * 1024 * 1024;
    private static final DateTimeFormatter DATE_PREFIX = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final S3Client s3Client;
    private final StorageProperties properties;

    public ProductImageService(S3Client s3Client, StorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    /**
     * 上传图片并返回可公开访问的 URL
     */
    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.IMAGE_UPLOAD_FAILED.getCode(), "上传文件为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(ResultCode.IMAGE_UPLOAD_FAILED.getCode(), "图片大小不能超过 5MB");
        }

        String extension = detectImageExtension(file);
        String objectKey = LocalDate.now().format(DATE_PREFIX) + "/" + UUID.randomUUID() + "." + extension;

        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(properties.getBucket())
                            .key(objectKey)
                            .contentType(file.getContentType())
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException | RuntimeException e) {
            log.error("图片上传失败，objectKey={}", objectKey, e);
            throw new BusinessException(ResultCode.IMAGE_UPLOAD_FAILED);
        }
        return properties.getPublicBaseUrl() + "/" + objectKey;
    }

    /**
     * 按文件头识别图片类型，避免只信任客户端声明的 Content-Type
     */
    private String detectImageExtension(MultipartFile file) {
        byte[] header = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.read(header);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.IMAGE_UPLOAD_FAILED.getCode(), "读取上传文件失败");
        }

        if (read >= 8 && startsWith(header, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) {
            return "png";
        }
        if (read >= 3 && startsWith(header, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return "jpg";
        }
        if (read >= 6 && startsWith(header, new byte[]{'G', 'I', 'F', '8', '7', 'a'})) {
            return "gif";
        }
        if (read >= 6 && startsWith(header, new byte[]{'G', 'I', 'F', '8', '9', 'a'})) {
            return "gif";
        }
        if (read >= 12 && startsWith(header, new byte[]{'R', 'I', 'F', 'F'})
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "webp";
        }
        throw new BusinessException(ResultCode.IMAGE_UPLOAD_FAILED.getCode(), "只支持 png、jpg、gif、webp 图片");
    }

    private boolean startsWith(byte[] source, byte[] prefix) {
        if (source.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (source[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
