package edu.cent35.asistencias.repository;

import edu.cent35.asistencias.model.AsistenciaManual;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AsistenciaManualRepository extends JpaRepository<AsistenciaManual, Long> {

    Optional<AsistenciaManual> findByAsistenciaId(Long asistenciaId);
}
