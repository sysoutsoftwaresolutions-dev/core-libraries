# Core Libraries SDK

This repository module contains the foundational shared libraries and engine contracts used to build dynamic, multi-tenant SaaS services across the Educational ERP platform.

---

## 1. Directory & Modules Structure

`core-libraries` is managed as a Maven reactor project composed of four sub-modules:

```text
core-libraries/
├── pom.xml                      # Parent reactor POM
├── core-common/                 # Shared security, exception mapping, and tenant propagation
│   └── src/main/java/com/core/common/
│       ├── aspect/              # WorkflowSecurityAspect, ControllerErrorHandlerAspect
│       ├── client/              # WebClientHelper with automatic headers propagation
│       ├── context/             # TenantContext, TenantWebInterceptor, TenantAwareTaskExecutor
│       └── security/            # JwtTokenParser, SecuredWorkflow annotation
├── core-utils/                  # Base utilities and helper methods
├── workflow-engine/             # State machine workflow processor
│   └── src/main/java/com/core/workflow/
│       ├── aspect/              # TenantModuleValidationAspect
│       ├── database/            # TenantRoutingMongoDatabaseFactory, TenantDatabaseResolver
│       ├── engine/              # StateMachineEngine (uses Java 21 virtual threads)
│       └── registry/            # TenantModuleRegistry (metadata feature flags)
└── workflow-rest/               # REST/GraphQL endpoints and Kafka consumer triggers
```

---

## 2. Key Capabilities

### A. Context Propagation & Async Threading
*   **`TenantContext`**: ThreadLocal container holding the active `tenantId` (e.g. `school-a`).
*   **`TenantWebInterceptor`**: Intercepts REST calls, reads the `X-Tenant-ID` header, and registers it to the thread context.
*   **`TenantAwareTaskExecutor`**: Wraps Spring's executor to duplicate parent `TenantContext` elements into spawned virtual or asynchronous worker threads, cleaning up on execution complete.

### B. Offline Token Verification & Downstream Routing
*   **`JwtTokenParser`**: Validates JWT signatures offline using RS256 public keys or HMAC secrets, with an unsigned fallback for local development.
*   **`WorkflowSecurityAspect`**: Checks incoming request `Authorization` header, locally decodes JWT claims, matches the token tenant ID with the HTTP context header, and checks roles.
*   **`WebClientHelper`**: WebClient wrapper that automatically propagates the active `X-Tenant-ID` and Bearer tokens to all downstream microservices.

### C. Tenant Database Routing (MongoDB)
*   **`TenantRoutingMongoDatabaseFactory`**: Custom `MongoDatabaseFactory` that intercepts database connections and switches databases on the fly based on the active `TenantContext` (supporting dedicated database routing for premium tiers).
*   **Prefix-based indexing**: Standard databases use `{ tenantId: 1 }` prefix keys to ensure query partitions.

### D. Workflow Engine & Feature Aspects
*   **`StateMachineEngine`**: Multi-step state machine executor that processes SpEL conditions, handles error backoffs/retries, and registers states. Uses Java 21 Virtual Threads for non-blocking concurrent runs.
*   **`TenantModuleValidationAspect`**: Intercepts step executions, blocking them if the tenant's registry has disabled the module.

---

## 3. Integration Guide

### Step 1: Install core libraries to your local Maven repository
Run the maven build at the root of `core-libraries` to install the SDK artifacts:
```bash
mvn clean install
```

### Step 2: Add Maven dependencies to your microservices
```xml
<dependency>
    <groupId>com.core</groupId>
    <artifactId>core-common</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>com.core</groupId>
    <artifactId>workflow-engine</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Step 3: Enable Auto-Configuration in Spring Boot
The configurations are loaded automatically via Spring Boot imports. Simply define properties in your service's `application.yml`:
```yaml
# JWT Signature Verification Config
core:
  security:
    jwt:
      secret: "your-super-secret-hmac-key-containing-at-least-256-bits"
      # Or specify a public key:
      # public-key: "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA..."
```

---

## 4. Running Verification Suite

The repository has comprehensive integration tests covering thread propagation, JWT parsing, and MongoDB dynamic routing:
```bash
mvn clean test
```
See test definitions in **`workflow-rest/src/test/java/com/core/workflow/controller/MultiTenancyVerificationTest.java`**.
