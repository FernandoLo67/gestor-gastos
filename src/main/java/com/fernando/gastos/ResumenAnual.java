package com.fernando.gastos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resumen anual de gastos: totales por mes, por categoria, promedios y
 * el mes con mas gasto.
 *
 * <p>Opera sobre un {@link GestorGastos} existente para no duplicar datos.
 * Solo agrega logica de agregacion temporal que GestorGastos no necesita
 * para su operacion diaria.
 */
public class ResumenAnual {

    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    private final GestorGastos gestor;
    private final Year anio;

    public ResumenAnual(GestorGastos gestor, Year anio) {
        if (gestor == null || anio == null) {
            throw new IllegalArgumentException("El gestor y el anio son obligatorios");
        }
        this.gestor = gestor;
        this.anio = anio;
    }

    public BigDecimal totalAnual() {
        BigDecimal total = BigDecimal.ZERO.setScale(GestorGastos.ESCALA, REDONDEO);
        for (YearMonth mes : mesesDelAnio()) {
            total = total.add(gestor.totalDe(mes));
        }
        return total;
    }

    public BigDecimal promedioMensual() {
        long mesesConGasto = contarMesesConGasto();
        if (mesesConGasto == 0) {
            return BigDecimal.ZERO.setScale(GestorGastos.ESCALA, REDONDEO);
        }
        return totalAnual().divide(BigDecimal.valueOf(mesesConGasto), GestorGastos.ESCALA, REDONDEO);
    }

    public Map<YearMonth, BigDecimal> totalesPorMes() {
        Map<YearMonth, BigDecimal> resultado = new LinkedHashMap<>();
        for (YearMonth mes : mesesDelAnio()) {
            BigDecimal total = gestor.totalDe(mes);
            if (total.signum() > 0) {
                resultado.put(mes, total);
            }
        }
        return Collections.unmodifiableMap(resultado);
    }

    public Map<Categoria, BigDecimal> totalesPorCategoria() {
        Map<Categoria, BigDecimal> porCategoria = new EnumMap<>(Categoria.class);
        for (YearMonth mes : mesesDelAnio()) {
            for (Categoria cat : Categoria.values()) {
                BigDecimal total = gestor.totalDe(mes, cat);
                if (total.signum() > 0) {
                    porCategoria.merge(cat, total, BigDecimal::add);
                }
            }
        }
        return ordenarPorMontoDescendente(porCategoria);
    }

    public Optional<YearMonth> mesConMasGasto() {
        return totalesPorMes().entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);
    }

    public long contarMesesConGasto() {
        long cuenta = 0;
        for (YearMonth mes : mesesDelAnio()) {
            if (gestor.totalDe(mes).signum() > 0) {
                cuenta++;
            }
        }
        return cuenta;
    }

    private List<YearMonth> mesesDelAnio() {
        List<YearMonth> meses = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            meses.add(YearMonth.of(anio.getValue(), m));
        }
        return meses;
    }

    static Map<Categoria, BigDecimal> ordenarPorMontoDescendente(Map<Categoria, BigDecimal> porCategoria) {
        Map<Categoria, BigDecimal> ordenado = new LinkedHashMap<>();
        porCategoria.entrySet().stream()
                .sorted(Map.Entry.<Categoria, BigDecimal>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .forEach(e -> ordenado.put(e.getKey(), e.getValue().setScale(GestorGastos.ESCALA, REDONDEO)));
        return Collections.unmodifiableMap(ordenado);
    }

    public String generar() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Resumen anual ").append(anio).append(" ===\n");
        sb.append("Total: ").append(totalAnual().toPlainString()).append('\n');
        sb.append("Promedio mensual: ").append(promedioMensual().toPlainString()).append('\n');
        sb.append("Meses con gasto: ").append(contarMesesConGasto()).append('\n');

        mesConMasGasto().ifPresent(mes ->
                sb.append("Mes con mas gasto: ").append(mes)
                        .append(" (").append(gestor.totalDe(mes).toPlainString()).append(")\n"));

        Map<Categoria, BigDecimal> categorias = totalesPorCategoria();
        if (!categorias.isEmpty()) {
            sb.append("\nPor categoria:\n");
            categorias.forEach((cat, monto) -> sb.append(String.format(
                    "  %-16s %10s%n", cat.getEtiqueta(), monto.toPlainString())));
        }

        Map<YearMonth, BigDecimal> meses = totalesPorMes();
        if (!meses.isEmpty()) {
            sb.append("\nPor mes:\n");
            meses.forEach((mes, monto) -> sb.append(String.format(
                    "  %-10s %10s%n", mes, monto.toPlainString())));
        }

        return sb.toString();
    }
}
