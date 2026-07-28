# Documentación del proyecto

Todo el material escrito del sistema de asistencias está acá, en una sola carpeta. Las subcarpetas están numeradas en el orden en que tiene sentido leerlas: de lo que se pidió, a lo que se definió, a cómo se construyó.

> **Lo único que no está acá** son las credenciales de desarrollo y el dossier personal de preparación de la defensa, que quedan fuera del repositorio a propósito.

## Mapa

| Carpeta | Qué contiene | Para qué sirve |
|---|---|---|
| [1-catedra](./1-catedra) | Guía del proyecto, requerimientos y diagrama de base de datos originales | El punto de partida: lo que pidió la cátedra, sin modificar |
| [2-requerimientos](./2-requerimientos) | Requerimientos relevados, alcance, DFD, DER y casos de uso | Lo que se definió construir, con sus versiones en Word y PDF |
| [3-legal](./3-legal) | Consentimiento biométrico y cumplimiento de la Ley 25.326 | El marco que condiciona todo el tratamiento de datos faciales |
| [4-arquitectura](./4-arquitectura) | Los 9 ADR, la referencia técnica y la deuda técnica | Por qué el sistema es como es, decisión por decisión |
| [5-diagramas](./5-diagramas) | Casos de uso, clases de dominio, secuencia y DFD | Se ven directo en [diagramas.md](./5-diagramas/diagramas.md), sin instalar nada |
| [6-manuales](./6-manuales) | Manual del administrador y manual técnico | Cómo se usa y cómo se instala |
| [7-informes](./7-informes) | Correcciones aplicadas y protocolo de calibración | El estado del proyecto y lo que queda por medir |
| [8-defensa](./8-defensa) | Apuntes de estudio y guion del video | Material para preparar la presentación |
| [9-imprimibles](./9-imprimibles) | Versiones PDF listas para imprimir | Para entregar en papel |

## Por dónde empezar, según lo que necesites

**Si querés entender el proyecto desde cero** → [8-defensa/apuntes-entender-el-proyecto.md](./8-defensa/apuntes-entender-el-proyecto.md). Está escrito para leerse de corrido junto al código fuente.

**Si necesitás el detalle técnico** → [4-arquitectura/referencia-tecnica.md](./4-arquitectura/referencia-tecnica.md). Es la radiografía completa: capas, flujos, esquema de base de datos.

**Si te preguntás por qué algo está hecho de determinada manera** → [4-arquitectura/adr](./4-arquitectura/adr). Cada decisión no obvia tiene su registro, con las alternativas que se descartaron y por qué.

**Si vas a instalar o poner a andar el sistema** → [6-manuales/manual-tecnico.md](./6-manuales/manual-tecnico.md).

**Si vas a usar el sistema** → [6-manuales/manual-administrador.md](./6-manuales/manual-administrador.md).

## Qué está al día y qué no

| Documento | Estado |
|---|---|
| ADR 0001 a 0009 | Al día |
| Referencia técnica | Al día |
| Apuntes de estudio | Al día |
| Deuda técnica | Al día |
| Manuales | Al día |
| Protocolo de calibración | **Escrito, pero sin ejecutar.** Falta la sesión con cámara: hasta entonces el umbral de reconocimiento es un valor por defecto, no uno medido |
| Diagramas | Al día y visibles sin instalar nada, en [diagramas.md](./5-diagramas/diagramas.md) |
| Política de privacidad y procedimiento ARCO | No escritos; ver [3-legal](./3-legal) |

## Cómo mantenerla

- **Una decisión no obvia se registra en un ADR.** Los ADR no se editan una vez aceptados: si algo cambia, se escribe uno nuevo que reemplace al anterior, como pasó con el 0001 y el 0006.
- **El consentimiento vive en el código**, en `TextoConsentimiento.java`, y la copia de `3-legal` se regenera desde ahí. Si cambia el texto hay que incrementar la versión.
- **Los PDF de `9-imprimibles` se generan** desde los `.md`; no se editan a mano.
