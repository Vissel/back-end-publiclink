# API Gateway Routing Examples

## 1. PublicLink Application

### Gateway Request
```
POST http://localhost:8080/publiclink/api/v1/qr/create
```

### Gateway Routing
- **Route Match**: `/publiclink/**` ✓
- **Target URI**: `http://localhost:9080`
- **Final Request**: `http://localhost:9080/publiclink/api/v1/qr/create`

### Backend Processing
- App Context Path: `/publiclink`
- Processed Path: `/api/v1/qr/create`
- Reaches Controller: `@RequestMapping("/api/v1/qr")`

---

## 2. User Application

### Gateway Request Examples

#### Create User
```
POST http://localhost:8080/api/v1/user/createUser
Content-Type: application/json

{
  "userName": "john_doe",
  "password": "securePassword123",
  "email": "john@example.com"
}
```

### Gateway Routing
- **Route Match**: `/api/v1/user/**` ✓
- **Target URI**: `http://localhost:8082/user`
- **Final Request**: `http://localhost:8082/user/api/v1/user/createUser`

### Backend Processing
- App Context Path: `/user`
- Processed Path: `/api/v1/user/createUser`
- Reaches Controller: `UserController` → `@RequestMapping("/api/v1/user")` → `@PostMapping("/createUser")`

#### Find User
```
POST http://localhost:8080/api/v1/user/findByUsername
Content-Type: application/json

{
  "userName": "john_doe"
}
```

### Gateway Routing
- **Route Match**: `/api/v1/user/**` ✓
- **Target URI**: `http://localhost:8082/user`
- **Final Request**: `http://localhost:8082/user/api/v1/user/findByUsername`

---

## Summary Table

| Client Request | Gateway Path Match | Target URI | Final Backend URL | Status |
|---|---|---|---|---|
| `POST /publiclink/api/v1/qr/create` | `/publiclink/**` | `http://localhost:9080` | `http://localhost:9080/publiclink/api/v1/qr/create` | ✓ |
| `POST /api/v1/user/createUser` | `/api/v1/user/**` | `http://localhost:8082/user` | `http://localhost:8082/user/api/v1/user/createUser` | ✓ |
| `POST /api/v1/user/findByUsername` | `/api/v1/user/**` | `http://localhost:8082/user` | `http://localhost:8082/user/api/v1/user/findByUsername` | ✓ |

---

## Gateway Host
- **Default**: `http://localhost:8080` (Spring Cloud Gateway default port)
- Update this based on your actual gateway configuration

