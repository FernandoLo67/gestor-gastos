package com.fernando.gastos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResumenAnualTest {

    private GestorGastos gestor;
    private ResumenAnual resumen;

    @BeforeEach
    void preparar() {
        gestor = new GestorGastos();
        resumen = new ResumenAnual(gestor, Year.of(2026));
    }

    @Test
    void totalAnualSumaLosMeses() {
        registrar("2026-01-10", "Almuerzo", "50.00", Categoria.COMIDA);
        registrar("2026-03-15", "Bus", "25.00", Categoria.TRANSPORTE);

        assertEquals(new BigDecimal("75.00"), resumen.totalAnual());
    }

    @Test
    void totalAnualSinGastosEsCero() {
        assertEquals(new BigDecimal("0.00"), resumen.totalAnual());
    }

    @Test
    void promedioMensualDivideEntreMesesConGasto() {
        registrar("2026-01-10", "Almuerzo", "100.00", Categoria.COMIDA);
        registrar("2026-03-15", "Bus", "50.00", Categoria.TRANSPORTE);

        assertEquals(new BigDecimal("75.00"), resumen.promedioMensual());
    }

    @Test
    void promedioSinGastosEsCero() {
        assertEquals(new BigDecimal("0.00"), resumen.promedioMensual());
    }

    @Test
    void totalesPorMesSoloIncluyeMesesConGasto() {
        registrar("2026-01-10", "Almuerzo", "50.00", Categoria.COMIDA);
        registrar("2026-03-15", "Bus", "25.00", Categoria.TRANSPORTE);

        Map<YearMonth, BigDecimal> porMes = resumen.totalesPorMes();

        assertEquals(2, porMes.size());
        assertTrue(porMes.containsKey(YearMonth.of(2026, 1)));
        assertTrue(porMes.containsKey(YearMonth.of(2026, 3)));
        assertFalse(porMes.containsKey(YearMonth.of(2026, 2)));
    }

    @Test
    void totalesPorCategoriaOrdenadosPorMontoDescendente() {
        registrar("2026-01-10", "Almuerzo", "200.00", Categoria.COMIDA);
        registrar("2026-02-15", "Bus", "50.00", Categoria.TRANSPORTE);
        registrar("2026-01-20", "Cena", "100.00", Categoria.COMIDA);

        Map<Categoria, BigDecimal> porCat = resumen.totalesPorCategoria();

        assertEquals(2, porCat.size());
        Categoria primera = porCat.keySet().iterator().next();
        assertEquals(Categoria.COMIDA, primera);
        assertEquals(new BigDecimal("300.00"), porCat.get(Categoria.COMIDA));
    }

    @Test
    void mesConMasGastoEsCorrecto() {
        registrar("2026-01-10", "Almuerzo", "50.00", Categoria.COMIDA);
        registrar("2026-03-15", "Bus", "200.00", Categoria.TRANSPORTE);

        assertEquals(YearMonth.of(2026, 3), resumen.mesConMasGasto().orElseThrow());
    }

    @Test
    void mesConMasGastoVacioSinDatos() {
        assertTrue(resumen.mesConMasGasto().isEmpty());
    }

    @Test
    void contarMesesConGasto() {
        registrar("2026-01-10", "Almuerzo", "50.00", Categoria.COMIDA);
        registrar("2026-01-20", "Cena", "30.00", Categoria.COMIDA);
        registrar("2026-05-15", "Bus", "25.00", Categoria.TRANSPORTE);

        assertEquals(2, resumen.contarMesesConGasto());
    }

    @Test
    void generarIncluyeTodosLosCampos() {
        registrar("2026-01-10", "Almuerzo", "100.00", Categoria.COMIDA);
        registrar("2026-03-15", "Bus", "50.00", Categoria.TRANSPORTE);

        String texto = resumen.generar();

        assertTrue(texto.contains("2026"));
        assertTrue(texto.contains("150.00"));
        assertTrue(texto.contains("Comida"));
        assertTrue(texto.contains("Transporte"));
    }

    @Test
    void ignoraGastosDeOtroAnio() {
        registrar("2025-12-31", "Compra", "500.00", Categoria.OTROS);
        registrar("2026-01-01", "Almuerzo", "50.00", Categoria.COMIDA);

        assertEquals(new BigDecimal("50.00"), resumen.totalAnual());
    }

    @Test
    void rechazaParametrosNulos() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResumenAnual(null, Year.of(2026)));
        assertThrows(IllegalArgumentException.class,
                () -> new ResumenAnual(gestor, null));
    }

    private void registrar(String fecha, String desc, String monto, Categoria cat) {
        gestor.registrar(new Gasto(
                LocalDate.parse(fecha), desc, new BigDecimal(monto), cat));
    }
}
