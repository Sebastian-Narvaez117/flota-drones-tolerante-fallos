package com.bft.drone.controller;

import com.bft.drone.config.NodeConfig;
import com.bft.drone.model.NodeState;
import com.bft.drone.service.CristianService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/cristian")
@RequiredArgsConstructor
public class CristianController {

    private final NodeConfig nodeConfig;
    private final NodeState nodeState;
    private final CristianService cristianService;

    @GetMapping("/time")
    public ResponseEntity<Map<String, Object>> getServerTime() {
        return ResponseEntity.ok(Map.of(
                "serverTime",    System.currentTimeMillis(),
                "nodeId",        nodeConfig.getId(),
                "isCoordinator", nodeConfig.getId() == nodeState.getCoordinatorId()
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSyncStatus() {
        long lastRtt = cristianService.getLastRtt();
        long clockOffset = cristianService.getClockOffset();   // Asegúrate de tener este método en CristianService

        // HashMap permite null, Map.of no
        Map<String, Object> status = new HashMap<>();
        status.put("nodeId",          nodeConfig.getId());
        status.put("synchronizedTime", cristianService.getSynchronizedTime());
        status.put("systemTime",       System.currentTimeMillis());
        status.put("coordinatorId",    nodeState.getCoordinatorId());
        status.put("lastRtt",          lastRtt >= 0 ? lastRtt : null);   // ahora null es válido
        status.put("clockOffset",      clockOffset);

        return ResponseEntity.ok(status);
    }
}