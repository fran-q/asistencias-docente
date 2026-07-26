# Deuda técnica conocida

Este archivo registra las decisiones técnicas que conscientemente postergamos. No son bugs — son trade-offs documentados para retomar cuando tenga sentido.

## TD-001: Nombres explícitos de PRIMARY KEY ignorados por MariaDB

**Detectado**: Sprint 0 (V001__init.sql)
**Severidad**: Baja (cosmético)
**Estado**: Aceptado — no se corrige

### Síntoma

Al aplicar la migración V001, Flyway loguea ~15 warnings de la forma:
```
DB: Name 'pk_instituciones' ignored for PRIMARY key. (SQL State: 42000 - Error Code: 1280)
```

### Causa

En `V001__init.sql` declaramos las claves primarias con sintaxis nombrada:
```sql
CONSTRAINT pk_instituciones PRIMARY KEY (id)
```

MariaDB acepta la PK pero **descarta el nombre** — todas las primary keys en MariaDB se llaman internamente `PRIMARY` por restricción del motor (no es configurable). El estándar SQL permite nombres custom, pero MariaDB/MySQL no los honran para PKs (sí para FKs y UNIQUE).

### Por qué no se corrige

1. **Las PKs funcionan correctamente** — los warnings son sólo informativos.
2. **V001 ya está aplicada** y commiteada. Modificarla rompería el tracking de Flyway.
3. Una migración nueva V00X que renombre/recree las PKs traería más riesgo (lock de tablas, recálculo de índices) que beneficio (silenciar logs).
4. Si en el futuro se migrara a PostgreSQL u otro motor que sí honre los nombres, los CONSTRAINT pk_xxx ya están listos.

### Mitigación

Los warnings aparecen una sola vez por migración. En arranques posteriores Flyway no re-aplica V001, así que los logs limpios.

---

## TD-002: Driver MySQL en lugar de MariaDB nativo (RESUELTO en Sprint 1)

**Detectado**: Sprint 0
**Severidad**: Baja (cosmético + sub-óptimo)
**Estado**: Resuelto — ver commit de Sprint 1

### Síntoma original

```
HHH000511: The 5.5.5 version for [org.hibernate.dialect.MariaDBDialect]
is no longer supported, hence certain features may not work properly.
```

Hibernate detecta MariaDB como "5.5.5" porque MariaDB devuelve esa versión legacy en el handshake JDBC para mantener compatibilidad con clientes MySQL antiguos.

### Solución aplicada

Cambio en `build.gradle`:
```diff
- runtimeOnly 'com.mysql:mysql-connector-j'
+ runtimeOnly 'org.mariadb.jdbc:mariadb-java-client'
```

Y en `application-local.properties`:
```diff
- spring.datasource.url=jdbc:mysql://...
+ spring.datasource.url=jdbc:mariadb://...
- spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
+ spring.datasource.driver-class-name=org.mariadb.jdbc.Driver
```

El driver nativo de MariaDB hace el handshake correctamente y reporta la versión real (10.4.32), por lo que Hibernate elige features modernos sin warnings.

---

## TD-003: El filtro Hibernate "tenant" NO se propaga a JOINs en JPQL

**Detectado**: Sprint 2 Fase D (Comisiones).
**Severidad**: **Alta** — fuga multi-tenant si se ignora.
**Estado**: Aceptado como invariante. Mitigado con regla de codigo.

### Síntoma

Una query JPQL del tipo:

```java
@Query("SELECT c FROM Comision c JOIN c.materia m ORDER BY ...")
List<Comision> findAllDelTenant();
```

devolvía Comisiones de **todos los tenants**, aunque `Materia` tiene `@Filter(name="tenant")` y el `TenantFilterAspect` activa el filtro por request.

Resultado: el SUPERADMIN UTF veía las comisiones de CENT35 al pegarle a `/comisiones`.

### Causa

El filtro Hibernate (`@Filter`) **se aplica a la entidad raíz del SELECT** y a sus colecciones (`@OneToMany`/`@ManyToMany`), pero **no se propaga automáticamente a entidades JOINeadas** desde JPQL. Esto está documentado pero es contraintuitivo.

En particular: `Comision` no es tenant-scoped (no tiene `@Filter`); el JOIN a `Materia` (que sí tiene `@Filter`) no fuerza el WHERE `materia.institucion_id = :tenantId` automáticamente cuando el SELECT es de Comision.

### Mitigación aplicada

**Regla**: cualquier `@Query` JPQL que JOINee a una entidad tenant-scoped (Materia, Carrera, Usuario, Docente, etc.) **debe filtrar explícitamente** por `institucionId`:

```java
@Query("""
    SELECT c FROM Comision c
    JOIN c.materia m
    WHERE m.institucionId = :tenantId
    ORDER BY ...
    """)
List<Comision> findAllDelTenant(@Param("tenantId") Long tenantId);
```

El service pasa `TenantContext.getRequired()` como argumento.

### Cuándo aplica esta regla

- ✅ Aplica: queries `@Query` JPQL/HQL con JOIN a entidad filtrada.
- ✅ Aplica: queries nativas (`nativeQuery=true`).
- ❌ No aplica: derived queries de Spring Data (ej: `findByMateriaIdAndCodigo`) — generan JPQL sobre la entidad raíz solamente, sin JOIN explícito a entidad filtrada.
- ❌ No aplica: `findAll()` sobre entidad que SÍ tiene `@Filter` — el filtro funciona normal.

### Lecciones para Sprint 3+

Cuando agreguemos consultas con JOIN a entidades de docente, asistencia, etc., **siempre incluir el `WHERE m.institucionId = :tenantId`** explícitamente. La defensa en profundidad sigue siendo:

1. Filtro Hibernate en la entidad raíz (cuando la entidad es tenant-scoped).
2. WHERE explícito en cualquier JOIN.
3. Validación en service (verificar que el resultado pertenece al tenant).
4. ID secuenciales (no leakeables) + camuflado como "no encontrado" si hay cross-tenant.

Tests de aislamiento al final de cada CRUD que JOINee. **Esto se descubrió manualmente en Fase D — no automatizado**. Pendiente: agregar test de integración con dos tenants.

---

## TD-004: Conversión lineal distancia LBPH → confianza

**Detectado**: Sprint 5 Fase A
**Severidad**: Baja
**Estado**: Aceptado

### Contexto

La tabla `asistencias.confianza` está pensada como score 0–1 (mayor = más confiable). LBPH devuelve distancia (menor = mejor). El service usa una conversión lineal:

```java
score = max(0, 1 - distancia / umbralConfianza)
```

con `umbralConfianza` como referencia.

### Por qué es deuda

LBPH no garantiza linealidad entre distancia y "probabilidad de match". Distancia 50 no es exactamente "50% mejor que 100". Para un PoC alcanza, pero para reportes estadísticos serios habría que calibrar con datos reales (histogramas de distancia para matches y no-matches).

### Próximo paso

Cuando haya datos reales, hacer una calibración no lineal (p. ej. una sigmoide) y publicar la nueva fórmula en ADR-0007.

---

## TD-005: Cache de modelos LBPH sin TTL ni límite de memoria

**Detectado**: Sprint 4 Fase D
**Severidad**: Baja en PoC, Media en producción

### Síntoma

`IdentificacionFacialService` mantiene un `ConcurrentHashMap<Long, LBPHFaceRecognizer>` indefinido. Para 200-400 docentes por institución es manejable, pero:
- Si una institución crece a miles, la RAM nativa de OpenCV sube proporcionalmente.
- No hay TTL: un docente dado de baja queda en cache hasta que se llame a `sincronizarCache` (que sí lo limpia, pero solo en próxima identificación).

### Mitigación temporal

`sincronizarCache` se ejecuta en cada llamada a `identificar`. Como el cliente llama frecuentemente, los modelos obsoletos se evictan en segundos.

### Próximo paso

Migrar a una librería de cache con TTL y tamaño máximo (Caffeine). Si la cantidad de docentes crece mucho, evaluar embeddings tipo FaceNet con índice ANN (FAISS, HNSWLib).

---

## TD-007: El aspecto multi-tenant quedó inactivo tras la reorganización (RESUELTO)

**Detectado**: revisión general post-Sprint 6.
**Severidad**: **Alta** — fuga multi-tenant real y activa.
**Estado**: Resuelto. Queda como lección documentada.

### Síntoma

`TenantFilterAspect` no se ejecutaba. El filtro Hibernate `"tenant"` nunca se activaba,
por lo que las **derived queries** sobre entidades tenant-scoped devolvían datos de
**todas las instituciones**. Ejemplos afectados: `CarreraService.listar()`,
`MateriaService.listar()`, `DocenteService.listar()` y todos los selectores de
docentes/materias/carreras de los formularios.

### Causa

El pointcut apuntaba a una estructura de paquetes que dejó de existir:

```java
@Before("execution(* edu.cent35.asistencias..application..*(..))")
```

Era correcto con package-by-feature (`docente/application/`, `academico/application/`).
La reorganización a package-by-layer (ADR-0006, commit `24b8dd2`) movió los services a
`edu.cent35.asistencias.service` y eliminó todos los paquetes `application/`. El pointcut
dejó de coincidir con nada.

### Por qué no lo detectó nadie

Tres razones que conviene entender:

1. **Un aspecto que no matchea no falla**: simplemente no hace nada. No hay error, no hay
   warning, no hay log. Falla en silencio.
2. **Los tests son unitarios con Mockito**: no ejercitan Hibernate ni el tejido AOP, así
   que ninguno podía notarlo.
3. **Las otras dos capas de defensa lo enmascararon**: el WHERE explícito en JOINs
   (TD-003) y la validación en services siguieron funcionando, así que la aplicación
   "andaba bien" en el uso normal con un solo tenant de prueba.

### Solución aplicada

Pointcut por **anotación** en vez de por nombre de paquete:

```java
@Before("@within(org.springframework.stereotype.Service)")
```

Un renombre o movimiento de paquetes ya no puede romperlo. Además se agregó
`TenantFilterAspectTest`, que parsea el pointcut y verifica que alcance a un `@Service`
real — se validó que ese test **falla** con el pointcut viejo, es decir que tiene poder
de detección real.

### Lección para llevarse

Una refactorización de estructura puede romper **configuración que depende de nombres de
paquete** (pointcuts de AOP, `@ComponentScan`, escaneo de entidades, reglas de ArchUnit)
sin que el compilador ni los tests digan nada. Al reorganizar paquetes hay que auditar
explícitamente todo lo que referencia rutas de paquete como texto.

---

## TD-006: Reportes — sin paginación

**Detectado**: Sprint 6 Fase A
**Severidad**: Baja en PoC, Media en producción

### Síntoma

`ReporteAsistenciaService.reporte()` trae **todas** las asistencias del rango de fechas en una sola query y las carga en memoria. Para 200-400 docentes × 30 días × 5 horarios por semana ≈ 30.000 filas. Manejable en RAM, pero crece linealmente.

### Próximo paso

- Para la pantalla HTML: paginar con `Pageable` (Spring Data).
- Para el CSV: streaming row-by-row al `HttpServletResponse`, sin cargar todo.

---

## TD-008: Límite de códigos por cuenta, no por origen

**Detectado**: al implementar la verificación de correo (ADR-0009)
**Severidad**: Baja en despliegue local, Media expuesto a internet

### Síntoma

`CodigoVerificacionService` limita a cinco pedidos por hora **por cuenta**. Eso frena que alguien bombardee el buzón de una persona concreta, pero no frena a quien recorra muchas cuentas distintas: cada una tiene su propio contador.

### Por qué se dejó así

En un despliegue local, dentro de la red de la institución y con un puñado de cuentas administrativas, el escenario no es realista. Sumar un límite por IP implicaría almacenamiento adicional y decidir qué hacer detrás de un proxy o de una IP compartida, que es exactamente donde estos controles empiezan a bloquear usuarios legítimos.

### Próximo paso

Si el sistema se expone a internet: límite por IP además del límite por cuenta, y un retardo creciente entre pedidos consecutivos del mismo origen.

---

## TD-009: La recuperación depende de que haya un SMTP disponible

**Detectado**: al implementar la recuperación de contraseña (ADR-0009)
**Severidad**: Media

### Síntoma

Sin servidor de correo alcanzable no hay recuperación posible. La aplicación no lo disimula —informa que no se pudo enviar, en vez de decir "revisá tu correo" y dejar a la persona esperando algo que nunca llega— pero el resultado es que queda sin poder recuperar el acceso.

### Mitigación actual

El superadmin conserva la capacidad de resetear contraseñas a mano desde la pantalla de usuarios, así que el camino viejo sigue disponible como respaldo. El hueco real persiste solo para la propia cuenta del superadmin.

### Próximo paso

Un procedimiento de emergencia documentado para recuperar la cuenta institucional sin correo: por ejemplo, un comando de administración que resetee la contraseña desde la consola del servidor, con constancia en el log.
