package com.banquito.core.account.shared.exception;

import com.banquito.core.account.api.dto.api.ErrorResponse;
import com.banquito.core.account.shared.tracing.CorrelationIdHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new ErrorResponse(
                LocalDateTime.now(),
                CorrelationIdHolder.get(),
                ex.getCode(),
                ex.getMessage(),
                List.of()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(
                LocalDateTime.now(),
                CorrelationIdHolder.get(),
                "VALIDATION_ERROR",
                "Datos de entrada inválidos",
                details
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(
                LocalDateTime.now(),
                CorrelationIdHolder.get(),
                "INVALID_REQUEST_VALUE",
                "Uno o más valores enviados no son válidos",
                List.of(ex.getMessage())
        ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                LocalDateTime.now(),
                CorrelationIdHolder.get(),
                "DATA_INTEGRITY_VIOLATION",
                "La operación viola una regla de integridad de la base de datos",
                List.of(ex.getMostSpecificCause() == null ? ex.getClass().getSimpleName() : ex.getMostSpecificCause().getMessage())
        ));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Concurrent modification detected. correlationId={}", CorrelationIdHolder.get(), ex);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                LocalDateTime.now(),
                CorrelationIdHolder.get(),
                "ACCOUNT_CONCURRENT_MODIFICATION",
                "La operación coincidió con otra actualización sobre el mismo recurso. Consulte su estado antes de reintentar.",
                List.of()
        ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(
                LocalDateTime.now(),
                CorrelationIdHolder.get(),
                "RESOURCE_NOT_FOUND",
                "Recurso no encontrado",
                List.of(ex.getResourcePath())
        ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(
                LocalDateTime.now(),
                CorrelationIdHolder.get(),
                "SECURITY_ACCESS_DENIED",
                "Acceso denegado. El token no posee permisos para este recurso.",
                List.of()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception. correlationId={}", CorrelationIdHolder.get(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(
                LocalDateTime.now(),
                CorrelationIdHolder.get(),
                "INTERNAL_ERROR",
                "Error interno no controlado",
                List.of(ex.getClass().getSimpleName())
        ));
    }
}
