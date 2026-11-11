package com.acme.apigatewaycafelab.fallback;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/iam")
    public Mono<ResponseEntity<?>> iamFallback() {
        return fallback("iam-cafelab");
    }

    @RequestMapping("/procedures")
    public Mono<ResponseEntity<?>> proceduresFallback() {
        return fallback("Procedures-CafeLab");
    }

    @RequestMapping("/management")
    public Mono<ResponseEntity<?>> managementFallback(ServerWebExchange exchange) {
        Object origUrl = exchange.getAttribute(org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
        Object reqUrl = exchange.getAttribute(org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        String fullInfo = String.valueOf(origUrl) + " " + String.valueOf(reqUrl);

        Map<String, Object> singleLot = Map.of(
                "id", 1,
                "coffeeLotId", 1,
                "userId", 5,
                "lotName", "Lote 1 - Geisha Especial",
                "coffeeType", "Geisha",
                "status", "active",
                "origin", "Cajamarca",
                "weight", 100
        );

        if (fullInfo.matches(".*coffee-lots/\\d+.*")) {
            return Mono.just(ResponseEntity.ok(singleLot));
        }

        if (fullInfo.contains("coffee-lots") || fullInfo.contains("/fallback/management")) {
            return Mono.just(ResponseEntity.ok(List.of(singleLot)));
        }
        return fallback("Management-CafeLab");
    }

    @RequestMapping("/iot-monitoring")
    public Mono<ResponseEntity<?>> iotFallback() {
        return fallback("iotmonitoring");
    }

    @RequestMapping("/costing")
    public Mono<ResponseEntity<?>> costingFallback() {
        return fallback("costing-cafelab");
    }

    @RequestMapping("/calibrations")
    public Mono<ResponseEntity<?>> calibrationsFallback() {
        return fallback("Calibrations-CafeLab");
    }

    @RequestMapping("/document-autofill")
    public Mono<ResponseEntity<?>> documentAutofillFallback() {
        return fallback("document-autofill-cafelab");
    }

    private Mono<ResponseEntity<?>> fallback(String service) {
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", "503",
                        "service", service,
                        "message", "El servicio no está disponible temporalmente. Intente más tarde."
                )));
    }
}
