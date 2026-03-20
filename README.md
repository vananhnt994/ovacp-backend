# OCVAP Backend

Backend für OCVAP als **Spring Boot / Spring Cloud Microservice-System** (Multi-Module Maven-Projekt).

## Überblick

Dieses Repository enthält mehrere Services, die zusammen über **Eureka (Service Discovery)** und ein **API Gateway** zusammenarbeiten. Ziel ist die Analyse und Visualisierung von Daten (z. B. Rossmann Store Sales).

- Kaggle-Datensatz: <https://www.kaggle.com/competitions/rossmann-store-sales/overview>

## Architektur

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        FRONTEND (React/TypeScript)                          │
│              vananhnt994/ovacp_frontend                                     │
│         CSV Upload  │  Chat/Prompt Input  │  Chart Visualisierung           │
└──────────────────────────────┬──────────────────────────────────────────────┘
                               │ HTTP/REST
                               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                      API GATEWAY (Spring Cloud Gateway)                     │
│                              Port: 8080                                     │
│              (Routing │ JWT Auth Filter │ Rate Limiting │ CORS)             │
└────────┬──────────────┬──────────────┬───────────────┬────────────────────-─┘
         │              │              │               │
         │    ┌─────────────────────────────────┐      │
         │    │        Eureka Server            │      │
         │    │        (Service Discovery)      │      │
         │    │  - Service Registry             │      │
         │    │  - Health Monitoring            │      │
         │    │  - Dynamic Routing              │      │
         │    └──────────────┬──────────────────┘      │
         │           alle Services registrieren sich   │
         │           beim Start bei Eureka             │
         │              │              │               │
         ▼              ▼              ▼               ▼
┌─────────────┐  ┌────────────┐  ┌──────────────┐  ┌────────────────┐
│    User     │  │   File     │  │  AI/Analysis │  │  Data Chart    │
│ Management  │  │  Service   │  │   Service    │  │   Service      │
│  Service    │  │   :8081    │  │    :8082     │  │    :8083       │
│   :8084     │  │            │  │              │  │                │
│             │  │ - CSV      │  │ - Prompt     │  │ - Bereitet     │
│ - Register  │  │   parsen   │  │   empfangen  │  │   Daten für    │
│ - Login     │  │ - Daten    │  │ - Gemini API │  │   Charts auf   │
│ - JWT Auth  │  │   im       │  │   aufrufen   │  │ - Gibt JSON    │
│ - Profile   │  │   Memory/  │  │ - Antwort    │  │   zurück       │
│             │  │   Session  │  │   parsen &   │  │   (Labels,     │
│             │  │   cachen   │  │   struktur.  │  │   Datasets,    │
│             │  │            │  │              │  │   ChartType)   │
└──────┬──────┘  └─────┬──────┘  └──────┬───────┘  └───────┬────────┘
       │               │                │                   │
       ▼               │                ▼                   │
┌─────────────┐        │         ┌─────────────────┐        │
│  Cloud SQL  │        │         │   Gemini API    │        │
│ (PostgreSQL │        │         │  (Google AI)    │        │
│  on Google  │        │         └─────────────────┘        │
│   Cloud)    │        │                                    │
│             │        ▼                                    │
│ - Users     │  ┌─────────────────────┐                    │
│ - Sessions  │  │  In-Memory / Session│◄───────────────────┘
│ - Roles     │  │  (temporärer        │  Chart Service liest
└─────────────┘  │   CSV-Speicher,     │  geparste CSV-Daten
                 │   kein persistenter │  aus dem Session-Cache
                 │   Speicher nötig)   │
                 └─────────────────────┘
```

## Module / Services (Maven)

| Modul | Beschreibung | Port |
|---|---|---|
| **eureka** | Eureka Server – Service Discovery | `8761` |
| **gateway** | Spring Cloud Gateway – API Entry Point | `8080` |
| **usermanamgement** | User Management Service | 8081 |
| **file-management** | File Management Service (CSV Upload / Storage) | `8082` |
| **ai-analysis-service** | AI/Analysis Service (Gemini API, WebClient, Eureka LoadBalancer) | `8083` |
| **Data-chart-service** | Visualization Service | `8084` |

## Tech Stack

- Java **21**
- Spring Boot **3.3.4**
- Spring Cloud **2023.0.3**
- Maven (Multi-Module)
- File Storage, PostgreSQL mit GCP, Gemini API

## Voraussetzungen

- Java 21 (JDK)
- Maven (oder Maven Wrapper `./mvnw`)
- Optional: Docker (für DB, Redis, MinIO)

## Lokales Setup (Quickstart)

### 1) Projekt bauen

```bash
./mvnw clean install
```

### 2) Services starten (empfohlene Reihenfolge)

```bash
# 1. Eureka Server (Service Discovery)
./mvn -pl eureka spring-boot:run
# → http://localhost:8761

# 2. API Gateway
./mvn -pl gateway spring-boot:run
# → http://localhost:8080

# 3. File Management Service
./mvn -pl file-management spring-boot:run
# → http://localhost:8082

# 4. AI Analysis Service
./mvn -pl ai-analysis-service spring-boot:run
# → http://localhost:8083

# 5. User Management Service
./mvn -pl usermanamgement spring-boot:run
# → http://localhost:8081

# 6. Data Chart Service
./mvn -pl usermanamgement spring-boot:run
# → http://localhost:8081
```

## Konfiguration

Jeder Service hat eigene Konfiguration unter:

```
<modul>/src/main/resources/application.properties
```

Wichtige Einstellungen:

- `eureka.client.service-url.defaultZone` – Eureka-Adresse (`http://localhost:8761/eureka`)
- Upload-Limits in `file-management`: max file size `50 MB`, max request size `200 MB`
- Upload-Verzeichnis: `./uploads`

## Troubleshooting

- **Service registriert sich nicht in Eureka** – Prüfe ob Eureka läuft (`http://localhost:8761`) und die `defaultZone`-URL korrekt ist.
- **Upload-Fehler (große Dateien)** – Limits sind im `file-management`-Modul bereits erweitert (multipart + Tomcat swallow size).
