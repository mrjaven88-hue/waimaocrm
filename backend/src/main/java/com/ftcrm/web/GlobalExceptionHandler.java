package com.ftcrm.web;

import com.ftcrm.common.ApiException;
import com.ftcrm.web.dto.ErrorBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorBody> api(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new ErrorBody(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> invalid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> label(error.getField()) + "不合法")
                .distinct()
                .collect(Collectors.joining("；"));
        if (message.isBlank()) {
            message = "请求不合法";
        }
        return ResponseEntity.badRequest().body(new ErrorBody("VALIDATION", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorBody> badJson(HttpMessageNotReadableException ex) {
        log.warn("rejected unreadable request");
        return ResponseEntity.badRequest().body(new ErrorBody("BAD_JSON", "请求格式不正确"));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorBody> missing(Exception ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody("NOT_FOUND", "资源不存在"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorBody> denied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorBody("FORBIDDEN", "没有权限"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> unknown(Exception ex) {
        log.error("unhandled {}", ex.getClass().getName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorBody("INTERNAL", "服务器错误"));
    }

    private static String label(String field) {
        String leaf = field;
        int dot = leaf.lastIndexOf('.');
        if (dot >= 0) {
            leaf = leaf.substring(dot + 1);
        }
        leaf = leaf.replaceAll("\\[\\d+]", "");
        return switch (leaf) {
            case "username" -> "用户名";
            case "password" -> "密码";
            case "displayName" -> "姓名";
            case "companyName" -> "公司";
            case "country" -> "国家";
            case "contactName" -> "联系人";
            case "phone", "email", "whatsapp" -> "联系方式";
            case "reason" -> "原因";
            case "leads" -> "导入数据";
            default -> "字段";
        };
    }
}
