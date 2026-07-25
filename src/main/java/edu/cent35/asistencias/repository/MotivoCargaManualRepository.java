package edu.cent35.asistencias.repository;

import edu.cent35.asistencias.model.MotivoCargaManual;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MotivoCargaManualRepository extends JpaRepository<MotivoCargaManual, Short> {

    // Motivos activos para mostrar en el selector del form.
    List<MotivoCargaManual> findByActivoTrueOrderByDescripcionAsc();
}
