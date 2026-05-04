package edu.cent35.asistencias.institucion.application;

import edu.cent35.asistencias.institucion.domain.Institucion;
import edu.cent35.asistencias.institucion.infrastructure.InstitucionRepository;
import edu.cent35.asistencias.institucion.web.InstitucionFormDto;
import edu.cent35.asistencias.shared.multitenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MiInstitucionServiceTest {

    private static final Long TENANT_ID = 42L;

    @Mock private InstitucionRepository institucionRepository;
    @InjectMocks private MiInstitucionService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("getMiInstitucion: fetch por TenantContext.getRequired (ignora id externo)")
    void getMiInstitucion_ok() {
        Institucion inst = Institucion.builder()
            .id(TENANT_ID)
            .nombre("Test Inst")
            .activo(true)
            .build();
        when(institucionRepository.findById(TENANT_ID)).thenReturn(Optional.of(inst));

        Institucion devuelto = service.getMiInstitucion();

        assertThat(devuelto).isSameAs(inst);
        verify(institucionRepository).findById(TENANT_ID);
    }

    @Test
    @DisplayName("getMiInstitucion: lanza si la institucion del tenant no existe")
    void getMiInstitucion_notFound() {
        when(institucionRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMiInstitucion())
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining(String.valueOf(TENANT_ID));
    }

    @Test
    @DisplayName("actualizar: aplica los cambios del DTO sobre la institucion del tenant")
    void actualizar_ok() {
        Institucion inst = Institucion.builder()
            .id(TENANT_ID)
            .nombre("Viejo Nombre")
            .activo(true)
            .build();
        when(institucionRepository.findById(TENANT_ID)).thenReturn(Optional.of(inst));
        when(institucionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InstitucionFormDto dto = InstitucionFormDto.builder()
            .nombre("Nombre Actualizado")
            .cuit("30-12345678-9")
            .direccion("Av. Siempre Viva 742")
            .emailContacto("contacto@test.com")
            .telefonoContacto("+54 9 11 12345678")
            .build();

        Institucion actualizado = service.actualizar(dto);

        assertThat(actualizado.getNombre()).isEqualTo("Nombre Actualizado");
        assertThat(actualizado.getCuit()).isEqualTo("30-12345678-9");
        assertThat(actualizado.getEmailContacto()).isEqualTo("contacto@test.com");
        verify(institucionRepository).save(inst);
    }

    @Test
    @DisplayName("actualizar: blank-to-null en campos opcionales")
    void actualizar_blankToNull() {
        Institucion inst = Institucion.builder()
            .id(TENANT_ID)
            .nombre("X")
            .cuit("30-12345678-9")
            .activo(true)
            .build();
        when(institucionRepository.findById(TENANT_ID)).thenReturn(Optional.of(inst));
        when(institucionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InstitucionFormDto dto = InstitucionFormDto.builder()
            .nombre("X")
            .cuit("   ")           // whitespace -> null
            .direccion("")          // vacio -> null
            .emailContacto(null)    // null directo
            .build();

        Institucion actualizado = service.actualizar(dto);

        assertThat(actualizado.getCuit()).isNull();
        assertThat(actualizado.getDireccion()).isNull();
        assertThat(actualizado.getEmailContacto()).isNull();
    }
}
