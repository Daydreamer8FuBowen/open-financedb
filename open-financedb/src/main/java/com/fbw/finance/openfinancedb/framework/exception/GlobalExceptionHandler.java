package com.fbw.finance.openfinancedb.framework.exception;

import com.fbw.finance.openfinancedb.framework.web.CommonResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ServiceException.class)
    public CommonResult<Void> handleServiceException(ServiceException exception) {
        return CommonResult.error(exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public CommonResult<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        return CommonResult.error(ErrorCodeConstants.BAD_REQUEST, buildFieldErrors(exception.getBindingResult().getFieldErrors()));
    }

    @ExceptionHandler(BindException.class)
    public CommonResult<Void> handleBindException(BindException exception) {
        return CommonResult.error(ErrorCodeConstants.BAD_REQUEST, buildFieldErrors(exception.getBindingResult().getFieldErrors()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public CommonResult<Void> handleConstraintViolationException(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        return CommonResult.error(ErrorCodeConstants.BAD_REQUEST, message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public CommonResult<Void> handleHandlerMethodValidationException(HandlerMethodValidationException exception) {
        return CommonResult.error(ErrorCodeConstants.BAD_REQUEST, exception.getAllErrors().stream()
                .map(error -> error.getDefaultMessage() == null ? "invalid request" : error.getDefaultMessage())
                .collect(Collectors.joining("; ")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public CommonResult<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException exception) {
        return CommonResult.error(ErrorCodeConstants.BAD_REQUEST, exception.getName() + " type mismatch");
    }

    private String buildFieldErrors(List<FieldError> fieldErrors) {
        if (fieldErrors.isEmpty()) {
            return "invalid request";
        }
        return fieldErrors.stream()
                .map(error -> error.getField() + " " + (error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage()))
                .collect(Collectors.joining("; "));
    }
}
