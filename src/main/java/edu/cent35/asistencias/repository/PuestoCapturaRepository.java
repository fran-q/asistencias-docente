package edu.cent35.asistencias.repository;

import edu.cent35.asistencias.model.PuestoCaptura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio de los equipos autorizados a capturar datos biométricos (ADR-0015).
 *
 * <p><b>Todas las consultas reciben el tenant como parámetro explícito.</b> No es
 * redundante con el filtro de Hibernate: el token es único a nivel de toda la tabla, así
 * que una búsqueda por token sin acotar institución devolvería el puesto sin importar de
 * quién sea. Si el filtro no estuviera activo —un test unitario con Mockito, un hilo que
 * no pasó por el interceptor, una llamada fuera de transacción— una cookie serviría en el
 * tenant equivocado, y lo que abre es la captura de datos biométricos. Ver TD-003 y TD-007.
 */
@Repository
public interface PuestoCapturaRepository extends JpaRepository<PuestoCaptura, Long> {

    /**
     * El puesto habilitado que corresponde a ese token dentro de esa institución. Es la
     * consulta que corre en cada petición a una pantalla de captura, así que decide sola
     * si el equipo pasa: exige el hash, la institución y que siga activo.
     */
    @Query("""
        SELECT p FROM PuestoCaptura p
        WHERE p.tokenHash = :tokenHash
          AND p.institucionId = :institucionId
          AND p.activo = true
    """)
    Optional<PuestoCaptura> habilitadoPorToken(@Param("tokenHash") String tokenHash,
                                               @Param("institucionId") Long institucionId);

    /** Los puestos de una institución, activos primero y después por nombre. */
    @Query("""
        SELECT p FROM PuestoCaptura p
        WHERE p.institucionId = :institucionId
        ORDER BY p.activo DESC, p.nombre ASC
    """)
    List<PuestoCaptura> deInstitucion(@Param("institucionId") Long institucionId);

    /** Un puesto por id, acotado a su institución: evita revocar el de otro tenant. */
    @Query("""
        SELECT p FROM PuestoCaptura p
        WHERE p.id = :id AND p.institucionId = :institucionId
    """)
    Optional<PuestoCaptura> porIdEnInstitucion(@Param("id") Long id,
                                               @Param("institucionId") Long institucionId);

    /** Si ya hay un puesto con ese nombre; el nombre es lo único que los distingue en pantalla. */
    @Query("""
        SELECT COUNT(p) > 0 FROM PuestoCaptura p
        WHERE p.institucionId = :institucionId
          AND LOWER(p.nombre) = LOWER(:nombre)
          AND (:idExcluido IS NULL OR p.id <> :idExcluido)
    """)
    boolean existeNombre(@Param("institucionId") Long institucionId,
                         @Param("nombre") String nombre,
                         @Param("idExcluido") Long idExcluido);

    /** Cuántos puestos habilitados tiene la institución; cero significa que no puede tomar asistencia. */
    @Query("""
        SELECT COUNT(p) FROM PuestoCaptura p
        WHERE p.institucionId = :institucionId AND p.activo = true
    """)
    long contarHabilitados(@Param("institucionId") Long institucionId);

    /**
     * Deja constancia de que el puesto se usó recién.
     *
     * <p>Va como UPDATE suelto y no tocando la entidad porque corre en cada petición del
     * pase —una por segundo mientras la cámara está encendida— y no tiene por qué arrastrar
     * el resto de las columnas ni disparar {@code @UpdateTimestamp} en cada cuadro.
     */
    @Modifying
    @Query("""
        UPDATE PuestoCaptura p SET p.ultimoUsoEn = :ahora
        WHERE p.id = :id AND p.institucionId = :institucionId
    """)
    int registrarUso(@Param("id") Long id,
                     @Param("institucionId") Long institucionId,
                     @Param("ahora") LocalDateTime ahora);
}
