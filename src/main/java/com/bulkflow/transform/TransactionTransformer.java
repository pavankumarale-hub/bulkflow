package com.bulkflow.transform;

import com.bulkflow.model.TransactionRecord;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class TransactionTransformer {

    public TransactionRecord transform(TransactionRecord record) {
        record.setTransactionType(record.getTransactionType().trim().toUpperCase());

        if (record.getCurrency() == null || record.getCurrency().isBlank()) {
            record.setCurrency("USD");
        } else {
            record.setCurrency(record.getCurrency().trim().toUpperCase());
        }

        if (record.getDescription() != null) {
            record.setDescription(record.getDescription().trim());
        }

        record.setRowHash(computeHash(record));
        return record;
    }

    private String computeHash(TransactionRecord r) {
        String content = String.join("|",
                safe(r.getTransactionId()),
                safe(r.getAccountId()),
                r.getAmount() != null ? r.getAmount().toPlainString() : "",
                safe(r.getCurrency()),
                safe(r.getTransactionType()),
                r.getTransactionDate() != null ? r.getTransactionDate().toString() : "",
                safe(r.getDescription())
        );
        return sha256(content);
    }

    private String safe(String s) {
        return s == null ? "" : s;
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
