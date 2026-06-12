# flota-drones-tolerante-fallos

# BFT Drone Fleet — Spring Boot

## Arquitectura
- **Spring Boot**: backend de cada nodo dron (algoritmos BFT OM-1 y Bully)
- **Flask**: dashboard de monitoreo (siguiente fase)
- **Cluster**: 3 PCs Linux + 1 VM VirtualBox

## Nodos del cluster

| Nodo  | IP             | ID | Rol               |
|-------|----------------|----|-------------------|
| VM    | 192.168.0.104  | 1  | Leal              |
| PC2   | 192.168.0.102  | 2  | Leal              |
| PC3   | 192.168.0.103  | 3  | Leal              |
| PC1   | 192.168.0.101  | 4  | Coordinador/Traidor |

## Compilar el proyecto (en cualquier PC con Java 17 + Maven)

```bash
mvn clean package -DskipTests
```
Genera: `target/drone-1.0.0.jar`

## Distribuir el JAR a todos los nodos

```bash
# Desde PC1 (192.168.0.101)
scp target/drone-1.0.0.jar ubuntu@192.168.0.102:~/
scp target/drone-1.0.0.jar ubuntu@192.168.0.103:~/
scp target/drone-1.0.0.jar dron4@192.168.0.104:~/
```

## Arrancar cada nodo

### PC1 — Dron 1 (Leal, ID=1)
```bash
NODE_ID=1 \
NODE_IP=192.168.0.101 \
NODE_ROLE=LEAL \   
SERVER_PORT=8080 \
java -jar drone-1.0.0.jar
```

### PC2 — Dron 2 (Leal, ID=2)
```bash
NODE_ID=2 \
NODE_IP=192.168.0.102 \
NODE_ROLE=loyal \
SERVER_PORT=8080 \
java -jar drone-1.0.0.jar
```

### PC3 — Dron 3 (Leal, ID=3)
```bash
NODE_ID=3 \
NODE_IP=192.168.0.103 \
NODE_ROLE=loyal \
SERVER_PORT=8080 \
java -jar drone-1.0.0.jar
```

### VM — Dron 4 (Coordinador inicial + Traidor, ID=4)
```bash
NODE_ID=4 \
NODE_IP=192.168.0.104 \
NODE_ROLE=traitor \
SERVER_PORT=8080 \
java -jar drone-1.0.0.jar
```

## Instalar Java 17 en cada nodo (si no está instalado)

```bash
sudo apt update
sudo apt install openjdk-17-jdk -y
java -version
```

## Endpoints disponibles en cada nodo

| Método | URL                | Descripción                        |
|--------|--------------------|------------------------------------|
| GET    | /health            | Verificar que el nodo está vivo    |
| GET    | /status            | Estado completo del nodo           |
| POST   | /bft/propose       | Fase 1 BFT: propuesta del coord.   |
| POST   | /bft/relay         | Fase 2 BFT: relay entre tenientes  |
| POST   | /bully/election    | Mensaje ELECTION                   |
| POST   | /bully/ok          | Mensaje OK                         |
| POST   | /bully/coordinator | Anuncio nuevo coordinador          |
| POST   | /bully/heartbeat   | Heartbeat del coordinador          |
| WS     | /ws                | WebSocket para el dashboard Flask  |

## Verificar que todos los nodos están corriendo

```bash
curl http://192.168.0.101:8080/health
curl http://192.168.0.102:8080/health
curl http://192.168.0.103:8080/health
curl http://192.168.0.104:8080/health
```

## Simular caída del coordinador (VM)

Para probar el algoritmo Bully, simplemente apaga la VM o detén el proceso:
```bash
# En la VM
sudo poweroff
```
El nodo ID=3 (PC3) detectará la caída en ~7 segundos e iniciará la elección.
Puedes ver el proceso en los logs de PC3 o via el dashboard Flask.

## Estructura del proyecto

```
bft-drone/
├── pom.xml
└── src/main/java/com/bft/drone/
    ├── DroneApplication.java
    ├── config/
    │   ├── AppConfig.java        # RestTemplate bean
    │   ├── BullyConfig.java      # Propiedades Bully
    │   ├── ClusterConfig.java    # Topologia del cluster
    │   ├── NodeConfig.java       # Config del nodo actual
    │   └── WebSocketConfig.java  # STOMP WebSocket
    ├── controller/
    │   ├── BftController.java    # /bft/propose, /bft/relay
    │   ├── BullyController.java  # /bully/*
    │   └── StatusController.java # /status, /health
    ├── model/
    │   ├── Messages.java         # DTOs de mensajes
    │   └── NodeState.java        # Estado compartido del nodo
    ├── service/
    │   ├── BftService.java       # Algoritmo OM(1)
    │   └── BullyService.java     # Algoritmo Bully
    └── websocket/
        └── EventPublisher.java   # Publicador WebSocket
```
