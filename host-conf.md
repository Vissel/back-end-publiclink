# Backend & Frontend — Multi-Environment Configuration

## Architecture Overview

| Module | Port | Base Path | DB Schema | Role |
|--------|------|-----------|-----------|------|
| `apiGateway` | 8080 | `/` | None | Entry point — JWT validation, rate limiting, routing |
| `authen-authorisation` | 8081 | `/authen-authorisation` | `publiclink-db` | Server-to-server auth (RSA key, normal login) |
| `user` | 8082 | `/api/v1/user`, `/api/v1/seller` | `user_schema` | User & seller CRUD, auth token management |
| `publiclink-app` | 9080 | `/publiclink` | `publiclink-db` | Core business — sale environments, orders, products |

### Gateway Routes
| Route | Target Service | Default URL |
|-------|---------------|-------------|
| `/publiclink/**` | publiclink-app | `http://localhost:9080` |
| `/api/v1/user/**` | user service | `http://localhost:8082` |

---

## Backend Configuration

Every Spring Boot service binds to **all network interfaces** (`server.address=0.0.0.0`), so it accepts connections via localhost, LAN IP, or hostname simultaneously.

### Module Ports & Files

| Module | Config File | Port |
|--------|------------|------|
| apiGateway | `apiGateway/src/main/resources/application.properties` | 8080 |
| publiclink-app (dev) | `publiclink-app/src/main/resources/application-dev.properties` | 9080 |
| publiclink-app (qa) | `publiclink-app/src/main/resources/application-qa.properties` | 9080 |
| user | `user/src/main/resources/application.properties` | 8082 |
| authen-authorisation | `authen-authorisation/src/main/resources/application.properties` | 8081 |

### Environment Variables

All inter-service URLs are configurable via environment variables:

| Variable | Default | Used By | Purpose |
|----------|---------|---------|---------|
| `PUBLICLINK_SERVICE_URL` | `http://localhost:9080` | GatewayRoutesConfig (apiGateway) | Gateway → publiclink-app routing |
| `USER_SERVICE_URL` | `http://localhost:8082` | GatewayRoutesConfig, UserClient, OperatedUserClient, OperatedSellerClient | Gateway → user service routing, publiclink-app → user service WebClient calls |
| `SECURITY_SERVICE_URL` | `http://localhost:8080` | SecurityCheckClient (publiclink-app) | publiclink-app → gateway security endpoints |
| `JWT_KEY` | *(required)* | All modules | JWT signing secret |
| `JWT_URLKEY` | *(required)* | publiclink-app, authen-authorisation | JWT URL secret |
| `RABBITMQ_HOST` | `localhost` | apiGateway, publiclink-app | RabbitMQ connection |
| `RABBITMQ_PORT` | `5672` | apiGateway, publiclink-app | RabbitMQ port |

---

## Cross-Environment Override Examples

### Local Development (default — no vars needed)
```bash
# Just start each service — defaults use localhost
```

### LAN Access via IP (e.g. 192.168.1.6)
```bash
export PUBLICLINK_SERVICE_URL=http://192.168.1.6:9080
export USER_SERVICE_URL=http://192.168.1.6:8082
export SECURITY_SERVICE_URL=http://192.168.1.6:8080
```

### LAN Access via Hostname (e.g. MacBook-Pro.local)
```bash
export PUBLICLINK_SERVICE_URL=http://MacBook-Pro.local:9080
export USER_SERVICE_URL=http://MacBook-Pro.local:8082
export SECURITY_SERVICE_URL=http://MacBook-Pro.local:8080
```

### Production (different hosts)
```bash
export PUBLICLINK_SERVICE_URL=http://app-server:9080
export USER_SERVICE_URL=http://user-server:8082
export SECURITY_SERVICE_URL=http://gateway-server:8080
```

---

## Frontend Configuration

The React frontend uses **dynamic hostname resolution** — no manual IP configuration needed.

### How It Works

1. **Dev server binds to all interfaces** via `HOST=0.0.0.0` in `.env.development`
2. **API base URL dynamically adapts** — `config.js` extracts the port from `REACT_APP_API_BASE_URL` but replaces the hostname with `window.location.hostname`

This means:
- Access from laptop → `http://localhost:3000` → API calls go to `http://localhost:8080`
- Access from mobile → `http://192.168.1.6:3000` → API calls go to `http://192.168.1.6:8080`
- Access via hostname → `http://macbook-pro.local:3000` → API calls go to `http://macbook-pro.local:8080`

### Configuration Files

**.env.development:**
```env
HOST=0.0.0.0
REACT_APP_API_BASE_URL=http://localhost:8080
```

**src/api/config.js:**
```js
const resolveBaseUrl = () => {
  const envUrl = process.env.REACT_APP_API_BASE_URL;
  if (!envUrl) return "";
  if (process.env.NODE_ENV === "production") return envUrl;
  
  // In development, replace hostname with current browser hostname
  try {
    const parsed = new URL(envUrl);
    parsed.hostname = window.location.hostname;
    return parsed.toString().replace(/\/$/, "");
  } catch {
    return envUrl;
  }
};
```

### Quick Start for Mobile Testing

```bash
# Terminal 1: Start all backend services (adjust IP to yours)
export PUBLICLINK_SERVICE_URL=http://192.168.1.6:9080
export USER_SERVICE_URL=http://192.168.1.6:8082
export SECURITY_SERVICE_URL=http://192.168.1.6:8080
# Then start each microservice (apiGateway, user, authen-authorisation, publiclink-app)

# Terminal 2: Start React frontend (no IP config needed!)
npm start

# Access from mobile browser:
# http://192.168.1.6:3000
# API calls automatically go to http://192.168.1.6:8080
```

---

## CORS Configuration

The gateway (`apiGateway/SecurityConfig.java`) already allows all origins:

```java
configuration.setAllowedOriginPatterns(List.of("*"));
configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
configuration.setAllowedHeaders(List.of("*"));
configuration.setAllowCredentials(true);
```

This covers requests from any hostname, IP, or port without additional configuration.

---

## Quick Start for Mobile Testing

```bash
# Terminal 1: Start all backend services (adjust IP to yours)
export PUBLICLINK_SERVICE_URL=http://192.168.1.6:9080
export USER_SERVICE_URL=http://192.168.1.6:8082
export SECURITY_SERVICE_URL=http://192.168.1.6:8080
# Then start each microservice (apiGateway, user, authen-authorisation, publiclink-app)

# Terminal 2: Start React frontend (no IP config needed!)
npm start

# Access from mobile browser:
# http://192.168.1.6:3000
# API calls automatically go to http://192.168.1.6:8080
```
