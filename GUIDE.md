# API Gateway CafeLab — Guía de implementación

## Stack del proyecto

| Aspecto | Valor |
|---|---|
| Framework | Spring Cloud Gateway (WebFlux/reactivo) |
| Spring Boot | 4.0.6 |
| Spring Cloud | 2025.1.1 (Oakwood) |
| Autenticación | JWT propio validado con OAuth2 Resource Server |
| Infra | Azure (Canada Central) |
| Service Discovery | Sin Eureka por ahora (URLs fijas), se agrega en el futuro |
| Funcionalidades | Enrutamiento, validación JWT centralizada, CORS, Load Balancing |

---

## Microservicios registrados

### `iam-service`
URL Azure: `https://cafelab-iam-cqhva4acfudtg0hc.canadacentral-01.azurewebsites.net`

| Dominio | Método | Path | JWT requerido | Descripción |
|---|---|---|---|---|
| **Authentication** | `POST` | `/api/v1/authentication/sign-up` | No | Crear cuenta y obtener token |
| | `POST` | `/api/v1/authentication/sign-in` | No | Login, obtener token |
| **Profiles** | `POST` | `/api/v1/profiles` | No | Crear perfil (se llama tras sign-up) |
| | `GET` | `/api/v1/profiles?email={email}` | Sí | Buscar perfil por email |
| | `GET` | `/api/v1/profiles/{userId}` | Sí | Obtener perfil por id |
| | `PATCH` | `/api/v1/profiles/{userId}` | Sí | Actualizar perfil |

---

### `procedures-service`
URL Azure: `https://cafelab-procedures-d6cxdhcwfzb4duhb.canadacentral-01.azurewebsites.net`

Todos los endpoints requieren JWT. El `userId` se extrae del token en el backend.

| Dominio | Método | Path | Descripción |
|---|---|---|---|
| **Recipes** | `POST` | `/api/v1/recipes` | Crear receta |
| | `GET` | `/api/v1/recipes/user/{userId}` | Listar recetas del usuario |
| | `GET` | `/api/v1/recipes/user/{userId}/{recipeId}` | Obtener receta por id |
| | `PUT` | `/api/v1/recipes/user/{userId}/{recipeId}` | Actualizar receta |
| | `DELETE` | `/api/v1/recipes/user/{userId}/{recipeId}` | Eliminar receta |
| **Ingredients** | `GET` | `/api/v1/recipes/{recipeId}/ingredients` | Listar ingredientes |
| | `POST` | `/api/v1/recipes/{recipeId}/ingredients` | Añadir ingrediente |
| | `PUT` | `/api/v1/recipes/{recipeId}/ingredients/{ingredientId}` | Actualizar ingrediente |
| | `DELETE` | `/api/v1/recipes/{recipeId}/ingredients/{ingredientId}` | Eliminar ingrediente |
| **Portfolios** | `POST` | `/api/v1/portfolios` | Crear portafolio |
| | `GET` | `/api/v1/portfolios/user/{userId}` | Listar portafolios |
| | `GET` | `/api/v1/portfolios/user/{userId}/{portfolioId}` | Obtener portafolio por id |
| | `PUT` | `/api/v1/portfolios/user/{userId}/{portfolioId}` | Actualizar portafolio |
| | `DELETE` | `/api/v1/portfolios/user/{userId}/{portfolioId}` | Eliminar portafolio |
| **Defects** | `POST` | `/api/v1/defects` | Crear defecto |
| | `GET` | `/api/v1/defects/user/{userId}` | Listar defectos |
| | `GET` | `/api/v1/defects/user/{userId}/{defectId}` | Obtener defecto por id |
| **Cupping sessions** | `POST` | `/api/v1/cupping-sessions` | Crear sesión de cata |
| | `GET` | `/api/v1/cupping-sessions/{userId}` | Listar sesiones |
| | `GET` | `/api/v1/cupping-sessions/user/{userId}/{sessionId}` | Obtener sesión por id |
| | `PUT` | `/api/v1/cupping-sessions/user/{userId}/{sessionId}` | Actualizar sesión |
| | `DELETE` | `/api/v1/cupping-sessions/user/{userId}/{sessionId}` | Eliminar sesión |
| **Grind calibrations** | `POST` | `/api/v1/calibrations` | Crear calibración |
| | `GET` | `/api/v1/calibrations/user/{userId}` | Listar calibraciones |
| | `GET` | `/api/v1/calibrations/user/{userId}/{calibrationId}` | Obtener calibración por id |
| | `PUT` | `/api/v1/calibrations/user/{userId}/{calibrationId}` | Actualizar calibración |

> **Importante:** Spring Cloud Gateway usa WebFlux (programación reactiva).
> **Nunca** agregues `spring-boot-starter-web` al proyecto — es incompatible con WebFlux.

---

## Fase 1 — Enrutamiento básico

El corazón del Gateway es su configuración de rutas en `application.yml`.
Crea el archivo (o renombra el `application.properties` existente) con esta estructura:

```yaml
spring:
  application:
    name: api-gateway-cafelab
  cloud:
    gateway:
      routes:
        - id: orders-service
          uri: ${ORDERS_SERVICE_URL}
          predicates:
            - Path=/api/orders/**

        - id: products-service
          uri: ${PRODUCTS_SERVICE_URL}
          predicates:
            - Path=/api/products/**

        - id: auth-service
          uri: ${AUTH_SERVICE_URL}
          predicates:
            - Path=/api/auth/**
```

### Cómo funciona cada campo

| Campo | Propósito |
|---|---|
| `id` | Nombre único de la ruta (solo informativo) |
| `uri` | URL base del microservicio destino |
| `predicates` | Condición para que la ruta aplique |
| `filters` | Transformaciones a request/response (se agregan más adelante) |

### El filtro `StripPrefix`

Si quieres que el microservicio reciba la ruta sin el prefijo `/api`, agrega:

```yaml
filters:
  - StripPrefix=1
```

Sin `StripPrefix`: gateway recibe `/api/orders/123` → microservicio recibe `/api/orders/123`
Con `StripPrefix=1`: gateway recibe `/api/orders/123` → microservicio recibe `/orders/123`

Elige según cómo estén mapeados tus controllers en los microservicios.

### Variables de entorno

Las URLs de los servicios van en variables de entorno para no hardcodearlas:

```yaml
uri: ${ORDERS_SERVICE_URL}
```

En local, crea un archivo `.env` o configura en tu IDE las variables:

```
ORDERS_SERVICE_URL=http://localhost:8081
PRODUCTS_SERVICE_URL=http://localhost:8082
AUTH_SERVICE_URL=http://localhost:8083
```

En Azure, se configuran en **App Service → Configuration → Application settings**.

---

## Fase 2 — CORS centralizado

Con el Gateway centralizas CORS para **todos** los microservicios.
Una vez que el Gateway lo maneje, puedes eliminar la configuración de CORS de cada microservicio.

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "https://tu-frontend.azurestaticapps.net"
              - "http://localhost:3000"   # para desarrollo local
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
              - OPTIONS
            allowedHeaders:
              - "*"
            allowCredentials: true
            maxAge: 3600
```

> Reemplaza `allowedOrigins` con la URL real de tu frontend en Azure.

---

## Fase 3 — Validación de JWT centralizada

Usamos **Spring Security OAuth2 Resource Server** (ya incluido en el `pom.xml`).
Este starter incluye **Nimbus JOSE JWT** internamente, sin necesidad de agregar JJWT por separado.

### Flujo de autenticación con el Gateway

```
Frontend
  │
  │  POST /api/auth/login  →  sin JWT (ruta pública)
  ▼
Gateway ──────────────────────────────────────────► Auth Service
  │                                                     │
  │                                          genera y devuelve JWT
  │◄─────────────────────────────────────────────────────
  │
  │  GET /api/orders/123   →  con JWT en header
  ▼
Gateway valida el JWT
  │
  ├── JWT inválido → responde 401 directamente (el microservicio nunca se entera)
  │
  └── JWT válido  ──────────────────────────────────────► Orders Service
```

### 3.1 Cómo firma el token el IAM de CafeLab

El `iam-service` usa **JJWT** con `Keys.hmacShaKeyFor()`, que selecciona el algoritmo HMAC
automáticamente según el tamaño de la clave:

| Tamaño de clave | Algoritmo elegido por JJWT |
|---|---|
| 32 bytes (256 bits) | HS256 |
| 48 bytes (384 bits) | HS384 |
| **64 bytes (512 bits)** | **HS512** ← recomendado para producción |

El token solo tiene tres campos en el payload:

```json
{
  "sub": "usuario@email.com",   ← el email, NO el userId
  "iat": 1234567890,
  "exp": 1235172690
}
```

> Para producción usa una clave de exactamente **64 bytes** en `JWT_SECRET`.
> Así JJWT usa HS512 y lo configuramos igual en el Gateway.

### 3.2 Configurar el decoder en el Gateway

El Gateway necesita el mismo secret que el IAM para verificar la firma.
La propiedad se llama igual que en el IAM: `authorization.jwt.secret`.

```java
// src/main/java/com/acme/apigatewaycafelab/config/SecurityConfig.java

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    // Mismo nombre de propiedad que en el iam-service
    @Value("${authorization.jwt.secret}")
    private String jwtSecret;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                // ── Rutas públicas del IAM service ──
                .pathMatchers(HttpMethod.POST, "/api/v1/authentication/sign-in").permitAll()
                .pathMatchers(HttpMethod.POST, "/api/v1/authentication/sign-up").permitAll()
                .pathMatchers(HttpMethod.POST, "/api/v1/profiles").permitAll()
                // ── Health check ──
                .pathMatchers("/actuator/health").permitAll()
                // ── Todo lo demás requiere JWT válido ──
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.decoder(jwtDecoder()))
            )
            .build();
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        // Keys.hmacShaKeyFor replica la misma derivación que usa el iam-service
        SecretKey key = Keys.hmacShaKeyFor(
            jwtSecret.getBytes(StandardCharsets.UTF_8)
        );
        // HS512 asume clave de producción de 64 bytes — ajusta si usas otra longitud
        return NimbusReactiveJwtDecoder
            .withSecretKey(key)
            .macAlgorithm(MacAlgorithm.HS512)
            .build();
    }
}
```

> **Imports necesarios:**
> ```java
> import io.jsonwebtoken.security.Keys;
> import javax.crypto.SecretKey;
> import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
> import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
> ```
> JJWT ya está disponible transitivamente desde `spring-boot-starter-oauth2-resource-server`
> a través de Nimbus. No necesitas agregar JJWT al `pom.xml` del Gateway.

### 3.3 Agregar la propiedad en `application.yml`

```yaml
# Mismo nombre que en el iam-service (authorization.jwt.secret)
authorization:
  jwt:
    secret: ${JWT_SECRET}
```

> **Local**: el valor de `JWT_SECRET` debe ser la misma cadena que tienes en el
> `application.properties` del IAM: `local-dev-only-change-me-0123456789abcdef0123456789abcdef`
>
> **Azure**: configura `JWT_SECRET` en App Service → Configuration → Application settings
> con la misma clave que tiene el IAM desplegado.

### 3.4 Propagar información del usuario a los microservicios

El subject del token del IAM de CafeLab es el **email** del usuario (no el userId).
Propaga el email como header para que los microservicios puedan identificar al usuario
sin necesidad de re-validar el token.

```java
// src/main/java/com/acme/apigatewaycafelab/filter/UserContextFilter.java

@Component
public class UserContextFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
            .cast(JwtAuthenticationToken.class)
            .flatMap(auth -> {
                Jwt jwt = auth.getToken();

                // El subject del IAM de CafeLab es el email del usuario
                String email = jwt.getSubject();

                ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Email", email)
                    .build();

                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            })
            .switchIfEmpty(chain.filter(exchange)); // rutas públicas no tienen principal
    }

    @Override
    public int getOrder() {
        return -1; // se ejecuta después de que Spring Security validó el token
    }
}
```

En tus microservicios podrás leer el email del usuario autenticado:

```java
@GetMapping("/recipes/user/{userId}")
public List<Recipe> getRecipes(
    @PathVariable Long userId,
    @RequestHeader("X-User-Email") String authenticatedEmail
) {
    // Puedes verificar que el userId del path corresponde al email del token
    return recipeService.findByUser(userId);
}
```

---

## Fase 4 — Load Balancing

### Sin Eureka (configuración actual)

Por ahora cada microservicio tiene una URL fija. El load balancing lo puedes delegar a
**Azure App Service** (que ya escala horizontalmente) o usar múltiples instancias detrás
del mismo hostname de Azure.

### Con Eureka (futuro)

Cuando agregues Eureka, el único cambio en las rutas es el prefijo `lb://`:

```yaml
# Antes (URL fija):
uri: ${ORDERS_SERVICE_URL}

# Después (con Eureka):
uri: lb://orders-service
```

Y agregas esta dependencia al `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

---

## Fase 5 — Despliegue en Azure

### Opción A — Azure App Service (recomendada para empezar)

1. Empaqueta el proyecto:
   ```bash
   ./mvnw clean package -DskipTests
   ```

2. Crea el App Service en Azure (Linux, Java 21).

3. Despliega el `.jar` generado en `target/`:
   ```bash
   az webapp deploy --resource-group <rg> --name <app-name> \
     --src-path target/api-gateway-cafelab-0.0.1-SNAPSHOT.jar \
     --type jar
   ```

4. Configura las variables de entorno en **App Service → Configuration → Application settings**:
   ```
   JWT_SECRET=<tu-clave-secreta-larga-y-aleatoria>
   ORDERS_SERVICE_URL=https://orders.azurewebsites.net
   PRODUCTS_SERVICE_URL=https://products.azurewebsites.net
   AUTH_SERVICE_URL=https://auth.azurewebsites.net
   ```

### Opción B — Azure Container Apps (más escalable)

1. Crea un `Dockerfile` en la raíz del proyecto:
   ```dockerfile
   FROM eclipse-temurin:21-jre
   COPY target/api-gateway-cafelab-0.0.1-SNAPSHOT.jar app.jar
   EXPOSE 8080
   ENTRYPOINT ["java", "-jar", "/app.jar"]
   ```

2. Sube la imagen a Azure Container Registry y despliega en Container Apps.

---

## Fase 6 — Ajustes en tus microservicios existentes

Una vez el Gateway esté operativo:

- **Elimina** la configuración de CORS en cada microservicio (el Gateway lo maneja).
- **Elimina** o simplifica la validación de JWT (el Gateway garantiza que solo llegan requests autenticadas).
- **Confía** en los headers `X-User-Id` y `X-User-Email` que el Gateway inyecta.
- **Asegura** que los microservicios **no** sean accesibles directamente desde internet (solo el Gateway debe ser público). En Azure, esto se logra con Virtual Networks o configurando las apps para que solo acepten tráfico de la IP del Gateway.

---

## Orden de implementación recomendado

- [ ] **Paso 1** — Renombrar `application.properties` a `application.yml`
- [ ] **Paso 2** — Configurar las rutas básicas y verificar el enrutamiento con Postman
- [ ] **Paso 3** — Configurar CORS global
- [ ] **Paso 4** — Implementar `SecurityConfig` con `ReactiveJwtDecoder`
- [ ] **Paso 5** — Probar rutas públicas, rutas protegidas con token válido, con token inválido
- [ ] **Paso 6** — Implementar `UserContextFilter` para propagar claims
- [ ] **Paso 7** — Desplegar en Azure y configurar variables de entorno
- [ ] **Paso 8** — Ajustar los microservicios para eliminar JWT/CORS propios
- [ ] **Paso 9 (futuro)** — Agregar Eureka para service discovery dinámico

---

## Referencias

- [Documentación oficial de Spring Cloud Gateway](https://docs.spring.io/spring-cloud-gateway/reference/)
- [Spring Security Reactive OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/reactive/oauth2/resource-server/jwt.html)
- [Tabla de compatibilidad Spring Boot / Spring Cloud](https://spring.io/projects/spring-cloud#overview)
- [Azure App Service — Deploy Java](https://learn.microsoft.com/azure/app-service/quickstart-java)
