package com.bulkflow.batch;

import com.bulkflow.model.AccountRecord;
import com.bulkflow.model.FailureReason;
import com.bulkflow.validation.ValidationException;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.validation.BindException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class AccountFieldSetMapper implements FieldSetMapper<AccountRecord> {

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    };

    @Override
    public AccountRecord mapFieldSet(FieldSet fieldSet) throws BindException {
        // Build raw line for dead-letter preservation before any parsing
        String rawLine = buildRawLine(fieldSet);

        AccountRecord record = AccountRecord.builder()
                .accountId(trim(fieldSet.readString("accountId")))
                .email(trim(fieldSet.readString("email")))
                .firstName(trim(fieldSet.readString("firstName")))
                .lastName(trim(fieldSet.readString("lastName")))
                .status(trim(fieldSet.readString("status")))
                .phone(trim(fieldSet.readString("phone")))
                .currency(trim(fieldSet.readString("currency")))
                .rawLine(rawLine)
                .build();

        String dobStr = trim(fieldSet.readString("dateOfBirth"));
        if (dobStr != null && !dobStr.isBlank()) {
            record.setDateOfBirth(parseDate(dobStr, rawLine));
        }

        String clStr = trim(fieldSet.readString("creditLimit"));
        if (clStr != null && !clStr.isBlank()) {
            try {
                record.setCreditLimit(new BigDecimal(clStr));
            } catch (NumberFormatException e) {
                throw new ValidationException(FailureReason.INVALID_NUMERIC, "credit_limit",
                        "Cannot parse credit_limit: " + clStr, rawLine);
            }
        }

        return record;
    }

    private LocalDate parseDate(String s, String raw) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(s, fmt);
            } catch (DateTimeParseException ignored) {
            }
        }
        throw new ValidationException(FailureReason.INVALID_DATE_FORMAT, "date_of_birth",
                "Cannot parse date: " + s, raw);
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }

    private String buildRawLine(FieldSet fieldSet) {
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < fieldSet.getFieldCount(); i++) {
                if (i > 0) sb.append(",");
                sb.append(fieldSet.readString(i));
            }
            return sb.toString();
        } catch (Exception e) {
            return "<unparseable>";
        }
    }
}
