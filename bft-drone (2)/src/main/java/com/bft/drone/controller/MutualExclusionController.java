package com.bft.drone.controller;

import com.bft.drone.service.MutualExclusionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints para el algoritmo de exclusión mutua (Ricart-Agrawala).
 * POST /me/request  → otro nodo solicita acceso
 * POST /me/reply    → otro nodo nos da OK
 */
@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class MutualExclusionController {

    private final MutualExclusionService mutualExclusionService;

    @PostMapping("/request")
    public ResponseEntity<Void> request(@RequestBody Map<String, Object> body) {
        int fromNodeId = (int) body.get("fromNodeId");
        int timestamp  = (int) body.get("timestamp");
        mutualExclusionService.receiveRequest(fromNodeId, timestamp);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reply")
    public ResponseEntity<Void> reply(@RequestBody Map<String, Object> body) {
        int fromNodeId = (int) body.get("fromNodeId");
        mutualExclusionService.receiveReply(fromNodeId);
        return ResponseEntity.ok().build();
    }
}