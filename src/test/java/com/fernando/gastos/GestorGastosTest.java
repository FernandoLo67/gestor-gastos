package com.fernando.gastos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de {@link GestorGastos}: totales, orden, presupuestos y validaciones.
 *
 * <p>La persistencia se prueba aparte, en {@link RepositorioGastosCsvTest}: ahi
 * vivia el defecto de guardar los gastos y perder los presupuestos.
 *
 * <p>Los importes se comparan con {@code compareTo} cuando lo que interesa es el
 * valor, y con {@code toPlainString} cuando lo que interesa es <b>la escala</b>.
 * {@code BigDecimal.equals} compara las dos cosas a la vez, que es justo lo que
 * no se quiere para afirmar una sola.
 */
class GestorGastosTest {

    private static final YearMonth MARZO = YearMonth.of(2026, 3);
    private static final YearMonth ABRIL = YearMonth.of(2026, 4);

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
    //  Escala de los importes
    // ---------------------------------------------------------------

    /**
     * EL DEFECTO DE FORMATO.
     *
     * <p>Las sumas partian de {@code BigDecimal.ZERO}, que tiene escala 0, asi
     * que el total de un mes sin gastos salia como {@code "0"} y el de un mes con
     * gastos como {@code "400.00"}. El mismo metodo, dos formatos de dinero.
     */
    @Test
    void todosLosTotalesTienenDosDecimales() {
        assertEquals("0.00", gestor.total().toPlainString(), "sin ningun gasto");
        assertEquals("0.00", gestor.totalDe(MARZO).toPlainString(), "en un mes vacio");
        assertEquals("0.00", gestor.totalDe(MARZO, Categoria.COMIDA).toPlainString());
        assertEquals("0.00", gestor.promedioDiario(MARZO).toPlainString());

        gasto(1, "cafe", "100", Categoria.COMIDA);

        assertEquals("100.00", gestor.total().toPlainString(), "y con un monto sin decimales tambien");
        assertEquals("100.00", gestor.totalDe(MARZO).toPlainString());
        assertEquals("100.00", gestor.totalesPorCategoria(MARZO).get(Categoria.COMIDA).toPlainString());
    }

    @Test
    void elPresupuestoTambienSeGuardaConDosDecimales() {
        gestor.definirPresupuesto(Categoria.COMIDA, new BigDecimal("1500"));
        assertEquals("1500.00", gestor.presupuestoDe(Categoria.COMIDA).orElseThrow().toPlainString());
    }

    // ---------------------------------------------------------------
    //  Orden determinista
    // ---------------------------------------------------------------

    /**
     * EL DEFECTO DE ORDEN.
     *
     * <p>{@code Collectors.groupingBy} construye un {@code HashMap} y
     * {@code sorted} es estable, asi que los empates conservaban el orden del
     * {@code HashMap} — que no esta especificado, porque las claves son
     * constantes de {@code enum} y su {@code hashCode} es el de identidad.
     *
     * <p>Con cuatro categorias empatadas el orden observado era
     * {@code [TRANSPORTE, SALUD, OTROS, COMIDA]}: ni el de declaracion, ni el
     * alfabetico. Ahora el desempate es explicito.
     */
    @Test
    void losEmpatesSeDesempatanPorElOrdenDeclaradoDelEnum() {
        gasto(1, "a", "100.00", Categoria.OTROS);
        gasto(2, "b", "100.00", Categoria.SALUD);
        gasto(3, "c", "100.00", Categoria.TRANSPORTE);
        gasto(4, "d", "100.00", Categoria.COMIDA);

        assertEquals(List.of(Categoria.COMIDA, Categoria.TRANSPORTE, Categoria.SALUD, Categoria.OTROS),
                List.copyOf(gestor.totalesPorCategoria(MARZO).keySet()));
        assertEquals(Categoria.COMIDA, gestor.categoriaConMasGasto(MARZO).orElseThrow());
    }

    /** El mismo resultado en mil repeticiones: no depende de ningun hash. */
    @Test
    void elOrdenEsElMismoEnCadaEjecucion() {
        gasto(1, "a", "100.00", Categoria.OTROS);
        gasto(2, "b", "100.00", Categoria.COMIDA);
        gasto(3, "c", "300.00", Categoria.SALUD);

        List<Categoria> esperado = List.copyOf(gestor.totalesPorCategoria(MARZO).keySet());
        assertEquals(List.of(Categoria.SALUD, Categoria.COMIDA, Categoria.OTROS), esperado);

        for (int i = 0; i < 1000; i++) {
            assertEquals(esperado, List.copyOf(gestor.totalesPorCategoria(MARZO).keySet()));
        }
    }

    @Test
    void elMontoMandaSobreElOrdenDelEnum() {
        gasto(1, "a", "10.00", Categoria.COMIDA);
        gasto(2, "b", "999.00", Categoria.OTROS);

        assertEquals(Categoria.OTROS, gestor.categoriaConMasGasto(MARZO).orElseThrow());
    }

    @Test
    void unMesSinGastosNoTieneCategoriaGanadora() {
        assertTrue(gestor.categoriaConMasGasto(MARZO).isEmpty());
        assertTrue(gestor.totalesPorCategoria(MARZO).isEmpty());
    }

    // ---------------------------------------------------------------
    //  Totales y filtros
    // ---------------------------------------------------------------

    @Test
    void losTotalesSeparanPorMes() {
        gasto(1, "cafe", "25.00", Categoria.COMIDA);
        gestor.registrar(new Gasto(LocalDate.of(2026, 4, 1), "cafe", new BigDecimal("30.00"), Categoria.COMIDA));

        assertEquals(0, new BigDecimal("25.00").compareTo(gestor.totalDe(MARZO)));
        assertEquals(0, new BigDecimal("30.00").compareTo(gestor.totalDe(ABRIL)));
        assertEquals(0, new BigDecimal("55.00").compareTo(gestor.total()));
    }

    @Test
    void losGastosDeUnMesSalenOrdenadosPorFecha() {
        gasto(20, "tarde", "1.00", Categoria.OTROS);
        gasto(3, "temprano", "1.00", Categoria.OTROS);
        gasto(11, "medio", "1.00", Categoria.OTROS);

        assertEquals(List.of("temprano", "medio", "tarde"),
                gestor.gastosDe(MARZO).stream().map(Gasto::descripcion)
                        .collect(Collectors.toUnmodifiableList()));
    }

    /** Marzo tiene 31 dias: 310.00 / 31 = 10.00 exacto. */
    @Test
    void elPromedioDiarioDivideEntreLosDiasDelMes() {
        gasto(1, "a", "310.00", Categoria.OTROS);
        assertEquals("10.00", gestor.promedioDiario(MARZO).toPlainString());

        // Febrero de 2026 tiene 28 dias.
        gestor.registrar(new Gasto(LocalDate.of(2026, 2, 1), "b", new BigDecimal("280.00"), Categoria.OTROS));
        assertEquals("10.00", gestor.promedioDiario(YearMonth.of(2026, 2)).toPlainString());
    }

    @Test
    void elRangoDeFechasIncluyeLosExtremos() {
        gasto(1, "primero", "1.00", Categoria.OTROS);
        gasto(10, "medio", "1.00", Categoria.OTROS);
        gasto(20, "ultimo", "1.00", Categoria.OTROS);

        List<String> dentro = gestor.entre(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 20))
                .stream().map(Gasto::descripcion).collect(Collectors.toUnmodifiableList());
        assertEquals(List.of("primero", "medio", "ultimo"), dentro);

        assertEquals(1, gestor.entre(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 10)).size(),
                "un rango de un solo dia");
    }

    @Test
    void unRangoInvertidoLanzaExcepcion() {
        assertThrows(IllegalArgumentException.class,
                () -> gestor.entre(LocalDate.of(2026, 3, 20), LocalDate.of(2026, 3, 1)));
    }

    // ---------------------------------------------------------------
    //  Presupuestos
    // ---------------------------------------------------------------

    @Test
    void elPresupuestoRestanteEsVacioSiNoHayTope() {
        assertTrue(gestor.presupuestoRestante(MARZO, Categoria.COMIDA).isEmpty(),
                "sin tope no es lo mismo que tope cero");
        assertFalse(gestor.presupuestoExcedido(MARZO, Categoria.COMIDA));
    }

    @Test
    void gastarExactamenteElPresupuestoNoLoExcede() {
        gestor.definirPresupuesto(Categoria.COMIDA, new BigDecimal("100.00"));
        gasto(1, "justo", "100.00", Categoria.COMIDA);

        assertEquals(0, gestor.presupuestoRestante(MARZO, Categoria.COMIDA).orElseThrow().signum());
        assertFalse(gestor.presupuestoExcedido(MARZO, Categoria.COMIDA), "el limite es inclusivo");

        gasto(2, "un centavo mas", "0.01", Categoria.COMIDA);
        assertTrue(gestor.presupuestoExcedido(MARZO, Categoria.COMIDA));
    }

    /** El presupuesto es mensual: lo gastado en marzo no afecta a abril. */
    @Test
    void elPresupuestoSeEvaluaMesAMes() {
        gestor.definirPresupuesto(Categoria.COMIDA, new BigDecimal("50.00"));
        gasto(1, "marzo", "80.00", Categoria.COMIDA);

        assertTrue(gestor.presupuestoExcedido(MARZO, Categoria.COMIDA));
        assertFalse(gestor.presupuestoExcedido(ABRIL, Categoria.COMIDA));
    }

    @Test
    void unPresupuestoNoPositivoOnuloSeRechaza() {
        assertThrows(IllegalArgumentException.class,
                () -> gestor.definirPresupuesto(Categoria.COMIDA, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> gestor.definirPresupuesto(Categoria.COMIDA, new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class,
                () -> gestor.definirPresupuesto(Categoria.COMIDA, null));
        assertThrows(IllegalArgumentException.class,
                () -> gestor.definirPresupuesto(null, new BigDecimal("10")));
    }

    @Test
    void elMapaDePresupuestosNoSePuedeModificarDesdeAfuera() {
        gestor.definirPresupuesto(Categoria.COMIDA, new BigDecimal("10.00"));
        Map<Categoria, BigDecimal> copia = gestor.presupuestos();

        assertThrows(UnsupportedOperationException.class,
                () -> copia.put(Categoria.SALUD, BigDecimal.ONE));
    }

    // ---------------------------------------------------------------
    //  Validaciones y nulos
    // ---------------------------------------------------------------

    @Test
    void unGastoInvalidoSeRechaza() {
        LocalDate hoy = LocalDate.of(2026, 3, 1);
        assertThrows(IllegalArgumentException.class,
                () -> new Gasto(null, "x", BigDecimal.ONE, Categoria.OTROS));
        assertThrows(IllegalArgumentException.class,
                () -> new Gasto(hoy, "  ", BigDecimal.ONE, Categoria.OTROS));
        assertThrows(IllegalArgumentException.class,
                () -> new Gasto(hoy, "x", BigDecimal.ZERO, Categoria.OTROS));
        assertThrows(IllegalArgumentException.class,
                () -> new Gasto(hoy, "x", new BigDecimal("-5"), Categoria.OTROS));
        assertThrows(IllegalArgumentException.class,
                () -> new Gasto(hoy, "x", BigDecimal.ONE, null));
    }

    @Test
    void losNulosNoRevientanConNullPointerException() {
        assertTrue(gestor.buscar(null).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> gestor.entre(null, LocalDate.now()));
        assertThrows(IllegalArgumentException.class, () -> gestor.entre(LocalDate.now(), null));
        assertThrows(NullPointerException.class, () -> gestor.registrar(null));
    }

    @Test
    void laCategoriaSeReconocePorNombreOporEtiqueta() {
        assertEquals(Categoria.COMIDA, Categoria.desdeTexto("COMIDA"));
        assertEquals(Categoria.COMIDA, Categoria.desdeTexto("  comida  "));
        assertEquals(Categoria.ENTRETENIMIENTO, Categoria.desdeTexto("Entretenimiento"));
        assertThrows(IllegalArgumentException.class, () -> Categoria.desdeTexto("VIAJES"));
        assertThrows(IllegalArgumentException.class, () -> Categoria.desdeTexto("  "));
        assertThrows(IllegalArgumentException.class, () -> Categoria.desdeTexto(null));
    }

    @Test
    void buscarEsInsensibleAmayusculasYaEspacios() {
        gasto(1, "Cafe de la manana", "10.00", Categoria.COMIDA);
        assertEquals(1, gestor.buscar("CAFE").size());
        assertEquals(1, gestor.buscar("  manana  ").size());
        assertEquals(0, gestor.buscar("cena").size());
    }

    // ---------------------------------------------------------------
    //  Reporte
    // ---------------------------------------------------------------

    @Test
    void elReporteMuestraTotalesAlertasYunMesVacio() {
        gasto(1, "cafe", "25.50", Categoria.COMIDA);
        gestor.definirPresupuesto(Categoria.COMIDA, new BigDecimal("20.00"));

        String reporte = gestor.reporteMensual(MARZO);
        assertTrue(reporte.contains("Total del mes: 25.50"), reporte);
        assertTrue(reporte.contains("Presupuestos excedidos"), reporte);
        assertTrue(reporte.contains("excedido por 5.50"), reporte);

        assertTrue(gestor.reporteMensual(ABRIL).contains("(sin gastos)"),
                "un mes vacio no puede mostrar una tabla en blanco sin explicacion");
    }

}
