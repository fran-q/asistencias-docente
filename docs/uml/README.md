# Diagramas UML

Diagramas del sistema en formato **PlantUML** (texto plano versionado).
Cada `.puml` se renderiza a PNG/SVG con cualquier herramienta PlantUML.

## Diagramas

| Archivo | Qué muestra | Sprint |
|---|---|---|
| `casos-de-uso.puml` | Actores y casos de uso del sistema, agrupados por dominio. | S6 |
| `clases-dominio.puml` | Entidades JPA principales con relaciones, enums y `BaseTenantEntity`. | S6 |
| `secuencia-pase-asistencia.puml` | Flujo completo del pase automático: frame → detección → identificación LBPH → marcado en BD. | S6 |

El **diagrama Entidad-Relación de la BD** ya está hecho y vive en
`docs/2. Diagrama BD Sistema Asistencias.pdf`.

## Cómo renderizar

### Opción A — Servidor PlantUML público (más rápido)
1. Abrir https://www.plantuml.com/plantuml/uml/
2. Copiar el contenido del `.puml`.
3. Descargar el PNG generado.

### Opción B — IntelliJ IDEA
1. Settings → Plugins → instalar **"PlantUML integration"**.
2. Abrir cualquier `.puml`: la vista previa aparece a la derecha.
3. Botón derecho sobre el preview → "Save Diagram" → PNG.

### Opción C — CLI con Java
```bash
# Descargar plantuml.jar de https://plantuml.com/download
java -jar plantuml.jar docs/uml/*.puml
# Genera un .png al lado de cada .puml
```

### Opción D — VS Code
1. Instalar la extensión **"PlantUML"** (jebbs).
2. Abrir el `.puml` → `Alt+D` → vista previa.
3. Comando "PlantUML: Export Current Diagram" → PNG.

## Convenciones de notación usadas

- **`<<tenant>>`** en una clase marca que extiende `BaseTenantEntity`
  (tiene columna `institucion_id`).
- **`<<system>>`** en un actor marca un componente automatizado.
- Las notas `note` describen invariantes o decisiones de diseño
  relevantes (idempotencia, constraints, etc.).
