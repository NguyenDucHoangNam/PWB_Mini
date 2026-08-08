package com.pwb.iam.api.controller;

import com.pwb.infra.search.SearchReindexService;
import com.pwb.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Operator controls for the search indices.
 *
 * <p>Lives in IAM because that is where the admin surface and its {@code ADMIN} guard already are; the
 * service behind it belongs to no module in particular and discovers whichever indices are registered.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/search")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSearchController {

    private final SearchReindexService reindexService;

    @GetMapping("/indices")
    public ResponseEntity<ApiResponse<List<String>>> listIndices() {
        return ResponseEntity.ok(ApiResponse.success(reindexService.availableIndices()));
    }

    /**
     * Rebuilds one index from Postgres. Needed after a first deployment, when the tables already hold rows
     * that no write event will ever fire for, and as the way back from any drift.
     *
     * <p>Runs inline rather than in the background: it is an operator action on a small dataset, and a
     * response that says how many documents landed is more useful than a job id nobody follows up.
     */
    @PostMapping("/reindex")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reindex(@RequestParam String index) {
        int indexed = reindexService.reindex(index);
        log.info("SEARCH.reindex requested by admin: index={} documents={}", index, indexed);
        return ResponseEntity.ok(ApiResponse.success(Map.of("index", index, "documents", indexed)));
    }
}
