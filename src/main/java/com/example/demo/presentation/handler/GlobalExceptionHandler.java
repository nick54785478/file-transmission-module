package com.example.demo.presentation.handler;

import com.example.demo.application.shared.exception.FileTransmissionException;
import com.example.demo.application.shared.exception.RemoteDirectoryCreationException;
import com.example.demo.application.shared.exception.TransmissionConnectionException;
import com.example.demo.presentation.assembler.TransmissionResourceAssembler;
import com.example.demo.presentation.resource.out.TransmissionErrorOccurredResource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 表現層全域例外處理器 (GlobalExceptionHandler - Inbound Adapter / Controller Advice)
 * <p>
 * <b>架構與設計職責：</b>
 * <ul>
 *   <li>作為表現層的集中錯誤攔截器，捕獲 Controller 與 UseCase 執行過程中拋出的受檢與未受檢例外。</li>
 *   <li>防止底層基礎設施與資料庫細節、連線協定異常向外洩漏，遵循防腐層原則將例外組裝為標準化的
 *       {@link TransmissionErrorOccurredResource}。</li>
 *   <li>依據業務語意與 HTTP REST 規範映射合適的狀態代碼：
 *     <ul>
 *       <li>{@link TransmissionConnectionException} &rarr; 502 Bad Gateway (遠端主機連線逾時、拒絕連線或身份驗證失敗)</li>
 *       <li>{@link RemoteDirectoryCreationException} &rarr; 500 Internal Server Error (遠端目錄建立異常)</li>
 *       <li>{@link FileTransmissionException} &rarr; 500 Internal Server Error (檔案傳輸應用層基底例外)</li>
 *       <li>{@link IllegalArgumentException} &rarr; 400 Bad Request (用戶端輸入參數格式不合法或不支援的協定)</li>
 *       <li>{@link IllegalStateException} &rarr; 400 Bad Request (系統當前狀態無法滿足請求)</li>
 *       <li>{@link HttpMessageNotReadableException} &rarr; 400 Bad Request (請求 JSON 格式錯誤或無法反序列化)</li>
 *       <li>{@link MethodArgumentNotValidException} &rarr; 400 Bad Request (參數 Bean Validation 校驗未通過)</li>
 *       <li>{@link HttpRequestMethodNotSupportedException} &rarr; 405 Method Not Allowed (不支援之 HTTP 方法)</li>
 *       <li>{@link Exception} &rarr; 500 Internal Server Error (全域最後防線，未預期的系統錯誤)</li>
 *     </ul>
 *   </li>
 * </ul>
 * </p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 處理遠端伺服器連線與驗證異常 (TransmissionConnectionException)
     * <p>
     * 當與遠端 FTP / FTPS / SFTP 主機連線失敗、連線逾時或認證遭拒時觸發，
     * 由於屬於上游外部協定伺服器問題，因此對應 HTTP 502 Bad Gateway。
     * </p>
     *
     * @param ex 遠端伺服器連線異常
     * @param request HTTP 請求物件
     * @return 502 Bad Gateway 與錯誤回應資料載體
     */
    @ExceptionHandler(TransmissionConnectionException.class)
    public ResponseEntity<TransmissionErrorOccurredResource> handleTransmissionConnectionException(
            TransmissionConnectionException ex,
            HttpServletRequest request
    ) {
        log.error("遠端傳輸主機連線或認證失敗 [URI: {}]: {}", request.getRequestURI(), ex.getMessage(), ex);
        HttpStatus status = HttpStatus.BAD_GATEWAY;
        TransmissionErrorOccurredResource errorResource = TransmissionResourceAssembler.toErrorResource(
                status,
                ex.getMessage(),
                request.getRequestURI(),
                ex.getClass().getSimpleName()
        );
        return ResponseEntity.status(status).body(errorResource);
    }

    /**
     * 處理遠端目錄建立失敗異常 (RemoteDirectoryCreationException)
     * <p>
     * 當在遠端伺服器上建立工作目錄因權限不足或檔案系統限制失敗時觸發，對應 HTTP 500 Internal Server Error。
     * </p>
     *
     * @param ex 遠端目錄建立異常
     * @param request HTTP 請求物件
     * @return 500 Internal Server Error 與錯誤回應資料載體
     */
    @ExceptionHandler(RemoteDirectoryCreationException.class)
    public ResponseEntity<TransmissionErrorOccurredResource> handleRemoteDirectoryCreationException(
            RemoteDirectoryCreationException ex,
            HttpServletRequest request
    ) {
        log.error("遠端目錄建立作業失敗 [URI: {}]: {}", request.getRequestURI(), ex.getMessage(), ex);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        TransmissionErrorOccurredResource errorResource = TransmissionResourceAssembler.toErrorResource(
                status,
                ex.getMessage(),
                request.getRequestURI(),
                ex.getClass().getSimpleName()
        );
        return ResponseEntity.status(status).body(errorResource);
    }

    /**
     * 處理通用檔案傳輸應用層基底異常 (FileTransmissionException)
     * <p>
     * 攔截檔案傳輸執行中發生的所有應用層邏輯錯誤、I/O 傳輸失敗或本機來源檔案不存在等異常。
     * </p>
     *
     * @param ex 檔案傳輸應用例外
     * @param request HTTP 請求物件
     * @return 500 Internal Server Error 與錯誤回應資料載體
     */
    @ExceptionHandler(FileTransmissionException.class)
    public ResponseEntity<TransmissionErrorOccurredResource> handleFileTransmissionException(
            FileTransmissionException ex,
            HttpServletRequest request
    ) {
        log.error("檔案傳輸作業執行失敗 [URI: {}]: {}", request.getRequestURI(), ex.getMessage(), ex);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        TransmissionErrorOccurredResource errorResource = TransmissionResourceAssembler.toErrorResource(
                status,
                ex.getMessage(),
                request.getRequestURI(),
                ex.getClass().getSimpleName()
        );
        return ResponseEntity.status(status).body(errorResource);
    }

    /**
     * 處理非法參數異常 (IllegalArgumentException)
     * <p>
     * 攔截用戶端傳入無效或缺漏的傳輸協定、空路徑、非法主機資訊等參數錯誤。
     * </p>
     *
     * @param ex 非法參數例外
     * @param request HTTP 請求物件
     * @return 400 Bad Request 與錯誤回應資料載體
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<TransmissionErrorOccurredResource> handleIllegalArgumentException(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        log.warn("用戶端傳入不合法的參數 [URI: {}]: {}", request.getRequestURI(), ex.getMessage());
        HttpStatus status = HttpStatus.BAD_REQUEST;
        TransmissionErrorOccurredResource errorResource = TransmissionResourceAssembler.toErrorResource(
                status,
                ex.getMessage(),
                request.getRequestURI(),
                ex.getClass().getSimpleName()
        );
        return ResponseEntity.status(status).body(errorResource);
    }

    /**
     * 處理非法狀態異常 (IllegalStateException)
     * <p>
     * 攔截物件或業務狀態不允許執行該操作的異常。
     * </p>
     *
     * @param ex 非法狀態例外
     * @param request HTTP 請求物件
     * @return 400 Bad Request 與錯誤回應資料載體
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<TransmissionErrorOccurredResource> handleIllegalStateException(
            IllegalStateException ex,
            HttpServletRequest request
    ) {
        log.warn("作業因狀態限制無法執行 [URI: {}]: {}", request.getRequestURI(), ex.getMessage());
        HttpStatus status = HttpStatus.BAD_REQUEST;
        TransmissionErrorOccurredResource errorResource = TransmissionResourceAssembler.toErrorResource(
                status,
                ex.getMessage(),
                request.getRequestURI(),
                ex.getClass().getSimpleName()
        );
        return ResponseEntity.status(status).body(errorResource);
    }

    /**
     * 處理 HTTP 請求內容解析錯誤 (HttpMessageNotReadableException)
     * <p>
     * 攔截 JSON 語法格式錯誤、型別無法轉換或 Request Body 缺失的情況。
     * </p>
     *
     * @param ex 訊息不可讀例外
     * @param request HTTP 請求物件
     * @return 400 Bad Request 與錯誤回應資料載體
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<TransmissionErrorOccurredResource> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        log.warn("用戶端 HTTP 請求體格式不合法 [URI: {}]: {}", request.getRequestURI(), ex.getMessage());
        HttpStatus status = HttpStatus.BAD_REQUEST;
        TransmissionErrorOccurredResource errorResource = TransmissionResourceAssembler.toErrorResource(
                status,
                "HTTP 請求體格式錯誤或無法解析 JSON 資料",
                request.getRequestURI(),
                ex.getClass().getSimpleName()
        );
        return ResponseEntity.status(status).body(errorResource);
    }

    /**
     * 處理方法參數校驗失敗異常 (MethodArgumentNotValidException 與 BindException)
     * <p>
     * 攔截 Spring Validation 驗證失敗的錯誤並提取第一條提示訊息。
     * </p>
     *
     * @param ex 參數校驗失敗例外
     * @param request HTTP 請求物件
     * @return 400 Bad Request 與錯誤回應資料載體
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<TransmissionErrorOccurredResource> handleValidationException(
            Exception ex,
            HttpServletRequest request
    ) {
        String detailMessage = "參數檢核未通過";
        if (ex instanceof BindException bindEx) {
            detailMessage = bindEx.getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .findFirst()
                    .orElse("參數檢核未通過");
        }

        log.warn("用戶端請求參數校驗失敗 [URI: {}]: {}", request.getRequestURI(), detailMessage);
        HttpStatus status = HttpStatus.BAD_REQUEST;
        TransmissionErrorOccurredResource errorResource = TransmissionResourceAssembler.toErrorResource(
                status,
                detailMessage,
                request.getRequestURI(),
                ex.getClass().getSimpleName()
        );
        return ResponseEntity.status(status).body(errorResource);
    }

    /**
     * 處理約束校驗違反異常 (ConstraintViolationException)
     * <p>
     * 攔截 @Validated 觸發之路徑變數或手動驗證之約束違規。
     * </p>
     *
     * @param ex 約束違反例外
     * @param request HTTP 請求物件
     * @return 400 Bad Request 與錯誤回應資料載體
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<TransmissionErrorOccurredResource> handleConstraintViolationException(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        String detailMessage = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .findFirst()
                .orElse(ex.getMessage());

        log.warn("用戶端請求參數約束校驗失敗 [URI: {}]: {}", request.getRequestURI(), detailMessage);
        HttpStatus status = HttpStatus.BAD_REQUEST;
        TransmissionErrorOccurredResource errorResource = TransmissionResourceAssembler.toErrorResource(
                status,
                detailMessage,
                request.getRequestURI(),
                ex.getClass().getSimpleName()
        );
        return ResponseEntity.status(status).body(errorResource);
    }

    /**
     * 處理不支援的 HTTP 請求方法 (HttpRequestMethodNotSupportedException)
     * <p>
     * 當請求端使用了 API 未定義的 HTTP Method 時觸發 (例如對 POST 端點發送 GET)。
     * </p>
     *
     * @param ex 請求方法不支援例外
     * @param request HTTP 請求物件
     * @return 405 Method Not Allowed 與錯誤回應資料載體
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<TransmissionErrorOccurredResource> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        log.warn("不支援的 HTTP 請求方法 [URI: {}]: {}", request.getRequestURI(), ex.getMethod());
        HttpStatus status = HttpStatus.METHOD_NOT_ALLOWED;
        TransmissionErrorOccurredResource errorResource = TransmissionResourceAssembler.toErrorResource(
                status,
                "不支援的 HTTP 請求方法: " + ex.getMethod(),
                request.getRequestURI(),
                ex.getClass().getSimpleName()
        );
        return ResponseEntity.status(status).body(errorResource);
    }

    /**
     * 處理查無資料異常 (NoSuchElementException)
     *
     * @param ex 查無元素例外
     * @param request HTTP 請求物件
     * @return 404 Not Found 與錯誤回應資料載體
     */
    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<TransmissionErrorOccurredResource> handleNoSuchElementException(
            java.util.NoSuchElementException ex,
            HttpServletRequest request
    ) {
        log.warn("查無指定的資料資源 [URI: {}]: {}", request.getRequestURI(), ex.getMessage());
        HttpStatus status = HttpStatus.NOT_FOUND;
        TransmissionErrorOccurredResource errorResource = TransmissionResourceAssembler.toErrorResource(
                status,
                ex.getMessage(),
                request.getRequestURI(),
                ex.getClass().getSimpleName()
        );
        return ResponseEntity.status(status).body(errorResource);
    }

    /**
     * 處理全域所有未預期之例外 (Exception)
     * <p>
     * 作為系統表現層的最後防線，攔截所有未被特定 Handler 捕捉的例外，防止未受控的 Exception 堆疊直接洩漏。
     * </p>
     *
     * @param ex 未知例外
     * @param request HTTP 請求物件
     * @return 500 Internal Server Error 與錯誤回應資料載體
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<TransmissionErrorOccurredResource> handleGeneralException(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("系統遭遇未預期的未知異常 [URI: {}]: {}", request.getRequestURI(), ex.getMessage(), ex);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        TransmissionErrorOccurredResource errorResource = TransmissionResourceAssembler.toErrorResource(
                status,
                "伺服器內部發生未預期錯誤，請聯絡系統管理員",
                request.getRequestURI(),
                ex.getClass().getSimpleName()
        );
        return ResponseEntity.status(status).body(errorResource);
    }
}
