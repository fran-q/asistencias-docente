# Diagramas

Todos los diagramas están en **Mermaid**, dentro de archivos Markdown. Eso quiere decir que **se ven sin instalar nada**: GitHub los dibuja al abrir el archivo, y VS Code también en su vista previa.

| Archivo | Qué contiene |
|---|---|
| [diagramas.md](./diagramas.md) | Clases del dominio, casos de uso y secuencia del pase de asistencia |
| [dfd.md](./dfd.md) | Diagramas de flujo de datos en tres niveles: contexto, procesos y explosión del registro automático |

El **diagrama Entidad-Relación** tiene dos versiones: la original de la cátedra en [1-catedra/Diagrama de base de datos.pdf](../1-catedra/Diagrama%20de%20base%20de%20datos.pdf), y la del modelo tal como quedó implementado en [2-requerimientos/04-diagrama-entidad-relacion.md](../2-requerimientos/04-diagrama-entidad-relacion.md).

## Por qué Mermaid y no PlantUML

Los diagramas estuvieron en PlantUML hasta julio de 2026. El formato es más expresivo —tiene diagrama de casos de uso propio, que Mermaid no— pero exige instalar herramientas para verlos, y en la práctica eso significaba que nadie los abría: en el repositorio se veían como texto plano.

Se migraron a Mermaid y **se eliminaron los `.puml`** en vez de mantener ambos. Convivir dos descripciones del mismo modelo garantiza que tarde o temprano digan cosas distintas, y una documentación que se contradice a sí misma es peor que tener una sola versión algo menos vistosa.

Lo único que se pierde es la notación de casos de uso: en Mermaid se representa como un grafo con los actores y los casos agrupados por dominio. A cambio, el diagrama se ve al abrir el archivo.

## Cómo obtener una imagen

Si hace falta un PNG para imprimir o pegar en un documento:

1. **Desde GitHub** — abrir el archivo, y sobre el diagrama ya renderizado usar el botón de copiar imagen del navegador.
2. **Desde VS Code** — vista previa del Markdown (`Ctrl+Shift+V`), botón derecho sobre el diagrama, guardar imagen.
3. **mermaid.live** — pegar el bloque y exportar a PNG o SVG.

## Convenciones de notación

- **`<<tenant>>`** en una clase marca que extiende `BaseTenantEntity`, es decir que lleva columna `institucion_id` y queda alcanzada por el filtro de Hibernate.
- Los actores con borde doble en el diagrama de casos de uso son **procesos automáticos**, no personas: el motor de reconocimiento, el job de ausencias y el servidor de correo.
- Lo que un diagrama no puede expresar —invariantes, constraints, criterios de desempate— va escrito en prosa debajo de cada uno.

## Al modificar el modelo

Los diagramas describen el código: si cambian las entidades, las reglas de acceso o el flujo del pase, hay que actualizarlos en el mismo cambio. Un diagrama desactualizado no es neutro, **afirma algo falso** sobre el sistema.
