package newcms.utils;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import newcms.entity.db.SysOssFile;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ContentDisposition;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinIOUtilsTest {

    @Mock
    private MinioClient minioClient;

    private MinIOUtils minIOUtils;

    @BeforeEach
    void setUp() {
        minIOUtils = new MinIOUtils();
        ReflectionTestUtils.setField(minIOUtils, "minioClient", minioClient);
        ReflectionTestUtils.setField(minIOUtils, "accessKey", "minioAdmin");
        ReflectionTestUtils.setField(minIOUtils, "secretKey", "minioAdmin");
        ReflectionTestUtils.setField(minIOUtils, "kkFileViewEndpoint", "http://47.96.172.199:9000");
    }

    @Test
    void resolveMime_usesExtensionNotOctetStream() {
        assertEquals("application/pdf", MinIOUtils.resolveMime("实习报告.pdf", "pdf", "2026/9/8/abc.pdf"));
        assertEquals("image/png", MinIOUtils.resolveMime("a.png", null, null));
        assertEquals("image/jpeg", MinIOUtils.resolveMime(null, "jpg", null));
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                MinIOUtils.resolveMime("计划.docx", "docx", "2026/9/8/uuid.docx"));
        String pdf = MinIOUtils.resolveMime("a.pdf", "pdf", "a.pdf");
        assertFalse(pdf.contains("charset"));
        assertNotEquals("application/octet-stream", pdf);
        assertNotEquals("application/json", pdf);
        assertNotEquals("text/html", pdf);
        assertNotEquals("text/plain", pdf);
    }

    @Test
    void contentDisposition_previewInlineDownloadAttachment() {
        String preview = MinIOUtils.contentDisposition(true, "实习报告.pdf");
        assertTrue(preview.startsWith("inline;"));
        assertEquals("实习报告.pdf", ContentDisposition.parse(preview).getFilename());
        assertTrue(preview.contains("filename*=UTF-8''"));
        assertTrue(preview.chars().allMatch(c -> c <= 0x7f));
        assertFalse(preview.contains("attachment"));

        String download = MinIOUtils.contentDisposition(false, "实习报告.pdf");
        assertTrue(download.startsWith("attachment;"));
        assertEquals("实习报告.pdf", ContentDisposition.parse(download).getFilename());
        assertTrue(download.contains("filename*=UTF-8''"));
        assertTrue(download.chars().allMatch(c -> c <= 0x7f));
        assertFalse(download.contains("inline"));
    }

    @Test
    void applyBinaryHeaders_pdfHasNoCharset() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCharacterEncoding("UTF-8");
        MinIOUtils.applyBinaryHeaders(response, "application/pdf",
                "inline; filename=\"a.pdf\"", "12");
        String contentType = response.getContentType();
        assertEquals("application/pdf", contentType);
        assertFalse(contentType.toLowerCase().contains("charset"));
        assertTrue(response.getHeader("Content-Disposition").contains("inline"));
        assertEquals(12, response.getContentLength());
    }

    @Test
    void stream_previewPdfWritesBytesAndHeaders() throws Exception {
        byte[] pdf = "%PDF-1.4 test".getBytes(StandardCharsets.US_ASCII);
        GetObjectResponse objectResponse = new GetObjectResponse(
                Headers.of("Content-Type", "application/octet-stream"),
                "internship", "us-east-1", "2026/9/8/a.pdf",
                new ByteArrayInputStream(pdf));
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(objectResponse);

        SysOssFile file = new SysOssFile()
                .setBucketName("internship")
                .setOssPath("2026/9/8/a.pdf")
                .setFileName("实习报告.pdf")
                .setSuffix("pdf")
                .setFileSize(String.valueOf(pdf.length));
        MockHttpServletResponse response = new MockHttpServletResponse();
        minIOUtils.stream(file, true, response);

        assertEquals("application/pdf", response.getContentType());
        assertFalse(response.getContentType().toLowerCase().contains("charset"));
        assertTrue(response.getHeader("Content-Disposition").contains("inline"));
        assertFalse(response.getHeader("Content-Disposition").contains("attachment"));
        assertArrayEquals(pdf, response.getContentAsByteArray());
        assertTrue(new String(response.getContentAsByteArray(), StandardCharsets.US_ASCII).startsWith("%PDF-"));
    }

    @Test
    void stream_downloadUsesRealMimeAndAttachment() throws Exception {
        byte[] pdf = "%PDF-1.4".getBytes(StandardCharsets.US_ASCII);
        GetObjectResponse objectResponse = new GetObjectResponse(
                Headers.of(), "internship", "us-east-1", "a.pdf",
                new ByteArrayInputStream(pdf));
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(objectResponse);

        SysOssFile file = new SysOssFile()
                .setBucketName("internship")
                .setOssPath("a.pdf")
                .setFileName("实习报告.pdf")
                .setSuffix("pdf")
                .setFileSize("8");
        MockHttpServletResponse response = new MockHttpServletResponse();
        minIOUtils.stream(file, false, response);

        assertEquals("application/pdf", response.getContentType());
        assertNotEquals("application/octet-stream", response.getContentType());
        String disposition = response.getHeader("Content-Disposition");
        assertTrue(disposition.startsWith("attachment;"));
        assertEquals("实习报告.pdf", ContentDisposition.parse(disposition).getFilename());
        assertTrue(disposition.chars().allMatch(c -> c <= 0x7f));
    }

    @Test
    void presignedKkFileViewUrl_usesConfiguredDevelopmentEndpoint() {
        String url = minIOUtils.presignedKkFileViewUrl(
                "internship", "2026/9/8/plan.docx", 600);
        assertTrue(url.startsWith("http://47.96.172.199:9000/internship/2026/9/8/plan.docx?"), url);
        String lower = url.toLowerCase();
        assertFalse(lower.contains("response-content-type"));
        assertFalse(lower.contains("response-content-disposition"));
        assertFalse(lower.contains("content-disposition"));
        assertFalse(url.contains("localhost"));
        assertFalse(url.contains("127.0.0.1"));
        assertTrue(url.contains("X-Amz-Algorithm="));
    }

    @Test
    void presignedKkFileViewUrl_usesConfiguredDockerEndpoint() {
        ReflectionTestUtils.setField(minIOUtils, "kkFileViewEndpoint", "http://minio:9000");
        String url = minIOUtils.presignedKkFileViewUrl(
                "internship", "2026/9/8/plan.docx", 600);

        assertTrue(url.startsWith("http://minio:9000/internship/2026/9/8/plan.docx?"), url);
        assertFalse(url.contains("47.96.172.199"));
        assertTrue(url.contains("X-Amz-Algorithm="));
    }
}
