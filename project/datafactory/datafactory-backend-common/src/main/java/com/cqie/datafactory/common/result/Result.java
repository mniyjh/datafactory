package com.cqie.datafactory.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class Result<T> {
    private int code;
    private String message;
    private T data;

    public Result() {}

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> success(T data) {
        return new Result<T>(0, "success", data);
    }

    public static <T> Result<T> success() {
        return new Result<T>(0, "success", null);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<T>(-1, message, null);
    }
}
