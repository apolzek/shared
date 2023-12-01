package com.example.postservice.feign;


public record ExceptionMessage(String timestamp,
                               int status,
                               String error,
                               String message,
                               String path) {
}
