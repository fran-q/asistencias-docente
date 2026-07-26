package edu.cent35.asistencias.repository;

import edu.cent35.asistencias.model.CodigoVerificacion;
import edu.cent35.asistencias.model.PropositoCodigo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repositorio de los códigos de un solo uso. Las consultas llevan el usuario como parámetro
 * explícito y no dependen del filtro de tenant, porque la recuperación de contraseña se
 * resuelve antes del login, cuando todavía no hay institución en contexto.
 */
@Repository
public interface CodigoVerificacionRepository extends JpaRepository<CodigoVerificacion, Long> {

    // Ultimo codigo emitido a esa persona para ese proposito, usado o no.
    @Query("""
        SELECT c FROM CodigoVerificacion c
        WHERE c.usuario.id = :usuarioId AND c.proposito = :proposito
        ORDER BY c.creadoEn DESC, c.id DESC
        LIMIT 1
    """)
    Optional<CodigoVerificacion> ultimoDe(@Param("usuarioId") Long usuarioId,
                                          @Param("proposito") PropositoCodigo proposito);

    // Cuantos codigos se pidieron desde un instante; sostiene el limite de reenvios.
    @Query("""
        SELECT COUNT(c) FROM CodigoVerificacion c
        WHERE c.usuario.id = :usuarioId AND c.proposito = :proposito
          AND c.creadoEn >= :desde
    """)
    long contarDesde(@Param("usuarioId") Long usuarioId,
                     @Param("proposito") PropositoCodigo proposito,
                     @Param("desde") LocalDateTime desde);

    // Invalida de una los codigos pendientes: al emitir uno nuevo, los anteriores dejan de servir.
    @Modifying
    @Query("""
        UPDATE CodigoVerificacion c SET c.usadoEn = :ahora
        WHERE c.usuario.id = :usuarioId AND c.proposito = :proposito AND c.usadoEn IS NULL
    """)
    int invalidarPendientes(@Param("usuarioId") Long usuarioId,
                            @Param("proposito") PropositoCodigo proposito,
                            @Param("ahora") LocalDateTime ahora);

    // Borra los vencidos hace rato; evita que la tabla crezca sin control.
    @Modifying
    @Query("DELETE FROM CodigoVerificacion c WHERE c.expiraEn < :limite")
    int borrarVencidosAntesDe(@Param("limite") LocalDateTime limite);
}
