package com.fernando.gastos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de {@link RepositorioGastosCsv} y {@link Csv}.
 *
 * <p>El bloque "ida y vuelta" es el que importa: ahi vivia el defecto de guardar
 * los gastos y perder los presupuestos, que apagaba las alertas de un dia para
 * otro sin decir nada.
 *
 * <p>La prueba fuerte no comprueba un campo: comprueba que
 * {@code cargar(guardar(g))} produce un gestor <b>indistinguible</b> del
 * original — mismos gastos, mismos presupuestos, mismas alertas y hasta el mismo
 * reporte en texto. Una prueba de ida y vuelta cubre de una sola vez todos los
 * campos que alguien pueda olvidarse de escribir, incluidos los que todavia no
 * existen.
 */
class RepositorioGastosCsvTest {

    private static final YearMonth MARZO = YearMonth.of(2026, 3);

    private GestorGastos gestor;

    @BeforeEach
    void prepararGestor() {
        gestor = new GestorGastos();
    }

    private void gasto(int dia, String descripcion, String monto, Categoria categoria) {
        gestor.registrar(new Gasto(LocalDate.of(2026, 3, dia), descripcion,
                new BigDecimal(monto), categoria));
    }

    // ---------------------------------------------------------------
    //  Ida y vuelta: donde estaba el defecto principal
    // ---------------------------------------------------------------

    /**
     * LA PRUEBA QUE DOCUMENTA EL DEFECTO CORREGIDO.
     *
     * <p>La version anterior guardaba los gastos y no los presupuestos. Al
     * cargar, las alertas desaparecian: el programa arrancaba diciendo que todo
     * estaba dentro del presupuesto porque ya no sabia cual era el presupuesto.
     */
    @Test
    void guardarYcargarConservaLosPresupuestosYlasAlertas() throws IOException {
        gasto(1, "cafe", "25.50", Categoria.COMIDA);
        gestor.definirPresupuesto(Categoria.COMIDA, new BigDecimal("20.00"));

        assertTrue(gestor.presupuestoExcedido(MARZO, Categoria.COMIDA));
        assertEquals(List.of(Categoria.COMIDA), gestor.alertasDePresupuesto(MARZO));

        GestorGastos vuelta = idaYvuelta(gestor);

        assertEquals(gestor.presupuestos(), vuelta.presupuestos(), "los presupuestos tienen que volver");
        assertTrue(vuelta.presupuestoExcedido(MARZO, Categoria.COMIDA), "y con ellos las alertas");
        assertEquals(List.of(Categoria.COMIDA), vuelta.alertasDePresupuesto(MARZO));
    }

    /** Todo lo observable tiene que sobrevivir a la ida y vuelta, no solo los gastos. */
    @Test
    void laIdaYvueltaConservaTodoElEstado() throws IOException {
        gasto(1, "cafe", "25.50", Categoria.COMIDA);
        gasto(3, "bus", "12.00", Categoria.TRANSPORTE);
        gasto(15, "medicina", "340.75", Categoria.SALUD);
        gestor.definirPresupuesto(Categoria.COMIDA, new BigDecimal("1500.00"));
        gestor.definirPresupuesto(Categoria.SALUD, new BigDecimal("300.00"));

        GestorGastos vuelta = idaYvuelta(gestor);

        assertEquals(gestor.gastos(), vuelta.gastos());
        assertEquals(gestor.presupuestos(), vuelta.presupuestos());
        assertEquals(gestor.totalDe(MARZO), vuelta.totalDe(MARZO));
        assertEquals(gestor.totalesPorCategoria(MARZO), vuelta.totalesPorCategoria(MARZO));
        assertEquals(gestor.alertasDePresupuesto(MARZO), vuelta.alertasDePresupuesto(MARZO));
        assertEquals(gestor.reporteMensual(MARZO), vuelta.reporteMensual(MARZO),
                "hasta el reporte completo tiene que salir identico");
    }

    @Test
    void unGestorVacioIdaYvuelveVacio() throws IOException {
        GestorGastos vuelta = idaYvuelta(gestor);
        assertTrue(vuelta.gastos().isEmpty());
        assertTrue(vuelta.presupuestos().isEmpty());
    }

    /** Los presupuestos se escriben antes que los gastos, para que las alertas ya esten activas al leer. */
    @Test
    void elArchivoPoneLosPresupuestosAntesQueLosGastos() throws IOException {
        gasto(1, "cafe", "25.50", Categoria.COMIDA);
        gestor.definirPresupuesto(Categoria.COMIDA, new BigDecimal("20.00"));

        Path archivo = Files.createTempFile("gastos", ".csv");
        new RepositorioGastosCsv().guardar(gestor, archivo);
        List<String> lineas = Files.readAllLines(archivo, StandardCharsets.UTF_8);

        assertEquals("tipo;fecha;categoria;monto;descripcion", lineas.get(0));
        assertTrue(lineas.get(1).startsWith("PRESUPUESTO;"), lineas.get(1));
        assertTrue(lineas.get(2).startsWith("GASTO;"), lineas.get(2));
    }

    @Test
    void unArchivoMalFormadoDaUnErrorQueDiceLaLinea() throws IOException {
        Path archivo = Files.createTempFile("gastos", ".csv");
        Files.write(archivo, List.of(
                "tipo;fecha;categoria;monto;descripcion",
                "GASTO;2026-03-01;COMIDA;25.50;cafe",
                "GASTO;no-es-fecha;COMIDA;10.00;pan"), StandardCharsets.UTF_8);

        IOException e = assertThrows(IOException.class, () -> new RepositorioGastosCsv().cargar(archivo));
        assertTrue(e.getMessage().contains("Linea 3"), e.getMessage());
    }

    // ---------------------------------------------------------------
    //  CSV: escapar en vez de prohibir
    // ---------------------------------------------------------------

    /**
     * EL SEGUNDO DEFECTO DEL REPOSITORIO.
     *
     * <p>La version anterior lanzaba una excepcion si la descripcion contenia el
     * separador: {@code "cafe; pan"} era un gasto valido en memoria que no se
     * podia guardar. El formato tiene que aguantar los datos, no al reves.
     */
    @Test
    void unaDescripcionConElSeparadorSeGuardaYvuelveIgual() throws IOException {
        gasto(1, "cafe; pan; leche", "30.00", Categoria.COMIDA);

        GestorGastos vuelta = idaYvuelta(gestor);

        assertEquals("cafe; pan; leche", vuelta.gastos().get(0).descripcion());
    }

    /** Comillas, separadores y los dos juntos. */
    @Test
    void elCsvSobreviveAcomillasYseparadores() {
        for (String original : List.of(
                "simple",
                "con; separador",
                "con \"comillas\"",
                "\"empieza con comilla\"",
                "todo junto: \"a; b\"; c",
                "")) {
            List<String> vuelta = Csv.partir(Csv.unir(original, "x"));
            assertEquals(List.of(original, "x"), vuelta, "no volvio: [" + original + "]");
        }
    }

    @Test
    void soloSeEntrecomillaLoQueHaceFalta() {
        assertEquals("simple", Csv.escapar("simple"));
        assertEquals("\"con; separador\"", Csv.escapar("con; separador"));
        assertEquals("\"con \"\"comillas\"\"\"", Csv.escapar("con \"comillas\""));
    }

    @Test
    void unasComillasSinCerrarSeRechazan() {
        assertThrows(IllegalArgumentException.class, () -> Csv.partir("\"sin cerrar;x"));
    }

    // ---------------------------------------------------------------
    //  Ayudante
    // ---------------------------------------------------------------

    private GestorGastos idaYvuelta(GestorGastos original) throws IOException {
        Path archivo = Files.createTempFile("gastos", ".csv");
        RepositorioGastosCsv repositorio = new RepositorioGastosCsv();
        repositorio.guardar(original, archivo);
        return repositorio.cargar(archivo);
    }
}
