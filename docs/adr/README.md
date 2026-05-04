# Architectural Decision Records (ADR)

Esta carpeta contiene las decisiones arquitectónicas relevantes del proyecto. Cada decisión es un archivo Markdown con el formato `NNNN-titulo-corto.md`.

## ¿Cuándo escribir un ADR?

Cuando se toma una decisión que:
- Afecta la estructura del sistema (nuevo módulo, división, integración).
- Es difícil de revertir (estructura de BD, framework, lenguaje).
- Tiene alternativas razonables que se descartaron y conviene documentar por qué.

## Formato

Cada ADR contiene:
1. **Estado**: Propuesta / Aceptada / Reemplazada / Obsoleta.
2. **Fecha** y **Decisor**.
3. **Contexto**: qué problema o necesidad la motiva.
4. **Decisión**: qué se decidió.
5. **Consecuencias**: efectos positivos y negativos esperados.
6. **Referencias**: documentos, libros o ADRs relacionados.

## Índice

| Nº | Título | Estado |
|---|---|---|
| 0001 | Monolito Modular organizado por dominio | Aceptada |
| 0002 | Multi-tenancy por discriminator (institucion_id) | Aceptada |
| 0003 | Estrategia de sesión: cookie HTTP clásica | Aceptada |
