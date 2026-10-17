package com.example.demo.presentation.handler;

import com.example.demo.application.shared.exception.FileTransmissionException;
import com.example.demo.application.shared.exception.RemoteDirectoryCreationException;
import com.example.demo.application.shared.exception.TransmissionConnectionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全域例外處理器單元測試 (GlobalExceptionHandlerTest)
 * <p>
 * 遵循測試規範 (AGENTS.md 第 5 節)：
 * 1. 涵蓋專案內各類被拋出的例外處理情境 (連線異常、傳輸異常、非法參數、未預期錯誤等)；
 * 2. 測試方法均撰寫 Gherkin 語法 Javadoc (Feature, Scenario, Given-When-Then)。
 * </p>
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @RestController
    static class ExceptionTestController {

        @GetMapping("/test/connection-error")
        public void throwConnectionException() {
            throw new TransmissionConnectionException("SFTP 連線逾時: 連線遭拒");
        }

        @GetMapping("/test/remote-dir-error")
        public void throwRemoteDirException() {
            throw new RemoteDirectoryCreationException("遠端目錄建立失敗: 權限不足");
        }

        @GetMapping("/test/transmission-error")
        public void throwTransmissionException() {
            throw new FileTransmissionException("本機來源檔案不存在或非一般檔案");
        }

        @GetMapping("/test/illegal-argument")
        public void throwIllegalArgumentException() {
            throw new IllegalArgumentException("不支援的傳輸協定: UNKNOWN");
        }

        @GetMapping("/test/illegal-state")
        public void throwIllegalStateException() {
            throw new IllegalStateException("傳輸通道未開啟");
        }

        @PostMapping("/test/json-body")
        public void acceptJson(@RequestBody DummyPayload payload) {
            // normal handler
        }

        @GetMapping("/test/general-error")
        public void throwGeneralException() {
            throw new RuntimeException("資料庫連線池枯竭");
        }
    }

    static class DummyPayload {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders
                .standaloneSetup(new ExceptionTestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /**
     * Feature: 全域例外處理
     * Scenario: 拋出 TransmissionConnectionException 時，應回傳 502 Bad Gateway
     * Given: 呼叫觸發遠端連線或驗證失敗的 API 端點
     * When: 發送 GET 請求至 /test/connection-error
     * Then: GlobalExceptionHandler 攔截並回傳 502 Bad Gateway 與 TransmissionErrorOccurredResource
     */
    @Test
    @DisplayName("情境：處理 TransmissionConnectionException 回傳 502 Bad Gateway")
    void shouldHandleTransmissionConnectionException() throws Exception {
        mockMvc.perform(get("/test/connection-error"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status", is(502)))
                .andExpect(jsonPath("$.error", is("Bad Gateway")))
                .andExpect(jsonPath("$.message", containsString("SFTP 連線逾時")))
                .andExpect(jsonPath("$.path", is("/test/connection-error")))
                .andExpect(jsonPath("$.exception", is("TransmissionConnectionException")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    /**
     * Feature: 全域例外處理
     * Scenario: 拋出 RemoteDirectoryCreationException 時，應回傳 500 Internal Server Error
     * Given: 呼叫觸發遠端目錄建立異常的 API 端點
     * When: 發送 GET 請求至 /test/remote-dir-error
     * Then: GlobalExceptionHandler 攔截並回傳 500 與 TransmissionErrorOccurredResource
     */
    @Test
    @DisplayName("情境：處理 RemoteDirectoryCreationException 回傳 500 Internal Server Error")
    void shouldHandleRemoteDirectoryCreationException() throws Exception {
        mockMvc.perform(get("/test/remote-dir-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status", is(500)))
                .andExpect(jsonPath("$.error", is("Internal Server Error")))
                .andExpect(jsonPath("$.message", containsString("遠端目錄建立失敗")))
                .andExpect(jsonPath("$.path", is("/test/remote-dir-error")))
                .andExpect(jsonPath("$.exception", is("RemoteDirectoryCreationException")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    /**
     * Feature: 全域例外處理
     * Scenario: 拋出 FileTransmissionException 時，應回傳 500 Internal Server Error
     * Given: 呼叫觸發檔案傳輸核心異常的 API 端點
     * When: 發送 GET 請求至 /test/transmission-error
     * Then: GlobalExceptionHandler 攔截並回傳 500 與 TransmissionErrorOccurredResource
     */
    @Test
    @DisplayName("情境：處理 FileTransmissionException 回傳 500 Internal Server Error")
    void shouldHandleFileTransmissionException() throws Exception {
        mockMvc.perform(get("/test/transmission-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status", is(500)))
                .andExpect(jsonPath("$.error", is("Internal Server Error")))
                .andExpect(jsonPath("$.message", containsString("本機來源檔案不存在")))
                .andExpect(jsonPath("$.path", is("/test/transmission-error")))
                .andExpect(jsonPath("$.exception", is("FileTransmissionException")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    /**
     * Feature: 全域例外處理
     * Scenario: 拋出 IllegalArgumentException 時，應回傳 400 Bad Request
     * Given: 呼叫傳入不合法參數之 API 端點
     * When: 發送 GET 請求至 /test/illegal-argument
     * Then: GlobalExceptionHandler 攔截並回傳 400 與 TransmissionErrorOccurredResource
     */
    @Test
    @DisplayName("情境：處理 IllegalArgumentException 回傳 400 Bad Request")
    void shouldHandleIllegalArgumentException() throws Exception {
        mockMvc.perform(get("/test/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("不支援的傳輸協定")))
                .andExpect(jsonPath("$.path", is("/test/illegal-argument")))
                .andExpect(jsonPath("$.exception", is("IllegalArgumentException")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    /**
     * Feature: 全域例外處理
     * Scenario: 拋出 IllegalStateException 時，應回傳 400 Bad Request
     * Given: 呼叫處於非法狀態之 API 端點
     * When: 發送 GET 請求至 /test/illegal-state
     * Then: GlobalExceptionHandler 攔截並回傳 400 與 TransmissionErrorOccurredResource
     */
    @Test
    @DisplayName("情境：處理 IllegalStateException 回傳 400 Bad Request")
    void shouldHandleIllegalStateException() throws Exception {
        mockMvc.perform(get("/test/illegal-state"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("傳輸通道未開啟")))
                .andExpect(jsonPath("$.path", is("/test/illegal-state")))
                .andExpect(jsonPath("$.exception", is("IllegalStateException")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    /**
     * Feature: 全域例外處理
     * Scenario: 傳入格式錯誤的 JSON 請求體時，應回傳 400 Bad Request
     * Given: 發送非法格式之 JSON 內容
     * When: 發送 POST 請求至 /test/json-body
     * Then: GlobalExceptionHandler 攔截 HttpMessageNotReadableException 並回傳 400
     */
    @Test
    @DisplayName("情境：處理 HttpMessageNotReadableException 回傳 400 Bad Request")
    void shouldHandleHttpMessageNotReadableException() throws Exception {
        mockMvc.perform(post("/test/json-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": invalid_json_syntax}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("HTTP 請求體格式錯誤")))
                .andExpect(jsonPath("$.path", is("/test/json-body")))
                .andExpect(jsonPath("$.exception", is("HttpMessageNotReadableException")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    /**
     * Feature: 全域例外處理
     * Scenario: 發送不支援的 HTTP Method 時，應回傳 405 Method Not Allowed
     * Given: 端點僅支援 POST
     * When: 發送 DELETE 請求至 /test/json-body
     * Then: GlobalExceptionHandler 攔截 HttpRequestMethodNotSupportedException 並回傳 405
     */
    @Test
    @DisplayName("情境：處理 HttpRequestMethodNotSupportedException 回傳 405 Method Not Allowed")
    void shouldHandleHttpRequestMethodNotSupportedException() throws Exception {
        mockMvc.perform(delete("/test/json-body"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status", is(405)))
                .andExpect(jsonPath("$.error", is("Method Not Allowed")))
                .andExpect(jsonPath("$.message", containsString("不支援的 HTTP 請求方法")))
                .andExpect(jsonPath("$.path", is("/test/json-body")))
                .andExpect(jsonPath("$.exception", is("HttpRequestMethodNotSupportedException")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    /**
     * Feature: 全域例外處理
     * Scenario: 拋出未預期之 Exception 時，應回傳 500 Internal Server Error
     * Given: 呼叫觸發一般 RuntimeException 之 API 端點
     * When: 發送 GET 請求至 /test/general-error
     * Then: GlobalExceptionHandler 攔截並回傳 500 與防洩漏之通用錯誤訊息
     */
    @Test
    @DisplayName("情境：處理未預期之通用 Exception 回傳 500 Internal Server Error")
    void shouldHandleGeneralException() throws Exception {
        mockMvc.perform(get("/test/general-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status", is(500)))
                .andExpect(jsonPath("$.error", is("Internal Server Error")))
                .andExpect(jsonPath("$.message", is("伺服器內部發生未預期錯誤，請聯絡系統管理員")))
                .andExpect(jsonPath("$.path", is("/test/general-error")))
                .andExpect(jsonPath("$.exception", is("RuntimeException")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }
}
