package edu.cent35.asistencias.repository;

import edu.cent35.asistencias.model.JustificacionAusencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JustificacionAusenciaRepository extends JpaRepository<JustificacionAusencia, Long> {

    Optional<JustificacionAusencia> findByAsistenciaId(Long asistenciaId);
}
