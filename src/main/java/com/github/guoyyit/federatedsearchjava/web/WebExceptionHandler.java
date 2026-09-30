package com.github.guoyyit.federatedsearchjava.web;

import com.github.guoyyit.federatedsearchjava.federation.SnapshotExpiredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class WebExceptionHandler {

    @ExceptionHandler(SnapshotExpiredException.class)
    public ResponseEntity<Map<String, String>> handleExpired(SnapshotExpiredException e) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(Map.of(
                        "error", "SNAPSHOT_EXPIRED",
                        "snapshotId", e.getMessage(),
                        "message", "snapshot expired or unknown, please start a new search"
                ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "INVALID_REQUEST", "message", e.getMessage()));
    }
}
