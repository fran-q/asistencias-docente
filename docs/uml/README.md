# Diagramas UML

Diagramas del sistema en formato PlantUML (`.puml`) versionados junto a su renderizado (`.png`).

## Diagramas previstos

| Diagrama | Sprint donde se hace | Archivo |
|---|---|---|
| Casos de uso | S0 | `casos-de-uso.puml` |
| Secuencia - Login | S1 | `secuencia-login.puml` |
| Secuencia - Toma de asistencia automática | S5 | `secuencia-asistencia-automatica.puml` |
| Estados - Asistencia | S5 | `estados-asistencia.puml` |
| Clases - Modelo de dominio | S6 | `clases-dominio.puml` |
| Componentes - Módulos del monolito | S6 | `componentes.puml` |
| Despliegue | S6 | `despliegue.puml` |

El diagrama Entidad-Relación de la base de datos ya está hecho y vive en `docs/2. Diagrama BD Sistema Asistencias.pdf`.

## Cómo trabajar con PlantUML

1. Instalar el plugin **PlantUML integration** en IntelliJ (Settings → Plugins).
2. Editar el `.puml` con preview en vivo.
3. Exportar a PNG desde el preview y commitear ambos archivos.
