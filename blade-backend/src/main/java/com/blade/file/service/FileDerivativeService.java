package com.blade.file.service;

import com.blade.file.entity.FileStorage;
import org.springframework.core.io.Resource;

/**
 * Service for managing image derivatives (thumb / card).
 */
public interface FileDerivativeService {

    /**
     * Generate thumb and card derivatives for a newly uploaded IMAGE file.
     * <p>
     * Must be called within an active transaction.
     * Generation failures are recorded as FAILED status — they do NOT throw.
     */
    void generate(FileStorage file);

    /**
     * Load a derivative file as a Spring Resource.
     * Returns null if no READY derivative exists — caller should fall back to original.
     */
    Resource loadVariantResource(Long fileId, String variantType);

    /**
     * Backfill derivatives for existing IMAGE files in the current tenant.
     *
     * @param limit max files to process (capped at 500)
     * @return backfill result with processed / succeeded / failed / skipped counts
     */
    BackfillResult backfill(int limit);

    record BackfillResult(int processed, int succeeded, int failed, int skipped) {}
}
