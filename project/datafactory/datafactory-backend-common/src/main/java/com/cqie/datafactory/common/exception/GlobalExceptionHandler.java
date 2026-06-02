package com.cqie.datafactory.common.exception;

import com.cqie.datafactory.common.result.Result;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        return Result.fail(e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, ConstraintViolationException.class})
    public Result<Void> handleValidation(Exception e) {
        return Result.fail("参数校验失败: " + e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleBadBody(HttpMessageNotReadableException e) {
        return Result.fail("请求体格式错误");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        String msg = e.getMessage();
        if (msg != null && (msg.contains("SQLSyntaxErrorException") || msg.contains("java.sql."))) {
            return Result.fail("服务异常，请稍后重试");
        }
        return Result.fail("服务异常: " + msg);
    }
}
