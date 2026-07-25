package edu.cent35.asistencias.repository;

import edu.cent35.asistencias.model.MotivoCargaManual;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repositorio del catálogo de motivos de carga manual (falla de cámara, docente sin registrar,
 * etc.). Es una tabla de referencia compartida: no se filtra por institución.
 */
public interface MotivoCargaManualRepository extends JpaRepository<MotivoCargaManual, Short> {

    // Motivos activos para mostrar en el selector del form.
    List<MotivoCargaManual> findByActivoTrueOrderByDescripcionAsc();
}
