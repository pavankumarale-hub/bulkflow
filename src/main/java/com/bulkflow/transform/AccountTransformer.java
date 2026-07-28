package com.bulkflow.transform;

import com.bulkflow.model.AccountRecord;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class AccountTransformer {

    public AccountRecord transform(AccountRecord record) {
        record.setEmail(record.getEmail().trim().toLowerCase());
        record.setFirstName(capitalize(record.getFirstName().trim()));
        record.setLastName(capitalize(record.getLastName().trim()));

        if (record.getStatus() == null || record.getStatus().isBlank()) {
            record.setStatus("PENDING");
        } else {
            record.setStatus(record.getStatus().trim().toUpperCase());
        }

        if (record.getCurrency() == null || record.getCurrency().isBlank()) {
            record.setCurrency("USD");
        } else {
            record.setCurrency(record.getCurrency().trim().toUpperCase());
        }

        if (record.getPhone() != null) {
            record.setPhone(record.getPhone().replaceAll("\\s+", "").trim());
        }

        record.setRowHash(computeHash(record));
        return record;
    }

    private String computeHash(AccountRecord r) {
        // Hash of natural key + all mutable fields — same record always produces same hash
        String content = String.join("|",
                safe(r.getAccountId()),
                safe(r.getEmail()),
                safe(r.getFirstName()),
                safe(r.getLastName()),
                safe(r.getStatus()),
                r.getDateOfBirth() != null ? r.getDateOfBirth().toString() : "",
                safe(r.getPhone()),
                r.getCreditLimit() != null ? r.getCreditLimit().toPlainString() : "",
                safe(r.getCurrency())
        );
        return sha256(content);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }
}
