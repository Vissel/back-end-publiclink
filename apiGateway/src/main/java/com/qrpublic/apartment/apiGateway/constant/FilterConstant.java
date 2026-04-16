package com.qrpublic.apartment.apiGateway.constant;

public class FilterConstant {
    
    // HTTP Headers
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String USER_ID_HEADER = "X-User-ID";
    public static final String TRACE_ID_HEADER = "X-Trace-ID";
    public static final String AUTH_TIMESTAMP_HEADER = "X-Auth-Timestamp";
    
    // Security Headers
    public static final String CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options";
    public static final String CONTENT_TYPE_OPTIONS_VALUE = "nosniff";
    public static final String FRAME_OPTIONS_HEADER = "X-Frame-Options";
    public static final String FRAME_OPTIONS_VALUE = "DENY";
    public static final String XSS_PROTECTION_HEADER = "X-XSS-Protection";
    public static final String XSS_PROTECTION_VALUE = "1; mode=block";
    
    // JWT Claims
    public static final String ROLES_CLAIM = "roles";
    public static final String TOKEN_TYPE_CLAIM = "type";
    public static final String ACCESS_TOKEN_TYPE = "access";
    public static final String REFRESH_TOKEN_TYPE = "refresh";
    
    private FilterConstant() {
        // Private constructor to prevent instantiation
    }
}
