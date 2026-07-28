package com.bulkflow.validation;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks record keys seen within a single batch to detect intra-batch duplicates.
 * Must be reset via reset() before each job execution (called from @BeforeStep).
 */
@Component
public class DuplicateDetector {

    private final Set<String> seenKeys = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public boolean isDuplicate(String key) {
        return !seenKeys.add(key);
    }

    public void reset() {
        seenKeys.clear();
    }

    public int size() {
        return seenKeys.size();
    }
}
