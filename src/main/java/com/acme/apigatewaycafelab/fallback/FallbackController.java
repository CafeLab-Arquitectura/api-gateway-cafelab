package com.acme.apigatewaycafelab.fallback;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/iam")
    public Mono<ResponseEntity<Map<String, String>>> iamFallback() {
        return fallback("iam-cafelab");
    }

    @RequestMapping("/procedures")
    public Mono<ResponseEntity<Map<String, String>>> proceduresFallback() {
        return fallback("Procedures-CafeLab");
    }

    @RequestMapping("/management")
    public Mono<ResponseEntity<Map<String, String>>> managementFallback() {
        return fallback("Management-CafeLab");
    }

    @RequestMapping("/iot-monitoring")
    public Mono<ResponseEntity<Map<String, String>>> iotFallback() {
        return fallback("iotmonitoring");
    }

    @RequestMapping("/costing")
    public Mono<ResponseEntity<Map<String, String>>> costingFallback() {
        return fallback("costing-cafelab");
    }

    private Mono<ResponseEntity<Map<String, String>>> fallback(String service) {
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", "503",
                        "service", service,
                        "message", "El servicio no está disponible temporalmente. Intente más tarde."
                )));
    }
}
