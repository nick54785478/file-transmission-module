package com.example.demo.presentation.controller;

import com.example.demo.application.port.in.DownloadFileUseCase;
import com.example.demo.application.port.in.GetTransmissionStatusUseCase;
import com.example.demo.application.port.in.SubmitDownloadFileUseCase;
import com.example.demo.application.port.in.SubmitSyncDirectoryUseCase;
import com.example.demo.application.port.in.SubmitUploadFileUseCase;
import com.example.demo.application.port.in.SyncDirectoryUseCase;
import com.example.demo.application.port.in.UploadFileFromMemoryUseCase;
import com.example.demo.application.port.in.UploadFileUseCase;
import com.example.demo.application.shared.command.DownloadFileCommand;
import com.example.demo.application.shared.command.SyncDirectoryCommand;
import com.example.demo.application.shared.command.UploadFileCommand;
import com.example.demo.application.shared.command.UploadFileFromMemoryCommand;
import com.example.demo.application.shared.dto.DirectorySyncGottenResult;
import com.example.demo.application.shared.dto.FileTransmissionGottenResult;
import com.example.demo.application.shared.dto.FileTransmissionSubmittedResult;
import com.example.demo.application.shared.exception.FileTransmissionException;
import com.example.demo.application.shared.query.GetTransmissionStatusQuery;
import com.example.demo.application.port.out.ServerCredentialStoragePort;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import com.example.demo.presentation.handler.GlobalExceptionHandler;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 檔案傳輸表現層控制器測試 (FileTransmissionControllerTest)
 * <p>
 * 遵循測試規範 (AGENTS.md 第 5 節)：
 * 1. 驗證表現層同步 API、非同步長任務提交 API (202 Accepted) 與狀態查詢 API (200 / 404)；
 * 2. 測試傳輸 API 全面依賴後端憑證管理 (credentialId) 之純化架構；
 * 3. 測試方法均撰寫 Gherkin 語法 Javadoc (Feature, Scenario, Given-When-Then)。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class FileTransmissionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UploadFileUseCase uploadFileUseCase;

    @Mock
    private DownloadFileUseCase downloadFileUseCase;

    @Mock
    private SyncDirectoryUseCase syncDirectoryUseCase;

    @Mock
    private SubmitUploadFileUseCase submitUploadFileUseCase;

    @Mock
    private SubmitDownloadFileUseCase submitDownloadFileUseCase;

    @Mock
    private SubmitSyncDirectoryUseCase submitSyncDirectoryUseCase;

    @Mock
    private GetTransmissionStatusUseCase getTransmissionStatusUseCase;

    @Mock
    private UploadFileFromMemoryUseCase uploadFileFromMemoryUseCase;

    @Mock
    private ServerCredentialStoragePort serverCredentialStoragePort;

    private static final TransmissionServerConfig MOCK_SFTP_CONFIG = new TransmissionServerConfig(
            "127.0.0.1", 2222, TransmissionProtocol.SFTP, "tester", "password", 10000, true, false, null, null
    );

    private static final TransmissionServerConfig MOCK_FTP_CONFIG = new TransmissionServerConfig(
            "127.0.0.1", 2121, TransmissionProtocol.FTP, "tester", "password", 10000, true, false, null, null
    );

    private static final TransmissionServerConfig MOCK_FTPS_CONFIG = new TransmissionServerConfig(
            "127.0.0.1", 990, TransmissionProtocol.FTPS, "tester", "password", 10000, true, false, null, null
    );

    @BeforeEach
    void setUp() {
        FileTransmissionController controller = new FileTransmissionController(
                uploadFileUseCase,
                downloadFileUseCase,
                syncDirectoryUseCase,
                submitUploadFileUseCase,
                submitDownloadFileUseCase,
                submitSyncDirectoryUseCase,
                getTransmissionStatusUseCase,
                uploadFileFromMemoryUseCase,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                serverCredentialStoragePort
        );
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /**
     * Feature: 檔案傳輸 REST API 端點
     * Scenario: 接收 POST /api/transmission/upload 同步請求並成功回傳 200 與 FileUploadedResource
     * Given: 傳入標準 JSON 格式之 UploadFileResource 請求體 (含 credentialId: "mock-sftp")
     * When: 發送 POST 請求至 /api/transmission/upload
     * Then: 控制器依 credentialId 解析連線配置，呼叫 UploadFileUseCase 並回傳 200 OK
     */
    @Test
    @DisplayName("情境：POST /api/transmission/upload 成功回應 200 與上傳結果")
    void testUploadEndpoint() throws Exception {
        when(serverCredentialStoragePort.resolveTransmissionConfig("mock-sftp"))
                .thenReturn(Optional.of(MOCK_SFTP_CONFIG));

        FileTransmissionGottenResult mockResult = new FileTransmissionGottenResult(
                "tx-123",
                "SFTP",
                "COMPLETED",
                "/local/file.txt",
                "/remote/path",
                "file.txt",
                2048L,
                150L,
                null
        );

        when(uploadFileUseCase.uploadFile(any(UploadFileCommand.class))).thenReturn(mockResult);

        String jsonPayload = """
                {
                    "credentialId": "mock-sftp",
                    "localFilePath": "src/test/resources/test.txt",
                    "remotePath": "/remote/path",
                    "filename": "file.txt"
                }
                """;

        mockMvc.perform(post("/api/transmission/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transmissionId").value("tx-123"))
                .andExpect(jsonPath("$.protocol").value("SFTP"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.fileName").value("file.txt"))
                .andExpect(jsonPath("$.fileSizeBytes").value(2048));
    }

    /**
     * Feature: 檔案傳輸 REST API 端點
     * Scenario: 接收 POST /api/transmission/download 同步下載請求並成功回傳 200 與 FileDownloadedResource
     * Given: 傳入標準 JSON 格式之 DownloadFileResource 請求體 (含 credentialId: "mock-sftp")
     * When: 發送 POST 請求至 /api/transmission/download
     * Then: 控制器呼叫 DownloadFileUseCase 並回傳 200 OK
     */
    @Test
    @DisplayName("情境：POST /api/transmission/download 成功回應 200 與下載結果")
    void testDownloadEndpointSuccess() throws Exception {
        when(serverCredentialStoragePort.resolveTransmissionConfig("mock-sftp"))
                .thenReturn(Optional.of(MOCK_SFTP_CONFIG));

        FileTransmissionGottenResult mockResult = new FileTransmissionGottenResult(
                "tx-dl-123",
                "SFTP",
                "COMPLETED",
                "/remote/path/remote.txt",
                "/local/downloads",
                "remote.txt",
                1024L,
                90L,
                null
        );

        when(downloadFileUseCase.downloadFile(any(DownloadFileCommand.class))).thenReturn(mockResult);

        String jsonPayload = """
                {
                    "credentialId": "mock-sftp",
                    "remoteFilePath": "/remote/path/remote.txt",
                    "savedLocalPath": "/local/downloads/remote.txt"
                }
                """;

        mockMvc.perform(post("/api/transmission/download")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transmissionId").value("tx-dl-123"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    /**
     * Feature: 檔案傳輸 REST API 端點
     * Scenario: 接收 POST /api/transmission/sync 同步目錄請求並成功回傳 200 與 DirectorySynchronizedResource
     * Given: 傳入標準 JSON 格式之 SyncDirectoryResource 請求體 (含 credentialId: "mock-ftps")
     * When: 發送 POST 請求至 /api/transmission/sync
     * Then: 控制器呼叫 SyncDirectoryUseCase 並回傳 200 OK
     */
    @Test
    @DisplayName("情境：POST /api/transmission/sync 成功回應 200 與目錄同步結果")
    void testSyncEndpointSuccess() throws Exception {
        when(serverCredentialStoragePort.resolveTransmissionConfig("mock-ftps"))
                .thenReturn(Optional.of(MOCK_FTPS_CONFIG));

        DirectorySyncGottenResult mockResult = new DirectorySyncGottenResult(
                "sync-tx-123",
                "FTPS",
                "COMPLETED",
                "LOCAL_TO_REMOTE",
                "/local/dir",
                "/remote/dir",
                5,
                0,
                300L,
                null
        );

        when(syncDirectoryUseCase.syncDirectory(any(SyncDirectoryCommand.class))).thenReturn(mockResult);

        String jsonPayload = """
                {
                    "credentialId": "mock-ftps",
                    "localDir": "/local/dir",
                    "remoteDir": "/remote/dir",
                    "direction": "LOCAL_TO_REMOTE",
                    "deleteRemoteExtras": false
                }
                """;

        mockMvc.perform(post("/api/transmission/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transmissionId").value("sync-tx-123"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.filesTransferredCount").value(5));
    }

    /**
     * Feature: 檔案傳輸 REST API 端點
     * Scenario: 接收 POST /api/transmission/upload/file multipart 請求並成功回傳 200 與 FileUploadedResource
     * Given: 傳入 MockMultipartFile 與 表單參數
     * When: 發送 multipart POST 請求至 /api/transmission/upload/file
     * Then: 控制器呼叫 UploadFileFromMemoryUseCase 並回傳 200 OK
     */
    @Test
    @DisplayName("情境：POST /api/transmission/upload/file Multipart 上傳成功回應 200")
    void testUploadMultipartFileEndpointSuccess() throws Exception {
        when(serverCredentialStoragePort.resolveTransmissionConfig("mock-sftp"))
                .thenReturn(Optional.of(MOCK_SFTP_CONFIG));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "direct-upload.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Direct Memory Upload Content".getBytes()
        );

        FileTransmissionGottenResult mockResult = new FileTransmissionGottenResult(
                "tx-mem-777",
                "SFTP",
                "COMPLETED",
                "<memory>",
                "/remote/upload",
                "direct-upload.txt",
                28L,
                80L,
                null
        );

        when(uploadFileFromMemoryUseCase.uploadFileFromMemory(any(UploadFileFromMemoryCommand.class)))
                .thenReturn(mockResult);

        mockMvc.perform(multipart("/api/transmission/upload/file")
                        .file(file)
                        .param("credentialId", "mock-sftp")
                        .param("remotePath", "/remote/upload")
                        .param("filename", "direct-upload.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transmissionId").value("tx-mem-777"))
                .andExpect(jsonPath("$.protocol").value("SFTP"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.sourcePath").value("<memory>"))
                .andExpect(jsonPath("$.fileName").value("direct-upload.txt"))
                .andExpect(jsonPath("$.fileSizeBytes").value(28));
    }

    /**
     * Feature: 檔案傳輸 REST API 端點
     * Scenario: 接收帶有 resource JSON 部件之 Multipart 上傳請求 (Swagger UI 模式) 並成功回傳 200
     * Given: 傳入 MockMultipartFile 與帶有 application/json 之 resource MockMultipartFile
     * When: 發送 multipart POST 請求至 /api/transmission/upload/file
     * Then: 控制器成功反序列化 resource JSON 部件，呼叫 UploadFileFromMemoryUseCase 並回傳 200 OK
     */
    @Test
    @DisplayName("情境：POST /api/transmission/upload/file 接收 Swagger UI JSON resource 部件上傳成功回應 200")
    void testUploadMultipartFileEndpointWithJsonResourcePartSuccess() throws Exception {
        when(serverCredentialStoragePort.resolveTransmissionConfig("mock-sftp"))
                .thenReturn(Optional.of(MOCK_SFTP_CONFIG));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "direct-upload.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Direct Memory Upload Content".getBytes()
        );

        String resourceJson = """
                {
                    "credentialId": "mock-sftp",
                    "remotePath": "/upload",
                    "filename": "swagger-direct.txt"
                }
                """;

        MockMultipartFile resourcePart = new MockMultipartFile(
                "resource",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                resourceJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        FileTransmissionGottenResult mockResult = new FileTransmissionGottenResult(
                "tx-swagger-888",
                "SFTP",
                "COMPLETED",
                "<memory>",
                "/upload",
                "swagger-direct.txt",
                28L,
                80L,
                null
        );

        when(uploadFileFromMemoryUseCase.uploadFileFromMemory(any(UploadFileFromMemoryCommand.class)))
                .thenReturn(mockResult);

        mockMvc.perform(multipart("/api/transmission/upload/file")
                        .file(file)
                        .file(resourcePart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transmissionId").value("tx-swagger-888"))
                .andExpect(jsonPath("$.protocol").value("SFTP"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.sourcePath").value("<memory>"))
                .andExpect(jsonPath("$.fileName").value("swagger-direct.txt"))
                .andExpect(jsonPath("$.fileSizeBytes").value(28));
    }

    /**
     * Feature: 檔案傳輸 REST API 端點
     * Scenario: 接收 Swagger UI 預設 application/octet-stream Content-Type 之 resource 部件並成功回傳 200
     * Given: 傳入 MockMultipartFile 與帶有 application/octet-stream 之 resource 部件
     * When: 發送 multipart POST 請求至 /api/transmission/upload/file
     * Then: 控制器從 Multipart 串流智慧解析 JSON，呼叫 UploadFileFromMemoryUseCase 並回傳 200 OK
     */
    @Test
    @DisplayName("情境：POST /api/transmission/upload/file 接收 application/octet-stream resource 部件成功回應 200")
    void testUploadMultipartFileEndpointWithOctetStreamResourcePartSuccess() throws Exception {
        when(serverCredentialStoragePort.resolveTransmissionConfig("mock-sftp"))
                .thenReturn(Optional.of(MOCK_SFTP_CONFIG));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "direct-upload.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Direct Memory Upload Content".getBytes()
        );

        String resourceJson = """
                {
                    "credentialId": "mock-sftp",
                    "remotePath": "/upload",
                    "filename": "octet-direct.txt"
                }
                """;

        MockMultipartFile octetStreamPart = new MockMultipartFile(
                "resource",
                "blob",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                resourceJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        FileTransmissionGottenResult mockResult = new FileTransmissionGottenResult(
                "tx-octet-999",
                "SFTP",
                "COMPLETED",
                "<memory>",
                "/upload",
                "octet-direct.txt",
                28L,
                85L,
                null
        );

        when(uploadFileFromMemoryUseCase.uploadFileFromMemory(any(UploadFileFromMemoryCommand.class)))
                .thenReturn(mockResult);

        mockMvc.perform(multipart("/api/transmission/upload/file")
                        .file(file)
                        .file(octetStreamPart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transmissionId").value("tx-octet-999"))
                .andExpect(jsonPath("$.protocol").value("SFTP"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.fileName").value("octet-direct.txt"));
    }

    /**
     * Feature: 檔案傳輸 REST API 端點
     * Scenario: 接收空的 Multipart 檔案時應回傳 400 Bad Request
     * Given: 傳入空內容的 MockMultipartFile
     * When: 發送 multipart POST 請求至 /api/transmission/upload/file
     * Then: 控制器拋出 IllegalArgumentException 並由 GlobalExceptionHandler 回應 400
     */
    @Test
    @DisplayName("情境：POST /api/transmission/upload/file 上傳空檔案時回應 400 Bad Request")
    void testUploadMultipartFileEndpointEmptyFileBadRequest() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.txt",
                MediaType.TEXT_PLAIN_VALUE,
                new byte[0]
        );

        mockMvc.perform(multipart("/api/transmission/upload/file")
                        .file(emptyFile)
                        .param("credentialId", "mock-sftp")
                        .param("remotePath", "/remote/upload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.exception").value("IllegalArgumentException"));
    }

    /**
     * Feature: 非同步檔案傳輸長任務 API (Mode 1)
     * Scenario: 接收 POST /api/transmission/upload/async 請求並成功回傳 202 Accepted 與 trackingId
     * Given: 傳入標準上傳請求 JSON (含 credentialId)
     * When: 發送 POST 請求至 /api/transmission/upload/async
     * Then: 控制器呼叫 SubmitUploadFileUseCase 並立即回傳 202 Accepted 與 IN_PROGRESS 狀態
     */
    @Test
    @DisplayName("情境：POST /api/transmission/upload/async 成功回應 202 Accepted")
    void testSubmitUploadEndpoint() throws Exception {
        when(serverCredentialStoragePort.resolveTransmissionConfig("mock-sftp"))
                .thenReturn(Optional.of(MOCK_SFTP_CONFIG));

        FileTransmissionSubmittedResult mockResult = new FileTransmissionSubmittedResult(
                "async-tx-001",
                "SFTP",
                "IN_PROGRESS",
                "檔案上傳任務已提交至背景執行緒執行",
                Instant.now()
        );

        when(submitUploadFileUseCase.submitUploadFile(any(UploadFileCommand.class))).thenReturn(mockResult);

        String jsonPayload = """
                {
                    "credentialId": "mock-sftp",
                    "localFilePath": "src/test/resources/test.txt",
                    "remotePath": "/remote/path",
                    "filename": "file.txt"
                }
                """;

        mockMvc.perform(post("/api/transmission/upload/async")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.trackingId").value("async-tx-001"))
                .andExpect(jsonPath("$.protocol").value("SFTP"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.message").value("檔案上傳任務已提交至背景執行緒執行"));
    }

    /**
     * Feature: 非同步檔案傳輸長任務 API (Mode 1)
     * Scenario: 接收 POST /api/transmission/download/async 請求並成功回傳 202 Accepted
     * Given: 傳入標準下載請求 JSON (含 credentialId)
     * When: 發送 POST 請求至 /api/transmission/download/async
     * Then: 控制器呼叫 SubmitDownloadFileUseCase 並回傳 202 Accepted
     */
    @Test
    @DisplayName("情境：POST /api/transmission/download/async 成功回應 202 Accepted")
    void testSubmitDownloadEndpoint() throws Exception {
        when(serverCredentialStoragePort.resolveTransmissionConfig("mock-ftp"))
                .thenReturn(Optional.of(MOCK_FTP_CONFIG));

        FileTransmissionSubmittedResult mockResult = new FileTransmissionSubmittedResult(
                "async-tx-002",
                "FTP",
                "IN_PROGRESS",
                "檔案下載任務已提交至背景執行緒執行",
                Instant.now()
        );

        when(submitDownloadFileUseCase.submitDownloadFile(any(DownloadFileCommand.class))).thenReturn(mockResult);

        String jsonPayload = """
                {
                    "credentialId": "mock-ftp",
                    "remoteFilePath": "/remote/data/doc.pdf",
                    "savedLocalPath": "/local/data/doc.pdf"
                }
                """;

        mockMvc.perform(post("/api/transmission/download/async")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.trackingId").value("async-tx-002"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    /**
     * Feature: 非同步檔案傳輸長任務 API (Mode 1)
     * Scenario: 接收 POST /api/transmission/sync/async 請求並成功回傳 202 Accepted
     * Given: 傳入標準同步請求 JSON (含 credentialId)
     * When: 發送 POST 請求至 /api/transmission/sync/async
     * Then: 控制器呼叫 SubmitSyncDirectoryUseCase 並回傳 202 Accepted
     */
    @Test
    @DisplayName("情境：POST /api/transmission/sync/async 成功回應 202 Accepted")
    void testSubmitSyncEndpoint() throws Exception {
        when(serverCredentialStoragePort.resolveTransmissionConfig("mock-ftps"))
                .thenReturn(Optional.of(MOCK_FTPS_CONFIG));

        FileTransmissionSubmittedResult mockResult = new FileTransmissionSubmittedResult(
                "async-tx-003",
                "FTPS",
                "IN_PROGRESS",
                "目錄同步任務已提交至背景執行緒執行",
                Instant.now()
        );

        when(submitSyncDirectoryUseCase.submitSyncDirectory(any(SyncDirectoryCommand.class))).thenReturn(mockResult);

        String jsonPayload = """
                {
                    "credentialId": "mock-ftps",
                    "localDir": "/local/dir",
                    "remoteDir": "/remote/dir",
                    "direction": "LOCAL_TO_REMOTE",
                    "deleteRemoteExtras": true
                }
                """;

        mockMvc.perform(post("/api/transmission/sync/async")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.trackingId").value("async-tx-003"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    /**
     * Feature: 傳輸狀態查詢 API (Mode 1)
     * Scenario: 接收 GET /api/transmission/status/{trackingId} 查詢存在之任務狀態
     * Given: 存在的 trackingId
     * When: 發送 GET 請求
     * Then: 回傳 200 OK 與完整 TransmissionStatusGottenResource
     */
    @Test
    @DisplayName("情境：GET /api/transmission/status/{trackingId} 成功查詢狀態回應 200")
    void testGetStatusEndpointFound() throws Exception {
        String trackingId = "track-999";
        FileTransmissionGottenResult mockResult = new FileTransmissionGottenResult(
                trackingId,
                "SFTP",
                "COMPLETED",
                "/local/data.txt",
                "/remote/data.txt",
                "data.txt",
                4096L,
                250L,
                null
        );

        when(getTransmissionStatusUseCase.getTransmissionStatus(any(GetTransmissionStatusQuery.class)))
                .thenReturn(Optional.of(mockResult));

        mockMvc.perform(get("/api/transmission/status/{trackingId}", trackingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transmissionId").value(trackingId))
                .andExpect(jsonPath("$.protocol").value("SFTP"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.fileName").value("data.txt"))
                .andExpect(jsonPath("$.fileSizeBytes").value(4096))
                .andExpect(jsonPath("$.durationMillis").value(250));
    }

    /**
     * Feature: 傳輸狀態查詢 API (Mode 1)
     * Scenario: 接收 GET /api/transmission/status/{trackingId} 查詢不存在之任務狀態
     * Given: 不存在的 trackingId
     * When: 發送 GET 請求
     * Then: 回傳 404 Not Found
     */
    @Test
    @DisplayName("情境：GET /api/transmission/status/{trackingId} 查無紀錄回應 404")
    void testGetStatusEndpointNotFound() throws Exception {
        when(getTransmissionStatusUseCase.getTransmissionStatus(any(GetTransmissionStatusQuery.class)))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/transmission/status/{trackingId}", "non-existing-id"))
                .andExpect(status().isNotFound());
    }

    /**
     * Feature: 檔案傳輸表現層例外處理
     * Scenario: 接收 POST /api/transmission/upload 傳入不存在之 credentialId
     * Given: 請求體中指定 credentialId 為 "invalid-cred" 且資料庫無此憑證
     * When: 發送 POST 請求至 /api/transmission/upload
     * Then: Assembler 拋出 IllegalArgumentException，由 GlobalExceptionHandler 攔截並回傳 400 Bad Request
     */
    @Test
    @DisplayName("情境：POST /api/transmission/upload 憑證無效回應 400 Bad Request")
    void testUploadEndpointInvalidCredentialId() throws Exception {
        when(serverCredentialStoragePort.resolveTransmissionConfig("invalid-cred"))
                .thenReturn(Optional.empty());

        String invalidRequestJson = """
                {
                    "credentialId": "invalid-cred",
                    "localFilePath": "test.txt",
                    "remotePath": "/remote",
                    "filename": "test.txt"
                }
                """;

        mockMvc.perform(post("/api/transmission/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.exception").value("IllegalArgumentException"))
                .andExpect(jsonPath("$.path").value("/api/transmission/upload"));
    }

    /**
     * Feature: 檔案傳輸表現層例外處理
     * Scenario: 接收 POST /api/transmission/upload 執行上傳發生 FileTransmissionException
     * Given: UploadFileUseCase 拋出 FileTransmissionException
     * When: 發送 POST 請求至 /api/transmission/upload
     * Then: GlobalExceptionHandler 攔截並回傳 500 Internal Server Error
     */
    @Test
    @DisplayName("情境：POST /api/transmission/upload 傳輸失敗回應 500 Internal Server Error")
    void testUploadEndpointTransmissionFailure() throws Exception {
        when(serverCredentialStoragePort.resolveTransmissionConfig("mock-sftp"))
                .thenReturn(Optional.of(MOCK_SFTP_CONFIG));

        when(uploadFileUseCase.uploadFile(any(UploadFileCommand.class)))
                .thenThrow(new FileTransmissionException("本機上傳來源檔案不存在或非一般檔案"));

        String requestJson = """
                {
                    "credentialId": "mock-sftp",
                    "localFilePath": "non-exist.txt",
                    "remotePath": "/remote",
                    "filename": "non-exist.txt"
                }
                """;

        mockMvc.perform(post("/api/transmission/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.exception").value("FileTransmissionException"))
                .andExpect(jsonPath("$.path").value("/api/transmission/upload"));
    }

    /**
     * Feature: 檔案傳輸 REST API 端點
     * Scenario: 使用者提供 credentialId 透過已維護之憑證設定檔執行檔案上傳
     * Given: 傳入包含 credentialId: "mock-sftp" 之 UploadFileResource 且儲存埠成功解析
     * When: 發送 POST 請求至 /api/transmission/upload
     * Then: 控制器自動裝配憑證配置，呼叫 UploadFileUseCase 並回傳 200 OK
     */
    @Test
    @DisplayName("情境：POST /api/transmission/upload 透過 credentialId 自動裝配連線配置上傳成功")
    void testUploadWithCredentialIdSuccess() throws Exception {
        when(serverCredentialStoragePort.resolveTransmissionConfig("mock-sftp"))
                .thenReturn(Optional.of(MOCK_SFTP_CONFIG));

        FileTransmissionGottenResult mockResult = new FileTransmissionGottenResult(
                "tx-cred-123",
                "SFTP",
                "COMPLETED",
                "test-data/local/upload-demo.txt",
                "/upload",
                "uploaded.txt",
                100L,
                45L,
                null
        );

        when(uploadFileUseCase.uploadFile(any(UploadFileCommand.class))).thenReturn(mockResult);

        String requestJson = """
                {
                    "credentialId": "mock-sftp",
                    "localFilePath": "test-data/local/upload-demo.txt",
                    "remotePath": "/upload",
                    "filename": "uploaded.txt"
                }
                """;

        mockMvc.perform(post("/api/transmission/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transmissionId").value("tx-cred-123"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    /**
     * Feature: Spring Validation 參數基本檢核
     * Scenario: 呼叫 POST /api/transmission/upload 缺少 credentialId 時，觸發 Bean Validation 回傳 400 Bad Request
     * Given: 傳入 credentialId 為空字串之 UploadFileResource 請求體
     * When: 發送 POST 請求至 /api/transmission/upload
     * Then: 回傳 400 Bad Request 與欄位檢核錯誤提示
     */
    @Test
    @DisplayName("情境：上傳請求 credentialId 為空時驗證失敗回傳 400 Bad Request")
    void testUploadWithBlankCredentialIdFailsValidation() throws Exception {
        String invalidJson = """
                {
                    "credentialId": "",
                    "localFilePath": "test-data/local/upload-demo.txt",
                    "remotePath": "/upload"
                }
                """;

        mockMvc.perform(post("/api/transmission/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("credentialId")));
    }

    /**
     * Feature: Spring Validation 參數基本檢核
     * Scenario: 呼叫 POST /api/transmission/download 缺少 remoteFilePath 時回傳 400 Bad Request
     * Given: 傳入 remoteFilePath 為空字串之 DownloadFileResource 請求體
     * When: 發送 POST 請求至 /api/transmission/download
     * Then: 回傳 400 Bad Request 與欄位檢核錯誤提示
     */
    @Test
    @DisplayName("情境：下載請求 remoteFilePath 為空時驗證失敗回傳 400 Bad Request")
    void testDownloadWithBlankRemotePathFailsValidation() throws Exception {
        String invalidJson = """
                {
                    "credentialId": "mock-sftp",
                    "remoteFilePath": "",
                    "savedLocalPath": "D:/downloads/file.txt"
                }
                """;

        mockMvc.perform(post("/api/transmission/download")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("remoteFilePath")));
    }

    /**
     * Feature: Spring Validation 參數基本檢核
     * Scenario: 呼叫 POST /api/transmission/sync 傳入非法方向值時，觸發 @Pattern 驗證失敗回傳 400 Bad Request
     * Given: 傳入 direction 為 "INVALID_DIRECTION" 之 SyncDirectoryResource 請求體
     * When: 發送 POST 請求至 /api/transmission/sync
     * Then: 回傳 400 Bad Request 與方向檢核錯誤提示
     */
    @Test
    @DisplayName("情境：同步請求傳入非法 direction 時驗證失敗回傳 400 Bad Request")
    void testSyncWithInvalidDirectionFailsValidation() throws Exception {
        String invalidJson = """
                {
                    "credentialId": "mock-sftp",
                    "localDir": "D:/local/dir",
                    "remoteDir": "/upload/dir",
                    "direction": "INVALID_DIRECTION"
                }
                """;

        mockMvc.perform(post("/api/transmission/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("direction")));
    }
}
