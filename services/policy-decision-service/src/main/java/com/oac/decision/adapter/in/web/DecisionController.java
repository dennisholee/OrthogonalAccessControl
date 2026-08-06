package com.oac.decision.adapter.in.web;

import com.oac.decision.application.port.in.DecisionQueryUseCase;
import com.oac.decision.model.CheckPermissionRequest;
import com.oac.decision.model.CheckPermissionResponse;
import com.oac.decision.model.LookupResourcesRequest;
import com.oac.decision.model.LookupResourcesResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/v1/decisions")
public class DecisionController {

    private final DecisionQueryUseCase decisionQueryUseCase;

    public DecisionController(DecisionQueryUseCase decisionQueryUseCase) {
        this.decisionQueryUseCase = decisionQueryUseCase;
    }

    @PostMapping("/check-permission")
    public ResponseEntity<CheckPermissionResponse> checkPermission(@Valid @RequestBody CheckPermissionRequest request) {
        CheckPermissionResponse response = decisionQueryUseCase.checkPermission(request);

        HttpHeaders headers = new HttpHeaders();
        if (response.circuitBreakerState() != null) {
            headers.add("X-OAC-Circuit-Breaker", response.circuitBreakerState());
        }
        if (response.cacheStatus() != null) {
            headers.add("X-OAC-Cache", response.cacheStatus());
        }
        if (response.decisionCode() != null && response.decisionCode().startsWith("DECISION_FAIL_OPEN")) {
            headers.add("X-OAC-Fallback", "true");
        }

        return ResponseEntity.ok().headers(headers).body(response);
    }

    @PostMapping("/lookup-resources")
    public ResponseEntity<LookupResourcesResponse> lookupResources(@Valid @RequestBody LookupResourcesRequest request) {
        return ResponseEntity.ok(decisionQueryUseCase.lookupResources(request));
    }
}
