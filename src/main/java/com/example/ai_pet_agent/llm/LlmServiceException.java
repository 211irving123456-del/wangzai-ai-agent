
package com.example.ai_pet_agent.llm;

import org.springframework.http.HttpStatus;

public class LlmServiceException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;

    public LlmServiceException(
            String code,
            HttpStatus httpStatus,
            String message) {

        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public LlmServiceException(
            String code,
            HttpStatus httpStatus,
            String message,
            Throwable cause) {

        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
