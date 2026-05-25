# Login con JWT + HttpOnly Cookies — Spring Boot & Vanilla JS

Sistema de autenticación full-stack con **JWT**, **cookie HttpOnly** y estrategia de doble transporte del token. El backend es Spring Boot 4 (Java 21) y el frontend es HTML/CSS/JS puro servido con Live Server.

---

## Estructura del proyecto

```
LoginCookies/
├── cookies/                        ← Backend Spring Boot
│   ├── pom.xml
│   └── src/main/java/com/taller/cookies/
│       ├── CookiesApplication.java
│       ├── controller/
│       │   ├── AuthController.java     ← Login / Me / Logout
│       │   └── CookieController.java   ← Demo de cookies (sin auth)
│       └── security/
│           ├── JwtUtil.java            ← Genera y valida JWTs
│           ├── JwtAuthFilter.java      ← Filtro: extrae JWT de cookie o header
│           └── SecurityConfig.java     ← Configuración Spring Security + CORS
│
└── cookies-Frontend/               ← Frontend estático
    ├── login.html
    ├── dashboard.html
    ├── css/style.css
    └── js/
        ├── login.js                ← Lógica de inicio de sesión
        └── dashboard.js            ← Validación de sesión y cierre
```

---

## Cómo levantar el sistema

### 1. Backend

```powershell
cd cookies
.\mvnw.cmd spring-boot:run
```

Levanta en `http://localhost:8080`. Requiere JDK 21 (el `mvnw.cmd` lo detecta automáticamente en `~/.jdks/ms-21.0.11`).

### 2. Frontend

Abrir `cookies-Frontend/login.html` con la extensión **Live Server** de VS Code.  
Queda disponible en `http://localhost:5500/login.html`.

### Credenciales de prueba

| Campo    | Valor          |
|----------|----------------|
| Usuario  | cualquier texto |
| Clave    | `1234`          |

---

## Arquitectura y flujo completo

```
┌─────────────────────────────────────────────────────────────┐
│  FRONTEND (localhost:5500)         BACKEND (localhost:8080)  │
│                                                             │
│  login.html ──POST /api/auth/login──►  AuthController       │
│                ◄── {usuario, token} + Set-Cookie ──         │
│                                                             │
│  dashboard.html ──GET /api/auth/me──►  JwtAuthFilter        │
│   (Bearer header + cookie)              │                   │
│                ◄── {usuario, estado} ──  AuthController      │
│                                                             │
│  logout ──POST /api/auth/logout──►  AuthController          │
│           ◄── cookie expirada (maxAge=0) ──                 │
└─────────────────────────────────────────────────────────────┘
```

### Paso a paso del flujo

1. **Usuario ingresa credenciales** en `login.html` → `login.js` hace `POST /api/auth/login`.
2. **El backend valida** la clave (en producción sería contra una BD con BCrypt). Si es correcta genera un JWT firmado con HS256.
3. **El JWT viaja al frontend de dos formas simultáneas:**
   - **Cookie HttpOnly** (`auth_token`, `SameSite=Lax`, 1 hora) → el navegador la guarda automáticamente y la envía en cada petición al mismo dominio.
   - **Body de la respuesta** (`{ token: "..." }`) → `login.js` lo guarda en `sessionStorage`.
4. **El frontend redirige a `dashboard.html`.**
5. **Al cargar el dashboard**, `dashboard.js` llama `GET /api/auth/me` enviando **ambos mecanismos**:
   - `credentials: 'include'` → manda la cookie HttpOnly.
   - `Authorization: Bearer <token>` → manda el token de `sessionStorage`.
6. **`JwtAuthFilter`** intercepta la petición, busca el token en la cookie primero y en el header después. Si el token es válido, registra la autenticación en el `SecurityContext`.
7. **`/api/auth/me`** devuelve los datos del usuario que Spring Security inyecta como `Principal`.
8. **Al cerrar sesión**, `POST /api/auth/logout` le dice al navegador que expire la cookie inmediatamente (`maxAge=0`) y `dashboard.js` borra el `sessionStorage`.

---

## Endpoints del backend

| Método | Ruta              | Auth requerida | Descripción                                  |
|--------|-------------------|:--------------:|----------------------------------------------|
| POST   | `/api/auth/login`  | No            | Valida credenciales, genera JWT, setea cookie |
| GET    | `/api/auth/me`     | **Sí**        | Devuelve datos del usuario autenticado        |
| POST   | `/api/auth/logout` | **Sí**        | Expira la cookie del JWT                      |
| GET    | `/api/cookies/**`  | No            | Endpoints demo de cookies (sin seguridad)     |

### POST `/api/auth/login`

**Request:**
```json
{
  "usuario": "juan",
  "clave": "1234"
}
```

**Response 200:**
```json
{
  "mensaje": "Login exitoso",
  "usuario": "juan",
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```
Además, el header `Set-Cookie` contiene:
```
auth_token=eyJ...; Path=/; Max-Age=3600; HttpOnly; SameSite=Lax
```

**Response 401:**
```json
{
  "error": "Credenciales inválidas. Usa cualquier usuario y clave 1234"
}
```

---

### GET `/api/auth/me`

**Headers requeridos (uno o ambos):**
```
Cookie: auth_token=eyJ...
Authorization: Bearer eyJ...
```

**Response 200:**
```json
{
  "usuario": "juan",
  "estado": "Autenticado",
  "tecnologia": "Spring Boot + JWT + HttpOnly Cookie"
}
```

**Response 401** (token ausente o inválido):
```json
No autenticado
```

---

### POST `/api/auth/logout`

**Response 200:**
```json
{
  "mensaje": "Sesión cerrada exitosamente"
}
```
Header de respuesta:
```
Set-Cookie: auth_token=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax
```

---

## Componentes de seguridad

### `JwtUtil.java`

Genera y valida tokens usando **JJWT 0.12.3** con firma **HS256**.

```
Secret key  → application.properties (jwt.secret)
Expiración  → 3 600 000 ms (1 hora)   (jwt.expiration)
Algoritmo   → HS256 (HMAC-SHA256)
Claims      → subject = username, issuedAt, expiration
```

### `JwtAuthFilter.java`

`OncePerRequestFilter` que se ejecuta en cada petición antes de los controladores.

```
Orden de búsqueda del token:
  1. Cookie "auth_token"           → extrae el valor directamente
  2. Header "Authorization: Bearer" → extrae el substring después de "Bearer "

Si el token es válido → registra UsernamePasswordAuthenticationToken en SecurityContext
Si no hay token o es inválido → deja pasar la petición sin autenticación (Spring Security
   rechazará las rutas protegidas y devolverá 401)
```

### `SecurityConfig.java`

```
CSRF           → deshabilitado (API stateless, no hay sesión de servidor)
Sesiones       → STATELESS (no se crean sesiones HTTP)
Form login     → deshabilitado
HTTP Basic     → deshabilitado
AuthEntryPoint → devuelve 401 (no 403) cuando no hay autenticación

Rutas públicas:
  POST /api/auth/login
  GET  /api/cookies/**

Rutas protegidas:
  cualquier otra → requiere JWT válido

CORS (doble capa):
  1. CorsFilter servlet (@Order más alto) → cubre TODAS las respuestas incluyendo errores
  2. http.cors() en Spring Security         → cubre el pipeline normal
  Orígenes permitidos: http://localhost:*, http://127.0.0.1:*
```

---

## Por qué doble transporte del token (cookie + Bearer)

Las peticiones `fetch()` entre orígenes distintos (puerto 5500 → 8080) son **cross-origin**. Los navegadores tienen reglas distintas según la plataforma y la configuración para enviar cookies cross-origin:

| Mecanismo             | Ventaja                              | Limitación                                   |
|-----------------------|--------------------------------------|----------------------------------------------|
| Cookie HttpOnly       | Invisible para JS → protege de XSS  | Algunos browsers no la envían cross-origin   |
| `Authorization: Bearer` | Siempre funciona en cross-origin   | Accesible desde JS → riesgo si hay XSS       |

La solución es usar **ambos al mismo tiempo**: el backend acepta cualquiera de los dos, el frontend envía los dos. Si la cookie llega, se usa; si no, el Bearer header sirve de fallback. En producción con HTTPS y mismo dominio se puede eliminar el Bearer y confiar solo en la cookie.

---

## Configuración (`application.properties`)

```properties
server.port=8080

# Clave secreta para firmar los JWT (mínimo 32 caracteres para HS256)
jwt.secret=T4ll3rSpr1ngB00tJWT-S3cr3tK3y-C0mpl3t0-Ok!

# Tiempo de vida del token en milisegundos (3 600 000 = 1 hora)
jwt.expiration=3600000

# Orígenes CORS permitidos (separados por coma)
cors.allowed-origins=http://localhost:5500,http://127.0.0.1:5500,http://localhost:3000
```

> **Importante para producción:** la `jwt.secret` debe almacenarse como variable de entorno o secret manager, nunca en el repositorio.

---

## Dependencias principales (`pom.xml`)

```xml
<!-- Spring Security -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- JJWT (Java JWT) 0.12.3 -->
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-api</artifactId>
  <version>0.12.3</version>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-impl</artifactId>
  <version>0.12.3</version>
  <scope>runtime</scope>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-jackson</artifactId>
  <version>0.12.3</version>
  <scope>runtime</scope>
</dependency>
```

---

## Tecnologías

| Capa       | Tecnología                          |
|------------|-------------------------------------|
| Backend    | Spring Boot 4.0.6 + Spring Security |
| JWT        | JJWT 0.12.3 (HS256)                 |
| Java       | JDK 21 (ms-21.0.11)                 |
| Build      | Maven Wrapper (mvnw.cmd)            |
| Frontend   | HTML5 + CSS3 + Vanilla JS           |
| Dev server | VS Code Live Server (puerto 5500)   |
