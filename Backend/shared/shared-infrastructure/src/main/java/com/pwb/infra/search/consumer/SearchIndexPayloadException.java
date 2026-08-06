package com.pwb.infra.search.consumer;

public class SearchIndexPayloadException extends RuntimeException {

    public SearchIndexPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
