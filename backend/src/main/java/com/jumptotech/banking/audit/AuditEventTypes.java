package com.jumptotech.banking.audit;

public final class AuditEventTypes {

    public static final String REGISTRATION_SUCCEEDED = "REGISTRATION_SUCCEEDED";
    public static final String LOGIN_SUCCEEDED = "LOGIN_SUCCEEDED";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String LOGOUT = "LOGOUT";
    public static final String PROFILE_UPDATED = "PROFILE_UPDATED";
    public static final String TRANSFER_COMPLETED = "TRANSFER_COMPLETED";
    public static final String TRANSFER_REJECTED = "TRANSFER_REJECTED";

    private AuditEventTypes() {
    }
}
