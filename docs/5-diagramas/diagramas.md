# Diagramas del sistema

Diagramas del sistema en **Mermaid**, que GitHub renderiza al abrir este archivo. No hace falta instalar nada ni exportar imágenes: se ven acá mismo, y VS Code también los dibuja en su vista previa de Markdown.

Esta es la **única fuente** de los diagramas. Antes convivían con una versión en PlantUML que había que renderizar aparte; se eliminó porque tener dos descripciones del mismo modelo garantiza que tarde o temprano digan cosas distintas.

El **diagrama de flujo de datos** vive aparte, en [dfd.md](./dfd.md), y también está en Mermaid.

---

## 1. Clases del dominio

Entidades JPA con sus relaciones. Las marcadas como `tenant` extienden `BaseTenantEntity`, es decir que llevan columna `institucion_id` y quedan alcanzadas por el filtro de Hibernate.

```mermaid
classDiagram
    class BaseTenantEntity {
        <<abstract>>
        Long institucionId
    }

    class Institucion {
        Long id
        String nombre
        String cuit
        Boolean activo
    }
    class Usuario {
        <<tenant>>
        Long id
        String username
        String email
        LocalDateTime emailVerificadoEn
        String passwordHash
        LocalDateTime ultimoLogin
        Boolean activo
    }
    class Rol {
        Short id
        String codigo
    }
    class CodigoVerificacion {
        <<tenant>>
        Long id
        String email
        String codigoHash
        LocalDateTime expiraEn
        LocalDateTime usadoEn
        Short intentos
    }

    class Carrera {
        <<tenant>>
        Long id
        String codigo
        String nombre
        Boolean activo
    }
    class Materia {
        <<tenant>>
        Long id
        String codigo
        String nombre
        Boolean activo
    }
    class Comision {
        Long id
        String codigo
        Boolean activo
    }
    class Horario {
        Long id
        Byte diaSemana
        LocalTime horaInicio
        LocalTime horaFin
        Short toleranciaMin
        Boolean activo
    }

    class Docente {
        <<tenant>>
        Long id
        String dni
        String legajo
        String nombre
        String apellido
        Boolean activo
    }
    class ConsentimientoBiometrico {
        Long id
        String versionTerminos
        LocalDateTime fechaConsentimiento
        LocalDateTime fechaRevocacion
        Boolean vigente
    }
    class ModeloFacial {
        Long id
        Bytes embeddingCifrado
        String algoritmo
        Short dimensiones
        Boolean activo
    }

    class Asistencia {
        <<tenant>>
        Long id
        LocalDate fecha
        LocalTime horaRegistrada
        BigDecimal confianza
    }
    class AsistenciaManual {
        Long id
        String detalleAdicional
    }
    class MotivoCargaManual {
        Short id
        String codigo
        String descripcion
    }
    class JustificacionAusencia {
        Long id
        String motivo
        String documentoUrl
    }

    Usuario --|> BaseTenantEntity
    Carrera --|> BaseTenantEntity
    Materia --|> BaseTenantEntity
    Docente --|> BaseTenantEntity
    Asistencia --|> BaseTenantEntity
    CodigoVerificacion --|> BaseTenantEntity

    Institucion "1" --> "*" Usuario
    Usuario "*" --> "1" Rol
    Usuario "1" --> "*" CodigoVerificacion

    Carrera "1" --> "*" Materia
    Materia "1" --> "*" Comision
    Comision "1" --> "*" Horario
    Materia "*" --> "0..1" Docente : titular
    Comision "*" --> "0..1" Docente : asignado

    Docente "1" --> "*" ConsentimientoBiometrico
    Docente "1" --> "*" ModeloFacial

    Asistencia "*" --> "1" Docente
    Asistencia "*" --> "1" Comision
    Asistencia "*" --> "1" Horario
    Asistencia "*" --> "0..1" ModeloFacial
    Asistencia "1" --> "0..1" AsistenciaManual
    Asistencia "1" --> "0..1" JustificacionAusencia
    AsistenciaManual "*" --> "1" MotivoCargaManual
```

**Invariantes que no se ven en el diagrama:**

- `UNIQUE (docente_id, horario_id, fecha)` sobre `asistencias` es lo que garantiza la idempotencia del pase: si el docente se queda frente a la cámara, la marca no se duplica.
- **`AUSENTE` es híbrido.** El listado la calcula al vuelo para cubrir la ventana entre el fin de la clase y la próxima corrida del job, y después el job la persiste de verdad (`metodo=AUTOMATICO`, `hora=hora_fin`). **Solo la persistida se puede justificar**, porque una fila calculada no existe todavía en la base.
- `Comision` y `Horario` **no** son tenant-scoped: su institución se deduce de la materia padre, por eso sus consultas van por JOIN.
- El código de verificación se guarda **hasheado**; `codigoHash` nunca contiene el valor que viajó por correo.

---

## 2. Casos de uso

Mermaid no tiene diagrama de casos de uso, así que se representa como grafo: los actores a la izquierda y los casos agrupados por dominio.

```mermaid
flowchart LR
    INST(["Administrador de institución<br/>rol INSTITUCION"])
    ADMIN(["Administrador operativo<br/>rol ADMIN"])
    DOC(["Docente<br/>no tiene cuenta"])
    SYS[["Reconocimiento facial<br/>JavaCV + LBPH"]]
    JOB[["Tarea programada<br/>job de ausencias"]]
    SMTP[["Servidor de correo<br/>SMTP"]]

    subgraph GI["Gestión de la institución"]
        UC1["Editar datos de la institución"]
        UC2["Crear y dar de baja administradores"]
    end

    subgraph CU["Cuenta propia"]
        UC18["Verificar el correo de mi cuenta"]
        UC19["Recuperar contraseña olvidada"]
    end

    subgraph GA["Gestión académica"]
        UC3["Gestionar carreras"]
        UC4["Gestionar materias"]
        UC5["Gestionar comisiones"]
        UC6["Gestionar horarios"]
        UC7["Ver grilla semanal"]
    end

    subgraph DB["Docentes y biometría"]
        UC8["Gestionar docentes"]
        UC9["Cargar consentimiento"]
        UC10["Revocar consentimiento"]
        UC11["Registrar rostro"]
        UC11b["Re-registrar rostro"]
        UC12["Suprimir datos biométricos<br/>derecho ARCO"]
    end

    subgraph AS["Asistencia"]
        UC13["Pasar asistencia automática"]
        UC13b["Identificar rostro y marcar"]
        UC14["Cargar asistencia manual"]
        UC20["Generar ausencias de clases terminadas"]
        UC15["Justificar ausencia"]
        UC16["Ver listado del día"]
        UC17["Exportar reporte CSV"]
    end

    INST --> GI
    INST --> GA
    INST --> DB
    INST --> AS
    INST --> CU
    ADMIN --> GA
    ADMIN --> DB
    ADMIN --> AS
    ADMIN --> CU

    DOC -. presenta su rostro .-> UC13
    JOB -- corre cada 30 min --> UC20
    SYS -. ejecuta LBPH .-> UC13b
    UC18 -. envía el código .-> SMTP
    UC19 -. envía el código .-> SMTP

    UC13 -. incluye .-> UC13b
    UC11 -. requiere consentimiento activo .-> UC9
    UC13 -. requiere modelo facial activo .-> UC11
    UC12 -. destruye todos los modelos<br/>incluidos los históricos .-> UC11
    UC15 -. requiere una AUSENTE persistida .-> UC20
    UC14 -. o la crea a mano .-> UC20
```

**Lo que conviene señalar al presentarlo:**

- El **docente es sujeto pasivo**: no tiene cuenta ni inicia sesión. Aparece porque presenta su rostro, no porque opere el sistema. Por eso tampoco se le verifica el correo.
- Solo el rol INSTITUCION accede a la gestión de la institución y de los usuarios; todo lo demás lo comparten los dos roles.
- Hay **tres actores que no son personas**: el motor de reconocimiento, el job que genera las ausencias cada 30 minutos y el servidor de correo. Reconocerlos como actores es lo que deja ver que el sistema hace cosas sin que nadie las pida.
- Para **justificar** una ausencia hace falta que exista como fila persistida, y a eso se llega por dos caminos: la genera el job, o la carga un administrador a mano.

---

## 3. Pase de asistencia automático

Del fotograma que toma la cámara hasta la marca en la base.

```mermaid
sequenceDiagram
    autonumber
    actor Op as Operador
    participant JS as Navegador
    participant PC as PaseAsistenciaController
    participant PS as PaseAsistenciaService
    participant IFS as IdentificacionFacialService
    participant AS as AsistenciaService
    participant DB as MariaDB

    Op->>JS: Iniciar pase (enciende la cámara y arranca el loop)

    loop Por cada fotograma
        JS->>PC: POST /asistencia/pase/marcar
        PC->>PS: pasar(bytes)
        PS->>IFS: identificar(bytes)
        Note over IFS: Detecta el rostro, lo normaliza y lo<br/>compara contra los modelos del tenant.<br/>Los modelos quedan en caché descifrados.

        alt Distancia mayor al umbral
            IFS-->>PS: No reconocido
            PS-->>JS: Recuadro rojo con el motivo
        else Distancia dentro del umbral
            IFS-->>PS: Docente identificado
            PS->>AS: marcarAutomatica(docente, distancia)

            alt Sin clase en la ventana horaria
                AS-->>PS: No hay clase ahora
                PS-->>JS: Recuadro verde con el motivo
            else Hay clase en curso
                AS->>DB: ¿Existe marca para docente, horario y fecha?

                alt Ya estaba marcada
                    DB-->>AS: Marca existente
                    AS-->>PS: Ya estaba (idempotencia)
                    PS-->>JS: Recuadro azul y pausa de 3 s
                else Todavía no
                    AS->>DB: Guardar PRESENTE o TARDE
                    Note over AS,DB: Si dos peticiones compiten, el UNIQUE de la<br/>base rechaza la segunda: se relee y se informa<br/>como ya marcada, sin error para el operador.
                    DB-->>AS: Asistencia persistida
                    AS-->>PS: Marca creada
                    PS-->>JS: Recuadro verde y pausa de 3 s
                end
            end
        end
    end
```

**El detalle que más se pregunta:** cuando hay más de un horario dentro de la ventana —clases consecutivas, o el mismo docente en dos comisiones a la misma hora— el sistema no elige al azar. Aplica tres criterios en orden: prefiere los horarios sin marca previa, después el de hora de inicio más cercana, y ante empate exacto el de menor id, para que la decisión sea reproducible (RF-18, ADR-0008).
