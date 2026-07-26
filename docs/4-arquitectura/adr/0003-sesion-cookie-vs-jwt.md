# ADR-0003: Estrategia de sesión — Cookie HTTP clásica

**Estado**: Aceptada
**Fecha**: 2026-04-30
**Decisor**: Francisco Quiroga (fran-q)

## Contexto

El sistema necesita autenticar usuarios y mantener su identidad entre requests. Las dos opciones modernas son:

1. **Sesión clásica con cookie HTTP** (`JSESSIONID` en una cookie httpOnly + sesión server-side).
2. **JWT (JSON Web Tokens)** stateless con el token en `Authorization: Bearer ...` o en una cookie.

## Decisión

Se adopta **sesión clásica con cookie HTTP** gestionada por Spring Security + Tomcat. La cookie se llama `JSESSIONID`, se marca `HttpOnly` y `Secure` (cuando estemos en HTTPS), y la sesión vive en memoria del server (timeout configurable, default 30 min).

## Razones

### A favor de cookie clásica (lo elegido)

- **Encaja con Thymeleaf server-side**: la app es un monolito que renderiza HTML del lado del servidor. La sesión vive donde corre la lógica.
- **CSRF natural**: Spring Security trae CSRF tokens listos para forms. Con JWT habría que armar otro flujo.
- **Logout server-side real**: invalidar la sesión la mata. Con JWT, el logout es lógico (token sigue siendo válido hasta expirar) salvo que tengamos blacklist server-side, lo que vuelve la solución *con estado* (matando el supuesto stateless).
- **Simplicidad**: cero código custom para emitir/validar tokens, refrescarlos, manejar expiración, blacklists, etc.
- **Menor superficie de error**: las decisiones sobre `exp`, `iss`, `aud`, refresh tokens, replay attacks, almacenamiento del token en el cliente, son problemas que no tenemos que resolver.
- **Multi-tenant**: el `TenantInterceptor` lee `SecurityContextHolder.getContext().getAuthentication()` que ya está populado por la sesión. Nada extra para hacer.

### Por qué se descartó JWT (por ahora)

- **Complejidad sin beneficio claro**: JWT brilla con *múltiples servicios* compartiendo identidad (microservicios) o *clientes nativos* (mobile, SPA cross-origin). No tenemos ninguno de los dos: es un único monolito que sirve HTML al mismo origen.
- **Logout es un dolor**: para invalidar tokens antes de su `exp` hay que armar blacklist (vuelve stateful) o forzar refresh corto (más complejidad).
- **Tamaño de request**: cada request lleva el token en header → más bytes vs. una cookie chica.
- **Almacenamiento del token en cliente**: `localStorage` es vulnerable a XSS, cookies httpOnly no pero entonces... ya estamos usando cookies, ¿para qué JWT?

## Implementación

```java
// SecurityConfig.java (resumen)
http.formLogin(form -> form.loginPage("/login")...)
    .logout(logout -> logout.deleteCookies("JSESSIONID")...);
```

`server.servlet.session.timeout=30m` en `application.properties`.

`CustomUserDetailsService` carga el usuario desde la tabla `usuarios`. La sesión guarda un `UsernamePasswordAuthenticationToken` con un `CustomUserDetails` como principal — el `TenantInterceptor` lo lee en cada request y popula el `TenantContext`.

## Cuándo revisar esta decisión

Si en el futuro:

- Se separa el **reconocimiento facial** a un microservicio independiente (Sprint 4+ tal vez), y necesitamos que ese servicio confíe en la identidad del request → JWT firmado podría tener sentido **entre servicios** (no para el cliente web).
- Se decide construir una **app móvil** o un cliente externo (API REST consumida desde otro origen) → JWT (o algún esquema basado en token) sería el camino estándar para clientes que no hablan cookies.
- Aparecen requirimientos de SSO con identidad federada (OAuth2/OIDC) — ahí nos integramos como cliente de un IdP, y el "token" lo emite el IdP, no nosotros.

Hasta entonces, **cookie clásica server-side**.

## Consecuencias

**Positivas:**
- Implementación mínima, alineada con el stack (Spring Boot + Thymeleaf).
- CSRF trivial. Logout real. Sesión revocable.
- Menos vectores de ataque que diseñar/cubrir.

**Negativas:**
- La sesión vive en memoria del proceso → restart del server invalida sesiones (acceptable: el user vuelve a loguear).
- Si en el futuro se balancean varias instancias, hay que sticky sessions o session replication (Redis store, etc.). Está documentado para revisitar.

## Referencias

- Sprint 1 Fase C+D — `CustomUserDetailsService`, `TenantInterceptor`.
- "Stop Using JWT for Sessions" — Sven Slootweg, 2016.
- "JWT should not be your default for sessions" — Joel Parker Henderson.
