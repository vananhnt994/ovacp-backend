# OVACP Backend

Backend für OVACP als **Spring Boot / Spring Cloud Microservice-System** (Multi-Module Maven-Projekt).

## Überblick

Dieses Repository enthält mehrere Services, die zusammen über **Eureka (Service Discovery)** und ein **API Gateway** zusammenarbeiten. Ziel ist die Analyse und Visualisierung von Daten (z. B. Rossmann Store Sales).

- Kaggle-Datensatz: <https://www.kaggle.com/competitions/rossmann-store-sales/overview>

## Architektur

```
┌─────────────────────────────────────────────────────┐
│                   Frontend (Next.js/React)           │
│              vananhnt994/ovacp_frontend              │
└──────────────────────┬──────────────────────────────┘
                       │ REST/HTTP
                       ▼
┌─────────────────────────────────────────────────────┐
│              API Gateway (Spring Cloud Gateway)      │
│                    Port: 8080                        │
└────┬──────────────┬───────────────┬─────────────────┘
     │              │               │
     ▼              ▼               ▼
┌─────────┐  ┌──────────┐  ┌───────────────┐
│  File   │  │   AI /   │  │ Visualization │
│ Service │  │ Analysis │  │   Service     │
│  :8081  │  │ Service  │  │    :8083      │
│         │  │  :8082   │  │               │
└────┬────┘  └────┬─────┘  └───────┬───────┘
     │            │                │
     ▼            ▼                ▼
  MinIO/S3    OpenAI/           PostgreSQL/
  Storage     Gemini API        Redis Cache
```

## Module / Services (Maven)

| Modul | Beschreibung | Port |
|---|---|---|
| **eureka** | Eureka Server – Service Discovery | `8761` |
| **gateway** | Spring Cloud Gateway – API Entry Point | `8080` |
| **usermanamgement** | User Management Service | – |
| **file-management** | File Management Service (CSV Upload / Storage) | `8081` |
| **ai-analysis-service** | AI/Analysis Service (Gemini API, WebClient, Eureka LoadBalancer) | `8082` |
| *(Visualization Service)* | Visualization Service | `8083` |

## Tech Stack

- Java **21**
- Spring Boot **3.3.4**
- Spring Cloud **2023.0.3**
- Maven (Multi-Module)
- MinIO/S3 (File Storage), PostgreSQL mit GCP, Gemini API

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
./mvnw -pl eureka spring-boot:run
# → http://localhost:8761

# 2. API Gateway
./mvnw -pl gateway spring-boot:run
# → http://localhost:8080

# 3. File Management Service
./mvnw -pl file-management spring-boot:run
# → http://localhost:8081

# 4. AI Analysis Service
./mvnw -pl ai-analysis-service spring-boot:run
# → http://localhost:8082

# 5. User Management Service
./mvnw -pl usermanamgement spring-boot:run
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
