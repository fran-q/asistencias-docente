package edu.cent35.asistencias.usuario.domain;

/**
 * Codigos de rol que entiende el sistema. Coincide con la columna
 * {@code codigo} de la tabla {@code roles} (seed en V001__init.sql).
 * <p>
 * Se mantiene como enum aparte de la entidad {@link Rol} para tener
 * type-safety en las comprobaciones de Spring Security
 * ({@code hasRole("ADMIN")}, etc.) y en la logica de negocio.
 */
public enum RolCodigo {

    /** Cuenta raiz de la institucion - gestiona los administradores. */
    SUPERADMIN_INSTITUCION,

    /** Personal administrativo - opera el sistema dia a dia. */
    ADMIN
}
