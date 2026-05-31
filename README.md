# 🏥 Patient Management System

A **production-grade microservices application** built with Java & Spring Boot that demonstrates modern backend engineering patterns — including **JWT authentication**, **gRPC**, **Apache Kafka**, **Protobuf**, **AWS CDK**, and **Docker**.

> Built by [@Vivek1035](https://github.com/Vivek1035)

---

## 📋 Table of Contents

- [Overview](#-overview)
- [Architecture](#-architecture)
- [Services](#-services)
- [Tech Stack](#-tech-stack)
- [Getting Started](#-getting-started)
- [API Reference](#-api-reference)
- [Inter-Service Communication](#-inter-service-communication)
- [Infrastructure (AWS CDK)](#-infrastructure-aws-cdk)
- [Running Tests](#-running-tests)
- [Project Structure](#-project-structure)

---

## 🌟 Overview

This system manages hospital patient records across **5 independent microservices**, each with a single responsibility. All external traffic enters through an **API Gateway** that enforces JWT-based authentication before routing requests downstream.

### Key Features

- ✅ **JWT Authentication** — stateless token-based auth (24-hour expiry, HMAC-SHA signed)
- ✅ **API Gateway** — single entry point with custom JWT validation filter
- ✅ **gRPC Communication** — synchronous RPC between Patient and Billing services using Protobuf
- ✅ **Event-Driven Architecture** — Kafka event streaming with Protobuf binary serialization
- ✅ **REST API + OpenAPI** — fully documented Swagger UI for all services
- ✅ **Bean Validation** — request validation with custom validation groups
- ✅ **Global Exception Handling** — structured JSON error responses
- ✅ **Docker Multi-Stage Builds** — optimized container images for all services
- ✅ **Infrastructure as Code** — complete AWS deployment stack using AWS CDK (Java)
- ✅ **Integration Tests** — end-to-end tests via REST Assured through the API Gateway

---

## 🏗️ Architecture

```
                        ┌─────────────────────────────────────────┐
                        │           API Gateway  :4004             │
   External Client ────►│  Spring Cloud Gateway                    │
   (REST Requests)      │  └── JwtValidationGatewayFilterFactory   │
                        └────────────┬──────────────┬─────────────┘
                                     │              │
                    ┌────────────────▼──┐    ┌──────▼─────────────┐
                    │  Auth Service      │    │  Patient Service    │
                    │  :4005             │    │  :4000             │
                    │  POST /login       │    │  GET  /patients    │
                    │  GET  /validate    │    │  POST /patients    │
                    │  JJWT + BCrypt     │    │  PUT  /patients/:id│
                    │  PostgreSQL        │    │  DELETE /patients  │
                    └────────────────────┘    └──────┬─────┬──────┘
                                                     │     │
                                         ┌───────────┘     └──────────────┐
                                         │  gRPC                          │  Kafka
                                         ▼                                ▼
                              ┌──────────────────────┐   ┌───────────────────────────┐
                              │  Billing Service       │   │  Analytics Service        │
                              │  :9001 (gRPC)          │   │  :4002                    │
                              │  Creates billing       │   │  Consumes 'patient' topic │
                              │  accounts              │   │  (PATIENT_CREATED events) │
                              └──────────────────────┘   └───────────────────────────┘
```

### Request Flow — Create Patient

```
Client
  │
  ├─► POST /auth/login → API Gateway → Auth Service → returns JWT token
  │
  └─► POST /api/patients [Bearer <token>]
        │
        ├─► API Gateway: JwtValidation filter
        │       └─► Calls GET /validate on Auth Service (verifies token)
        │
        ├─► Forwards to Patient Service
        │       ├─► Validate request body (Bean Validation)
        │       ├─► Check email uniqueness in PostgreSQL
        │       ├─► Save patient to PostgreSQL
        │       ├─► [gRPC] Call Billing Service → create billing account
        │       └─► [Kafka] Publish PATIENT_CREATED event → Analytics Service
        │
        └─► 200 OK { patient data }
```

---

## 🔧 Services

### 1. 🚪 API Gateway (`port 4004`)
The single front door for all client requests. Built with **Spring Cloud Gateway** (reactive / non-blocking).

| Route | Destination | Filters |
|---|---|---|
| `POST /auth/**` | auth-service:4005 | StripPrefix |
| `GET/POST/PUT/DELETE /api/patients/**` | patient-service:4000 | **JwtValidation** + StripPrefix |
| `GET /api-docs/patients` | patient-service:4000 | RewritePath → `/v3/api-docs` |
| `GET /api-docs/auth` | auth-service:4005 | RewritePath → `/v3/api-docs` |

The `JwtValidationGatewayFilterFactory` intercepts every request to protected routes and calls the Auth Service's `/validate` endpoint before forwarding.

---

### 2. 🔐 Auth Service (`port 4005`)
Handles user authentication and token management.

| Endpoint | Method | Description |
|---|---|---|
| `/login` | `POST` | Accepts `{email, password}`, returns `{token}` |
| `/validate` | `GET` | Validates `Authorization: Bearer <token>` header |

**How it works:**
- Passwords stored as **BCrypt** hashes in PostgreSQL
- JWT tokens signed with HMAC-SHA using a Base64-encoded secret key
- Tokens contain: `email` (subject), `role` claim, 24-hour expiry
- Spring Security is used only for `BCryptPasswordEncoder` — all routes are `permitAll` since auth is handled at the Gateway

---

### 3. 🧑‍⚕️ Patient Service (`port 4000`)
The core service. Manages patient CRUD and orchestrates downstream calls.

| Endpoint | Method | Auth | Description |
|---|---|---|---|
| `/patients` | `GET` | ✅ | List all patients |
| `/patients` | `POST` | ✅ | Create patient + billing account + publish event |
| `/patients/{id}` | `PUT` | ✅ | Update patient details |
| `/patients/{id}` | `DELETE` | ✅ | Remove patient |

**Patient Schema:**

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Auto-generated |
| `name` | String | Max 100 chars, required |
| `email` | String | Unique, validated email format |
| `address` | String | Required |
| `dateOfBirth` | LocalDate | Required |
| `registeredDate` | LocalDate | Required on create only |

---

### 4. 💳 Billing Service (`port 4001 HTTP / 9001 gRPC`)
A **gRPC server** that creates billing accounts when new patients are registered. Called synchronously by the Patient Service.

**Proto contract:**
```protobuf
service BillingService {
  rpc CreateBillingAccount (BillingRequest) returns (BillingResponse);
}
message BillingRequest  { string patientId = 1; string name = 2; string email = 3; }
message BillingResponse { string accountId = 1; string status = 2;}
```

---

### 5. 📊 Analytics Service (`port 4002`)
An event-driven service that subscribes to the Kafka `patient` topic and processes `PATIENT_CREATED` events. Designed to be extended with analytics business logic.

**Kafka message format (Protobuf):**
```protobuf
message PatientEvent {
  string patientId   = 1;
  string name        = 2;
  string email       = 3;
  string event_type  = 4;   // "PATIENT_CREATED"
}
```

---

## 🛠️ Tech Stack

| Category | Technology | Version |
|---|---|---|
| Language | Java | 21 |
| Framework | Spring Boot | 3.4.x / 4.0.x |
| API Gateway | Spring Cloud Gateway | 2024.0.0 |
| Security | Spring Security + JJWT | 0.12.6 |
| ORM | Spring Data JPA / Hibernate | — |
| Database | PostgreSQL | 17 (RDS) |
| Local DB | H2 (in-memory) | — |
| RPC | gRPC | 1.69.0 |
| Serialization | Protocol Buffers (proto3) | 4.29.1 |
| Messaging | Apache Kafka | — |
| Spring Kafka | spring-kafka | 3.3.0 |
| API Docs | SpringDoc OpenAPI | 2.6 / 2.7 |
| Containerization | Docker (multi-stage) | — |
| IaC | AWS CDK (Java) | — |
| Cloud Compute | AWS ECS Fargate | — |
| Cloud Messaging | AWS MSK (Managed Kafka) | 2.8.0 |
| Cloud DB | AWS RDS PostgreSQL | 17.2 |
| Testing | REST Assured + JUnit 5 | — |
| Build | Apache Maven | 3.9.x |

---

## 🚀 Getting Started

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker + Docker Compose
- PostgreSQL (or use the H2 in-memory option for local dev)
- Apache Kafka (or AWS MSK in production)

### Running Locally (Dev Mode)

**Step 1 — Start infrastructure (Kafka + PostgreSQL):**
```bash
# You'll need Kafka and PostgreSQL running locally.
# Easiest way is via Docker:
docker run -d --name postgres -e POSTGRES_PASSWORD=admin -p 5432:5432 postgres:17
docker run -d --name kafka -p 9092:9092 apache/kafka:latest
```

**Step 2 — Configure Patient Service for local DB:**
In `patient-service/src/main/resources/application.yaml`, uncomment the H2 section:
```yaml
spring:
  h2:
    console:
      path: /h2-console
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: admin_viewer
    password: admin
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: update
```

**Step 3 — Build and run each service:**
```bash
# Build any service (example: patient-service)
cd patient-service
./mvnw clean package -DskipTests
java -jar target/patient-service-0.0.1-SNAPSHOT.jar

# Repeat for auth-service, billing-service, analytics-service, api-gateway
```

### Running with Docker

**Step 1 — Build Docker images:**
```bash
# Build each service image (from each service directory)
cd patient-service && docker build -t patient-service .
cd ../auth-service && docker build -t auth-service .
cd ../billing-service && docker build -t billing-service .
cd ../analytics-service && docker build -t analytics-service .
cd ../api-gateway && docker build -t api-gateway .
```

**Step 2 — Run containers:**
```bash
# Example — start patient-service
docker run -d \
  -p 4000:4000 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/patient-service-db \
  -e SPRING_DATASOURCE_USERNAME=admin_user \
  -e SPRING_DATASOURCE_PASSWORD=admin \
  -e BILLING_SERVICE_ADDRESS=host.docker.internal \
  -e BILLING_SERVICE_GRPC_PORT=9001 \
  -e SPRING_KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092 \
  patient-service
```

### Port Reference

```
4004 — API Gateway    (public entry point — use this for all requests)
4005 — Auth Service
4000 — Patient Service
4001 — Billing Service (HTTP)
9001 — Billing Service (gRPC)
4002 — Analytics Service
```

---

## 📡 API Reference

> All API calls go through the **API Gateway on port 4004**.

### Authentication

#### Login
```http
POST http://localhost:4004/auth/login
Content-Type: application/json

{
  "email": "testuser@test.com",
  "password": "password123"
}
```
**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

#### Validate Token
```http
GET http://localhost:4004/auth/validate
Authorization: Bearer <token>
```
**Response:** `200 OK` or `401 Unauthorized`

---

### Patients *(requires JWT)*

#### Get All Patients
```http
GET http://localhost:4004/api/patients
Authorization: Bearer <token>
```

#### Create Patient
```http
POST http://localhost:4004/api/patients
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "John Doe",
  "email": "john.doe@example.com",
  "address": "123 Main Street, Springfield",
  "dateOfBirth": "1990-05-15",
  "registeredDate": "2024-01-01"
}
```

#### Update Patient
```http
PUT http://localhost:4004/api/patients/{id}
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "John Doe Updated",
  "email": "john.updated@example.com",
  "address": "456 New Street",
  "dateOfBirth": "1990-05-15"
}
```

#### Delete Patient
```http
DELETE http://localhost:4004/api/patients/{id}
Authorization: Bearer <token>
```

---

### Swagger / OpenAPI Docs

| Service | Swagger UI | OpenAPI JSON |
|---|---|---|
| Patient Service | `http://localhost:4000/swagger-ui.html` | `http://localhost:4004/api-docs/patients` |
| Auth Service | `http://localhost:4005/swagger-ui.html` | `http://localhost:4004/api-docs/auth` |

---

## 🔗 Inter-Service Communication

This project uses **two communication patterns** depending on the use case:

### gRPC — Synchronous (Patient → Billing)

Used when the Patient Service **needs an immediate response** from Billing (e.g., billing account ID).

```
Patient Service                          Billing Service
      │                                        │
      │── gRPC CreateBillingAccount(req) ─────►│
      │                                        │── Business logic
      │◄─ BillingResponse{accountId, status} ──│
      │                                        │
```

- Uses **blocking stub** — patient service waits for the response
- Protobuf serialized — compact binary format over the wire
- Connection configured via `billing.service.address` + `billing.service.grpc.port` properties

### Kafka — Asynchronous (Patient → Analytics)

Used when the Patient Service **does not need a response** — analytics processing happens in the background.

```
Patient Service         Kafka 'patient' topic       Analytics Service
      │                          │                          │
      │── byte[] (Protobuf) ────►│                          │
      │                          │── deliver event ────────►│
      │   (continues immediately)│                          │── parseFrom(bytes)
      │                          │                          │── log + analytics
```

- Serialized as Protobuf `byte[]` — smaller than JSON
- Consumer group ID: `analytics-service`
- Fire-and-forget: patient creation succeeds even if analytics is temporarily down

---

## ☁️ Infrastructure (AWS CDK)

The [`infrastructure/`](./infrastructure) module defines the **entire AWS deployment** as Java code using AWS CDK. Run `cdk deploy` to provision everything.

### What Gets Created

| Resource | Details |
|---|---|
| **VPC** | `PatientManagementVPC` — 2 Availability Zones |
| **ECS Cluster** | `patient-management.local` (CloudMap service discovery) |
| **Fargate Services** | One per microservice (256 CPU / 512MB RAM each) |
| **RDS PostgreSQL 17** | Separate DB instances for auth-service and patient-service (t2.micro / 20GB) |
| **AWS MSK** | Kafka 2.8.0 cluster (kafka.m5.xlarge, 1 broker) |
| **Application Load Balancer** | Fronts the API Gateway Fargate service |
| **CloudWatch Log Groups** | 1-day retention logs for all services |
| **Health Checks** | TCP health checks on RDS before dependent services start |

### Service Startup Order (Dependencies)

```
AuthServiceDB ──► AuthDB HealthCheck ──► auth-service
                                                        │
PatientServiceDB ──► PatientDB HealthCheck ──┐          │
billing-service ─────────────────────────────┼──► patient-service
MSK Kafka Cluster ───────────────────────────┘

MSK Kafka Cluster ──► analytics-service
```

### Deploy

```bash
cd infrastructure
mvn clean package
npx cdk deploy
```

---

## 🧪 Running Tests

### Integration Tests

Integration tests run **end-to-end** through the API Gateway on port 4004. The full system must be running.

```bash
cd integration-tests
mvn test
```

**What is tested:**

| Test | Description |
|---|---|
| `AuthIntegrationTest#shouldReturnOKWithValidToken` | `POST /auth/login` returns 200 + non-null JWT token |
| `AuthIntegrationTest#shouldReturnUnauthorizedOnInvalidLogin` | Wrong credentials return 401 |
| `PatientIntegrationTest#shouldReturnPatientsWithValidToken` | Login → use token → `GET /api/patients` returns 200 |

### Unit Tests

Each service has its own unit tests:
```bash
cd patient-service && mvn test
cd auth-service && mvn test
```

---

## 📂 Project Structure

```
PatientManagementSystem/
│
├── api-gateway/                          # Spring Cloud Gateway
│   └── src/main/
│       ├── java/.../filter/
│       │   └── JwtValidationGatewayFilterFactory.java
│       └── resources/
│           ├── application.yml           # Dev routes (uses service names)
│           └── application-prod.yml      # Prod routes (uses host.docker.internal)
│
├── auth-service/                         # JWT Authentication Service
│   └── src/main/java/.../authservice/
│       ├── controller/AuthController.java
│       ├── service/AuthService.java
│       ├── service/UserService.java
│       ├── model/User.java
│       ├── util/JwtUtil.java
│       ├── config/SecurityConfig.java
│       └── repository/UserRepository.java
│
├── patient-service/                      # Core Patient CRUD Service
│   └── src/main/
│       ├── java/.../patientservice/
│       │   ├── controller/PatientController.java
│       │   ├── service/PatientService.java
│       │   ├── model/Patient.java
│       │   ├── dto/PatientRequestDTO.java
│       │   ├── dto/PatientResponseDTO.java
│       │   ├── mapper/PatientMapper.java
│       │   ├── grpc/BillingServiceGrpcClient.java
│       │   ├── kafka/KafkaProducer.java
│       │   └── exception/GlobalExceptionHandler.java
│       └── proto/
│           ├── billing_service.proto     # gRPC contract (client side)
│           └── patient_event.proto       # Kafka message schema
│
├── billing-service/                      # gRPC Billing Server
│   └── src/main/
│       ├── java/.../billingservice/grpc/BillingServiceGrpc.java
│       └── proto/billing_service.proto   # gRPC contract (server side)
│
├── analytics-service/                    # Kafka Event Consumer
│   └── src/main/
│       ├── java/.../analyticsservice/kafka/KafkaConsumer.java
│       └── proto/patient_event.proto     # Kafka message schema
│
├── infrastructure/                       # AWS CDK Infrastructure (Java)
│   └── src/main/java/org.vivek.stack/
│       └── LocalStack.java               # Full AWS stack definition
│
├── integration-tests/                    # End-to-end REST Assured tests
│   └── src/test/java/
│       ├── AuthIntegrationTest.java
│       └── PatientIntegrationTest.java
│
├── api-requests/                         # HTTP request files (manual testing)
└── grpc-requests/                        # gRPC request files (manual testing)
```

---

## 🔒 Environment Variables

| Service | Variable | Description |
|---|---|---|
| auth-service | `JWT_SECRET` | Base64-encoded HMAC-SHA secret key |
| patient-service | `BILLING_SERVICE_ADDRESS` | Hostname of billing-service |
| patient-service | `BILLING_SERVICE_GRPC_PORT` | gRPC port of billing-service (default: `9001`) |
| patient-service | `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka broker addresses |
| analytics-service | `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka broker addresses |
| api-gateway | `AUTH_SERVICE_URL` | Full URL of auth-service (e.g. `http://auth-service:4005`) |
| All DB services | `SPRING_DATASOURCE_URL` | JDBC connection string |
| All DB services | `SPRING_DATASOURCE_USERNAME` | DB username |
| All DB services | `SPRING_DATASOURCE_PASSWORD` | DB password |

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit your changes: `git commit -m 'Add some feature'`
4. Push to the branch: `git push origin feature/your-feature`
5. Open a Pull Request

---

## 📄 License

This project is open-source and available under the [MIT License](LICENSE).

---

<div align="center">

**Built with ❤️ using Java, Spring Boot, gRPC, Kafka & AWS**

⭐ Star this repo if you found it helpful!

</div>
