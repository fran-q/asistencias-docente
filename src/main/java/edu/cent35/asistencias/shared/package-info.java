/**
 * Infraestructura transversal de la aplicacion.
 * <p>
 * Contiene codigo compartido por todos los modulos de dominio:
 * configuracion, seguridad, multi-tenancy, auditoria, manejo de
 * excepciones, paginacion y utilitarios.
 * <p>
 * Ningun modulo de dominio debe depender de otro modulo de dominio
 * directamente: las dependencias cruzadas pasan siempre por shared.
 */
package edu.cent35.asistencias.shared;
