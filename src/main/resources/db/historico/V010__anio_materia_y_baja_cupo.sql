-- =============================================================================
--  V010__anio_materia_y_baja_cupo.sql
--
--  Tres cambios del plan de estudios y uno de limpieza:
--
--    1. carreras.duracion_anios: cuantos anios dura la carrera.
--    2. materias.anio: en que anio de esa carrera se cursa la materia.
--    3. comisiones.cupo: se elimina.
--
--  Por que el anio va en la materia y no en la comision. El anio es una
--  propiedad del plan de estudios: "Analisis I es de primero" no depende de si
--  se dicta a la manana o a la noche. Ponerlo en la comision obligaria a
--  repetir el mismo dato en cada una y habilitaria que dos comisiones de la
--  misma materia declararan anios distintos, que es un estado imposible.
--
--  Por que la duracion vive en la carrera. Sin ella el anio de la materia
--  seria un entero suelto y nada impediria cargar una materia de "quinto" en
--  una tecnicatura de tres anios. Teniendola, el limite superior sale del
--  propio plan y la validacion es una comparacion, no una constante inventada.
--
--  El default de 3 anios no es una opinion sobre las carreras existentes: es
--  el unico valor que permite agregar una columna NOT NULL sobre filas que ya
--  estan. Las carreras cargadas hay que revisarlas a mano; el formulario ya
--  pide el dato para las nuevas.
--
--  Por que el cupo se borra en vez de quedarse esperando. Nadie lo lee ni lo
--  escribe: no hay inscripcion de alumnos en el sistema, asi que el numero no
--  llegaba a compararse contra nada. Una columna muerta es peor que no
--  tenerla, porque aparenta que la funcionalidad existe a medias. Cuando la
--  inscripcion se implemente de verdad va a necesitar su propio diseno --con
--  inscriptos, fechas y estados-- y esta columna suelta no le va a servir.
--
--  Las dos columnas nuevas son SMALLINT y no TINYINT, aunque un anio entre en un
--  byte: Hibernate mapea Short a SMALLINT y valida el esquema al arrancar, asi
--  que un TINYINT hace que la aplicacion no levante. El byte que se ahorra no
--  paga tener que explicar esa diferencia cada vez que alguien la lea.
-- =============================================================================

-- 1) Duracion de la carrera. Acotada por la base y no solo por el formulario:
--    estas filas tambien se escriben desde migraciones y correcciones manuales.
ALTER TABLE carreras
    ADD COLUMN duracion_anios SMALLINT NOT NULL DEFAULT 3
        COMMENT 'Cuantos anios dura la carrera. Acota el anio de sus materias.'
        AFTER nombre;

ALTER TABLE carreras
    ADD CONSTRAINT ck_carreras_duracion_razonable
        CHECK (duracion_anios BETWEEN 1 AND 10);

-- 2) Anio de cursada de la materia dentro de su carrera.
ALTER TABLE materias
    ADD COLUMN anio SMALLINT NOT NULL DEFAULT 1
        COMMENT 'Anio de la carrera en el que se cursa esta materia.'
        AFTER nombre;

ALTER TABLE materias
    ADD CONSTRAINT ck_materias_anio_positivo
        CHECK (anio >= 1);

-- Que el anio no exceda la duracion de su carrera no se puede expresar con un
-- CHECK: MariaDB no admite subconsultas dentro de CHECK. Queda como regla del
-- servicio (MateriaService), que es donde se valida contra la carrera elegida.

-- 3) Baja del cupo. Primero la restriccion, que si no bloquea el DROP.
ALTER TABLE comisiones
    DROP CONSTRAINT ck_comisiones_cupo_positivo;

ALTER TABLE comisiones
    DROP COLUMN cupo;
