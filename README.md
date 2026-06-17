# 🚁 BFT Drone Fleet – Sistema Distribuido Tolerante a Fallos Bizantinos

## 📖 Descripción

**BFT Drone Fleet** es un sistema distribuido que simula una flota de drones capaces de alcanzar consenso aun en presencia de nodos maliciosos o defectuosos (fallos bizantinos).

El proyecto integra varios algoritmos clásicos de sistemas distribuidos:

* **OM(1) (Oral Messages)** de Lamport para tolerancia a fallos bizantinos.
* **Algoritmo Bully** para elección dinámica de coordinador.
* **Algoritmo de Cristian** para sincronización de relojes.
* **Ricart–Agrawala** para exclusión mutua distribuida.

El sistema está compuesto por cuatro drones ejecutándose como procesos independientes sobre distintas máquinas de la red y un dashboard web que permite monitorear en tiempo real el estado del clúster, las elecciones de coordinador, los consensos alcanzados y los eventos distribuidos.

---

# 🎯 Objetivos

* Simular una flota de drones distribuida.
* Implementar consenso tolerante a fallos bizantinos.
* Demostrar recuperación automática ante fallos del coordinador.
* Visualizar eventos distribuidos en tiempo real.
* Integrar múltiples algoritmos clásicos de sistemas distribuidos en una única aplicación.

---

# 🏗️ Arquitectura General

```text
                    ┌─────────────────────┐
                    │   Dashboard Flask   │
                    │  HTML/CSS/JS/STOMP  │
                    └──────────┬──────────┘
                               │
                     REST + WebSocket
                               │
      ┌─────────────┬──────────┼──────────┬─────────────┐
      │             │          │          │             │
 ┌────▼────┐  ┌────▼────┐ ┌────▼────┐ ┌────▼────┐
 │ Drone 1 │  │ Drone 2 │ │ Drone 3 │ │ Drone 4 │
 │ Spring  │  │ Spring  │ │ Spring  │ │ Spring  │
 │ Boot    │  │ Boot    │ │ Boot    │ │ Boot    │
 └─────────┘  └─────────┘ └─────────┘ └─────────┘

      Comunicación entre drones mediante HTTP/REST
```

---

# 🧠 Algoritmos Implementados

## 1. Consenso Bizantino – OM(1)

El algoritmo OM(1) permite alcanzar consenso incluso cuando existe un nodo traidor dentro del sistema.

### Escenario 1: Coordinador Leal y Teniente Traidor

* El coordinador envía la misma orden a todos los drones.
* El dron traidor altera los mensajes durante el reenvío.
* Los drones leales aplican mayoría.
* Se conserva el valor correcto.

### Escenario 2: Coordinador Traidor

* El coordinador envía órdenes distintas a cada teniente.
* Los tenientes intercambian los valores recibidos.
* Todos los drones leales aplican la misma regla de decisión.
* Se cumple la propiedad de acuerdo (IC1).

### Condición de tolerancia

Para OM(1):

```text
n ≥ 3m + 1
```

donde:

* n = número total de drones
* m = número máximo de traidores

En este proyecto:

```text
n = 4
m = 1

4 ≥ 4 ✓
```

---

## 2. Elección de Líder – Algoritmo Bully

Cuando el coordinador deja de enviar latidos (heartbeats):

1. Los drones detectan el fallo.
2. Se inicia una elección.
3. El nodo con mayor identificador activo gana.
4. El nuevo coordinador anuncia su liderazgo.

---

## 3. Sincronización de Relojes – Algoritmo de Cristian

Permite reducir la deriva temporal entre los drones utilizando un servidor de tiempo distribuido.

Cada nodo:

1. Solicita la hora al servidor.
2. Calcula el retardo de red.
3. Ajusta su reloj local.

---

## 4. Exclusión Mutua – Ricart–Agrawala

Controla el acceso concurrente a recursos compartidos mediante intercambio de mensajes REQUEST y REPLY entre los nodos.

---

# 🖥️ Topología del Clúster

| Nodo | IP            | ID | Rol     |
| ---- | ------------- | -- | ------- |
| D1   | 192.168.0.104 | 1  | Leal    |
| D2   | 192.168.0.102 | 2  | Leal    |
| D3   | 192.168.0.103 | 3  | Leal    |
| D4   | 192.168.0.101 | 4  | Traidor |

El nodo con mayor ID es seleccionado inicialmente como coordinador mediante el algoritmo Bully.

---

# ⚙️ Tecnologías Utilizadas

## Backend

* Java 17
* Spring Boot
* Spring Web
* Spring WebSocket
* Maven
* Lombok

## Frontend

* Python 3
* Flask
* HTML5
* CSS3
* JavaScript
* Bootstrap 5
* STOMP
* SockJS

## Infraestructura

* VirtualBox
* Red local LAN
* 3 PCs físicas
* 1 Máquina Virtual

---

# 📂 Estructura del Proyecto

```text
flota-drones-tolerante-fallos/
│
├── backend/
│   ├── config/
│   ├── controller/
│   ├── model/
│   ├── service/
│   └── websocket/
│
├── dashboard/
│   ├── templates/
│   ├── static/
│   │   ├── css/
│   │   └── js/
│   └── app.py
│
└── README.md
```

---

# 🚀 Instalación

## Compilar Backend

```bash
cd backend
mvn clean package -DskipTests
```

Se generará:

```text
target/drone-1.0.0.jar
```

---

## Ejecutar los nodos

### D4 – Coordinador Traidor

```bash
NODE_ID=4 \
NODE_IP=192.168.0.101 \
NODE_ROLE=TRAITOR \
SERVER_PORT=8080 \
java -jar drone-1.0.0.jar
```

### D3 – Leal

```bash
NODE_ID=3 \
NODE_IP=192.168.0.103 \
NODE_ROLE=LOYAL \
SERVER_PORT=8080 \
java -jar drone-1.0.0.jar
```

### D2 – Leal

```bash
NODE_ID=2 \
NODE_IP=192.168.0.102 \
NODE_ROLE=LOYAL \
SERVER_PORT=8080 \
java -jar drone-1.0.0.jar
```

### D1 – Leal

```bash
NODE_ID=1 \
NODE_IP=192.168.0.104 \
NODE_ROLE=LOYAL \
SERVER_PORT=8080 \
java -jar drone-1.0.0.jar
```

---

# 🌐 Dashboard

Instalar dependencias:

```bash
cd dashboard
pip install flask requests
```

Ejecutar:

```bash
python app.py
```

Abrir:

```text
http://localhost:5000
```

---

# 📡 Endpoints Principales

| Método | Endpoint           | Descripción             |
| ------ | ------------------ | ----------------------- |
| GET    | /health            | Estado del nodo         |
| GET    | /status            | Estado completo         |
| POST   | /bft/propose       | Fase de propuesta       |
| POST   | /bft/relay         | Reenvío entre tenientes |
| POST   | /bully/election    | Inicio de elección      |
| POST   | /bully/coordinator | Anuncio coordinador     |
| POST   | /bully/heartbeat   | Latido del líder        |
| GET    | /cristian/time     | Hora del servidor       |
| GET    | /cristian/status   | Estado sincronización   |
| GET    | /me/status         | Estado Ricart-Agrawala  |
| WS     | /ws                | Eventos en tiempo real  |

---

# 🧪 Escenarios de Prueba

## Fallo del Coordinador

1. Detener el coordinador actual.
2. Esperar aproximadamente 7 segundos.
3. Los drones iniciarán una elección Bully.
4. El nodo activo con mayor ID será elegido coordinador.

## Coordinador Traidor

1. D4 envía trayectorias distintas.
2. Los tenientes intercambian mensajes.
3. Todos alcanzan el mismo consenso.
4. Se verifica IC(1).

## Teniente Traidor

1. Un coordinador leal envía la misma orden.
2. El teniente traidor modifica mensajes.
3. Los drones leales aplican mayoría.
4. Se preserva el valor correcto.

---

