package com.bulkflow.ingestion;

public enum FeedType {
    ACCOUNTS("accounts"),
    TRANSACTIONS("transactions");

    private final String value;

    FeedType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static FeedType fromFilename(String filename) {
        if (filename == null) return null;
        String lower = filename.toLowerCase();
        if (lower.contains("account")) return ACCOUNTS;
        if (lower.contains("transaction")) return TRANSACTIONS;
        return null;
    }
}
