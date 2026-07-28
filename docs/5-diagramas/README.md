# Diagramas UML

Los diagramas están en dos formatos, y cada uno cumple un propósito:

| Formato | Archivo | Para qué |
|---|---|---|
| **Mermaid** | [diagramas.md](./diagramas.md) y [dfd.md](./dfd.md) | **Se ven sin instalar nada**: GitHub los renderiza al abrir el archivo |
| **PlantUML** | los `.puml` de esta carpeta | Mayor fidelidad y exportación a PNG/SVG, pero requiere herramientas |

Si solo querés *ver* los diagramas, abrí [diagramas.md](./diagramas.md) y listo.

> **Al modificar el modelo hay que actualizar los dos formatos.** Describen lo mismo, y tocar uno solo deja documentación que se contradice a sí misma.

## Diagramas

| Archivo | Qué muestra | Sprint |
|---|---|---|
| `casos-de-uso.puml` | Actores y casos de uso del sistema, agrupados por dominio. | S6 |
| `clases-dominio.puml` | Entidades JPA principales con relaciones, enums y `BaseTenantEntity`. | S6 |
| `secuencia-pase-asistencia.puml` | Flujo completo del pase automático: frame → detección → identificación LBPH → marcado en BD. | S6 |

El **diagrama Entidad-Relación** tiene dos versiones: la original de la cátedra en
[1-catedra/Diagrama de base de datos.pdf](../1-catedra/Diagrama%20de%20base%20de%20datos.pdf),
y la del modelo tal como quedó implementado en
[2-requerimientos/04-diagrama-entidad-relacion.md](../2-requerimientos/04-diagrama-entidad-relacion.md).

## Cómo exportar los `.puml` a imagen

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
