package edu.cent35.asistencias.dto;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Racha de identificaciones seguidas del mismo docente frente a la cámara del pase. Vive en
 * la sesión HTTP del operador, así que se limpia sola cuando esa sesión termina y no puede
 * manipularse desde el navegador.
 */
public class ConfirmacionIdentidad implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long docente;
    private final Deque<Long> instantes = new ArrayDeque<>();

    // Docente de la racha en curso, o null si no hay ninguna.
    public Long getDocente() {
        return docente;
    }

    // Cuántas lecturas seguidas lleva acumuladas la racha.
    public int getLecturas() {
        return instantes.size();
    }

    // Cuántos milisegundos separan la primera lectura de la última.
    public long getDuracionMs() {
        if (instantes.size() < 2) return 0;
        return instantes.getLast() - instantes.getFirst();
    }

    // Suma una lectura a la racha en curso.
    public void sumar(long instante) {
        instantes.addLast(instante);
    }

    // Arranca una racha nueva para otro docente, descartando la anterior.
    public void reiniciarCon(Long docenteId, long instante) {
        docente = docenteId;
        instantes.clear();
        instantes.addLast(instante);
    }

    // Borra la racha. Se usa al marcar y cuando el rostro desaparece del cuadro.
    public void limpiar() {
        docente = null;
        instantes.clear();
    }

    // Instante de la última lectura, o 0 si la racha está vacía.
    public long ultimoInstante() {
        return instantes.isEmpty() ? 0L : instantes.getLast();
    }
}
