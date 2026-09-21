package com.coldstore.freezer.dto;

public class BizException extends RuntimeException {
    public BizException(String message) {
        super(message);
    }
}
