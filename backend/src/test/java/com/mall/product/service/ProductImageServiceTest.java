package com.mall.product.service;

import com.mall.common.exception.BusinessException;
import com.mall.common.result.ResultCode;
import com.mall.config.storage.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductImageServiceTest {

    private static final byte[] PNG_HEADER = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] GIF_HEADER = "GIF89a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP_HEADER = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

    @Mock
    private S3Client s3Client;

    private ProductImageService imageService;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        properties.setBucket("mall-product");
        properties.setPublicBaseUrl("http://localhost:9000/mall-product");
        imageService = new ProductImageService(s3Client, properties);
    }

    @Test
    @DisplayName("按文件头识别扩展名，对象键按日期分目录并以 UUID 命名")
    void uploadsWithDetectedExtension() {
        String url = imageService.upload(file("photo.bin", "image/png", PNG_HEADER));

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        PutObjectRequest request = captor.getValue();
        assertThat(request.key()).matches("\\d{4}/\\d{2}/\\d{2}/[0-9a-f-]{36}\\.png");
        assertThat(request.bucket()).isEqualTo("mall-product");
        assertThat(request.contentType()).isEqualTo("image/png");
        assertThat(url).isEqualTo("http://localhost:9000/mall-product/" + request.key());
    }

    @Test
    @DisplayName("支持 jpg、gif、webp 三类文件头")
    void detectsSupportedFormats() {
        assertThat(uploadKey(file("a.jpg", "image/jpeg", JPG_HEADER))).endsWith(".jpg");
        assertThat(uploadKey(file("a.gif", "image/gif", GIF_HEADER))).endsWith(".gif");
        assertThat(uploadKey(file("a.webp", "image/webp", WEBP_HEADER))).endsWith(".webp");
    }

    @Test
    @DisplayName("不信任客户端声明的 Content-Type，非图片文件头被拒绝")
    void rejectsNonImageContent() {
        MultipartFile text = file("fake.png", "image/png", "not an image".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> imageService.upload(text))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.IMAGE_UPLOAD_FAILED.getCode());
    }

    @Test
    @DisplayName("空文件被拒绝")
    void rejectsEmptyFile() {
        MultipartFile empty = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> imageService.upload(empty))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上传文件为空");
    }

    @Test
    @DisplayName("超过 5MB 的图片被拒绝")
    void rejectsOversizedFile() {
        MultipartFile huge = new MockMultipartFile("file", "huge.png", "image/png",
                new byte[5 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> imageService.upload(huge))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("5MB");
    }

    @Test
    @DisplayName("对象存储失败时转换为图片上传失败错误")
    void mapsStorageFailure() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("connection refused"));

        assertThatThrownBy(() -> imageService.upload(file("a.png", "image/png", PNG_HEADER)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.IMAGE_UPLOAD_FAILED.getCode());
    }

    private String uploadKey(MultipartFile file) {
        String url = imageService.upload(file);
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private MultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }
}
