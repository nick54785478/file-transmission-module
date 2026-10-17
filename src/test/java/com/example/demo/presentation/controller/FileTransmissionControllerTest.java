package com.example.demo.presentation.controller;

import com.example.demo.application.port.in.DownloadFileUseCase;
import com.example.demo.application.port.in.GetTransmissionStatusUseCase;
import com.example.demo.application.port.in.SubmitDownloadFileUseCase;
import com.example.demo.application.port.in.SubmitSyncDirectoryUseCase;
import com.example.demo.application.port.in.SubmitUploadFileUseCase;
import com.example.demo.application.port.in.SyncDirectoryUseCase;
import com.example.demo.application.port.in.UploadFileUseCase;
import com.example.demo.application.shared.command.DownloadFileCommand;
import com.example.demo.application.shared.command.SyncDirectoryCommand;
import com.example.demo.application.shared.command.UploadFileCommand;
import com.example.demo.application.shared.dto.DirectorySyncGottenResult;
import com.example.demo.application.shared.dto.FileTransmissionGottenResult;
import com.example.demo.application.shared.dto.FileTransmissionSubmittedResult;
import com.example.demo.application.shared.exception.FileTransmissionException;
import com.example.demo.application.shared.query.GetTransmissionStatusQuery;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 檔案傳輸表現層控制器測試 (FileTransmissionControllerTest)
 * <p>
 * 遵循測試規範 (AGENTS.md 第 5 節)：
 * 1. 驗證表現層同步 API、非同步長任務提交 API (202 Accepted) 與狀態查詢 API (200 / 404)；
 * 2. 測試方法均撰寫 Gherkin 語法 Javadoc (Feature, Scenario, Given-When-Then)。
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

    @BeforeEach
    void setUp() {
        FileTransmissionController controller = new FileTransmissionController(
                uploadFileUseCase,
                downloadFileUseCase,
                syncDirectoryUseCase,
                submitUploadFileUseCase,
                submitDownloadFileUseCase,
                submitSyncDirectoryUseCase,
                getTransmissionStatusUseCase
        );
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /**
     * Feature: 檔案傳輸 REST API 端點
     * Scenario: 接收 POST /api/transmission/upload 同步請求並成功回傳 200 與 FileUploadedResource
     * Given: 傳入標準 JSON 格式之 UploadFileResource 請求體
     * When: 發送 POST 請求至 /api/transmission/upload
     * Then: 控制器呼叫 UploadFileUseCase 並回傳 200 OK
     */
    @Test
    @DisplayName("情境：POST /api/transmission/upload 成功回應 200 與上傳結果")
    void testUploadEndpoint() throws Exception {
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
                    "host": "127.0.0.1",
                    "port": 22,
                    "protocol": "SFTP",
                    "username": "sftpuser",
                    "password": "secretPassword",
                    "localFilePath": "src/test/resources",
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
     * Feature: 非同步檔案傳輸長任務 API (Mode 1)
     * Scenario: 接收 POST /api/transmission/upload/async 請求並成功回傳 202 Accepted 與 trackingId
     * Given: 傳入標準上傳請求 JSON
     * When: 發送 POST 請求至 /api/transmission/upload/async
     * Then: 控制器呼叫 SubmitUploadFileUseCase 並立即回傳 202 Accepted 與 IN_PROGRESS 狀態
     */
    @Test
    @DisplayName("情境：POST /api/transmission/upload/async 成功回應 202 Accepted")
    void testSubmitUploadEndpoint() throws Exception {
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
                    "host": "127.0.0.1",
                    "port": 22,
                    "protocol": "SFTP",
                    "username": "sftpuser",
                    "password": "secretPassword",
                    "localFilePath": "src/test/resources",
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
     * Given: 傳入標準下載請求 JSON
     * When: 發送 POST 請求至 /api/transmission/download/async
     * Then: 控制器呼叫 SubmitDownloadFileUseCase 並回傳 202 Accepted
     */
    @Test
    @DisplayName("情境：POST /api/transmission/download/async 成功回應 202 Accepted")
    void testSubmitDownloadEndpoint() throws Exception {
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
                    "host": "127.0.0.1",
                    "port": 21,
                    "protocol": "FTP",
                    "username": "ftpuser",
                    "password": "ftpPassword",
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
     * Given: 傳入標準同步請求 JSON
     * When: 發送 POST 請求至 /api/transmission/sync/async
     * Then: 控制器呼叫 SubmitSyncDirectoryUseCase 並回傳 202 Accepted
     */
    @Test
    @DisplayName("情境：POST /api/transmission/sync/async 成功回應 202 Accepted")
    void testSubmitSyncEndpoint() throws Exception {
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
                    "host": "127.0.0.1",
                    "port": 990,
                    "protocol": "FTPS",
                    "username": "ftpsuser",
                    "password": "ftpsPassword",
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
     * Scenario: 接收 POST /api/transmission/upload 傳入不合法的傳輸協定
     * Given: 請求體中指定 protocol 為 "INVALID_PROTO"
     * When: 發送 POST 請求至 /api/transmission/upload
     * Then: Assembler 拋出 IllegalArgumentException，由 GlobalExceptionHandler 攔截並回傳 400 Bad Request
     */
    @Test
    @DisplayName("情境：POST /api/transmission/upload 協定無效回應 400 Bad Request")
    void testUploadEndpointInvalidProtocol() throws Exception {
        String invalidRequestJson = """
                {
                    "host": "sftp.example.com",
                    "port": 22,
                    "protocol": "INVALID_PROTO",
                    "username": "user",
                    "password": "pwd",
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
        when(uploadFileUseCase.uploadFile(any(UploadFileCommand.class)))
                .thenThrow(new FileTransmissionException("本機上傳來源檔案不存在或非一般檔案"));

        String requestJson = """
                {
                    "host": "sftp.example.com",
                    "port": 22,
                    "protocol": "SFTP",
                    "username": "user",
                    "password": "pwd",
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
}
