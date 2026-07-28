package com.bulkflow.unit;

import com.bulkflow.model.AccountRecord;
import com.bulkflow.transform.AccountTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AccountTransformerTest {

    private AccountTransformer transformer;

    @BeforeEach
    void setup() {
        transformer = new AccountTransformer();
    }

    private AccountRecord record() {
        return AccountRecord.builder()
                .accountId("acc_00001")
                .email("  ALICE.SMITH@Example.COM  ")
                .firstName("  alice  ")
                .lastName("  SMITH  ")
                .status("  active  ")
                .currency("  usd  ")
                .phone("  +1 555 123 4567  ")
                .creditLimit(new BigDecimal("5000.00"))
                .rawLine("raw")
                .build();
    }

    @Test
    void email_lowercased_and_trimmed() {
        AccountRecord result = transformer.transform(record());
        assertThat(result.getEmail()).isEqualTo("alice.smith@example.com");
    }

    @Test
    void name_capitalized_and_trimmed() {
        AccountRecord result = transformer.transform(record());
        assertThat(result.getFirstName()).isEqualTo("Alice");
        assertThat(result.getLastName()).isEqualTo("Smith");
    }

    @Test
    void status_uppercased() {
        AccountRecord result = transformer.transform(record());
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void currency_uppercased() {
        AccountRecord result = transformer.transform(record());
        assertThat(result.getCurrency()).isEqualTo("USD");
    }

    @Test
    void phone_whitespace_stripped() {
        AccountRecord result = transformer.transform(record());
        assertThat(result.getPhone()).doesNotContain(" ");
    }

    @Test
    void row_hash_computed_and_stable() {
        AccountRecord r1 = transformer.transform(record());
        AccountRecord r2 = transformer.transform(record());
        assertThat(r1.getRowHash()).isNotNull();
        assertThat(r1.getRowHash()).hasSize(64); // SHA-256 hex
        assertThat(r1.getRowHash()).isEqualTo(r2.getRowHash()); // deterministic
    }

    @Test
    void different_records_produce_different_hashes() {
        AccountRecord r1 = record();
        AccountRecord r2 = record();
        r2.setAccountId("acc_00002");

        String hash1 = transformer.transform(r1).getRowHash();
        String hash2 = transformer.transform(r2).getRowHash();
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void null_status_defaults_to_pending() {
        AccountRecord r = record();
        r.setStatus(null);
        AccountRecord result = transformer.transform(r);
        assertThat(result.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void null_currency_defaults_to_usd() {
        AccountRecord r = record();
        r.setCurrency(null);
        AccountRecord result = transformer.transform(r);
        assertThat(result.getCurrency()).isEqualTo("USD");
    }
}
