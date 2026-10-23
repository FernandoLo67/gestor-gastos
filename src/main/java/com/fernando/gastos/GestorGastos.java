package com.fernando.gastos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Gastos personales: registro, totales por mes y categoria, presupuestos y
 * alertas.
 *
 * <h2>Defecto 1: los totales no tenian escala fija</h2>
 *
 * Las sumas usaban {@code BigDecimal.ZERO} como valor inicial, y
 * {@code BigDecimal.ZERO} tiene escala 0. Consecuencia:
 *
 * <pre>
 * total de un mes vacio     = "0"
 * total de un mes con datos = "400.00"
 * </pre>
 *
 * <p>El mismo metodo devolvia dinero con dos decimales o sin ninguno segun
 * hubiera datos o no. En un reporte eso se ve como un error de formato; en una
 * comparacion con {@code equals} es peor, porque {@code BigDecimal.equals}
 * tambien compara la escala y {@code 0} no es igual a {@code 0.00}.
 *
 * <p>Ahora todos los totales salen con {@link #ESCALA} decimales, siempre.
 *
 * <h2>Defecto 2: el orden de los empates no estaba definido</h2>
 *
 * {@code totalesPorCategoria} agrupaba con {@code Collectors.groupingBy}, que
 * construye un {@link java.util.HashMap}, y despues ordenaba por monto
 * descendente. {@code sorted} es estable, asi que <b>los empates conservaban el
 * orden del {@code HashMap}</b>, que no esta especificado: las claves son
 * constantes de {@code enum} y su {@code hashCode} es el de identidad.
 *
 * <p>Con cuatro categorias empatadas en 100.00, el orden observado fue
 * {@code [TRANSPORTE, SALUD, OTROS, COMIDA]}: ni el de declaracion, ni el
 * alfabetico, ni ninguno que alguien pueda predecir leyendo el codigo. Y
 * {@code categoriaConMasGasto} devolvia el primero de esa lista, o sea una
 * respuesta arbitraria a una pregunta con cuatro respuestas igual de validas.
 *
 * <p>Ahora se agrupa en un {@link EnumMap} y el orden es explicito: por monto
 * descendente y, a igual monto, por el orden de declaracion del {@code enum}.
 * Un desempate cualquiera esta bien; lo que no esta bien es no tener ninguno.
 *
 * <h2>Defecto 3: los nulos reventaban por dentro</h2>
 *
 * {@code buscar(null)} y {@code entre(null, hoy)} lanzaban
 * {@link NullPointerException} desde el medio de un {@code stream}.
 */
public class GestorGastos {

    /** Decimales de todos los importes que salen de esta clase. */
    public static final int ESCALA = 2;

    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;
    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(ESCALA, REDONDEO);

    private final List<Gasto> gastos = new ArrayList<>();
    private final Map<Categoria, BigDecimal> presupuestos = new EnumMap<>(Categoria.class);

    // ---------------------------------------------------------------
    //  Alta
    // ---------------------------------------------------------------

    /**
     * @param gasto gasto a registrar
     * @throws NullPointerException si es nulo
     */
    public void registrar(Gasto gasto) {
        gastos.add(Objects.requireNonNull(gasto, "El gasto no puede ser nulo"));
    }

    /**
     * @param categoria    categoria a limitar
     * @param montoMensual tope mensual, mayor que cero
     * @throws IllegalArgumentException si la categoria es nula o el monto no es positivo
     */
    public void definirPresupuesto(Categoria categoria, BigDecimal montoMensual) {
        if (categoria == null) {
            throw new IllegalArgumentException("La categoria no puede ser nula");
        }
        if (montoMensual == null || montoMensual.signum() <= 0) {
            throw new IllegalArgumentException("El presupuesto debe ser mayor a 0");
        }
        presupuestos.put(categoria, montoMensual.setScale(ESCALA, REDONDEO));
    }

    // ---------------------------------------------------------------
    //  Consultas
    // ---------------------------------------------------------------

    /** @return el presupuesto de esa categoria, o vacio si no tiene */
    public Optional<BigDecimal> presupuestoDe(Categoria categoria) {
        return Optional.ofNullable(presupuestos.get(categoria));
    }

    /**
     * @return todos los presupuestos definidos, en orden de declaracion del
     *         {@code enum}. Hace falta para poder <b>guardarlos</b>: sin este
     *         metodo el repositorio no tenia forma de leerlos, que es
     *         exactamente por lo que no los guardaba.
     *
     *         <p>Se devuelve una copia en {@link EnumMap} y no un
     *         {@code Map.copyOf}: el javadoc de {@code Map.copyOf} dice que el
     *         orden de iteracion <b>no esta especificado</b>, y este mapa se
     *         recorre para escribir el archivo. Usarlo aqui habria vuelto a
     *         meter, por la puerta de atras, el mismo defecto de orden que se
     *         acaba de sacar de {@code totalesPorCategoria}.
     */
    public Map<Categoria, BigDecimal> presupuestos() {
        return Collections.unmodifiableMap(new EnumMap<>(presupuestos));
    }

    /** @return todos los gastos, en orden de registro */
    public List<Gasto> gastos() {
        return List.copyOf(gastos);
    }

    /** @return los gastos de ese mes, ordenados por fecha */
    public List<Gasto> gastosDe(YearMonth mes) {
        return gastos.stream()
                .filter(g -> g.mes().equals(mes))
                .sorted(Comparator.comparing(Gasto::fecha))
                .collect(Collectors.toUnmodifiableList());
    }

    /** @return el total de todos los gastos, con {@value #ESCALA} decimales */
    public BigDecimal total() {
        return sumar(gastos);
    }

    /** @return el total del mes, con {@value #ESCALA} decimales */
    public BigDecimal totalDe(YearMonth mes) {
        return sumar(gastosDe(mes));
    }

    /** @return el total de esa categoria en ese mes, con {@value #ESCALA} decimales */
    public BigDecimal totalDe(YearMonth mes, Categoria categoria) {
        return sumar(gastosDe(mes).stream().filter(g -> g.categoria() == categoria)
                .collect(Collectors.toUnmodifiableList()));
    }

    /**
     * Total por categoria dentro de un mes.
     *
     * @return un mapa ordenado de mayor a menor gasto y, a igual monto, por el
     *         orden de declaracion del {@code enum}. Solo aparecen las
     *         categorias con al menos un gasto.
     */
    public Map<Categoria, BigDecimal> totalesPorCategoria(YearMonth mes) {
        Map<Categoria, BigDecimal> porCategoria = new EnumMap<>(Categoria.class);
        for (Gasto gasto : gastosDe(mes)) {
            porCategoria.merge(gasto.categoria(), gasto.monto(), BigDecimal::add);
        }

        Map<Categoria, BigDecimal> ordenado = new LinkedHashMap<>();
        porCategoria.entrySet().stream()
                .sorted(Map.Entry.<Categoria, BigDecimal>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .forEach(e -> ordenado.put(e.getKey(), e.getValue().setScale(ESCALA, REDONDEO)));
        return ordenado;
    }

    /**
     * @return la categoria en la que mas se gasto ese mes, o vacio si no hubo
     *         gastos. Con empate gana la primera en el orden de declaracion del
     *         {@code enum}.
     */
    public Optional<Categoria> categoriaConMasGasto(YearMonth mes) {
        return totalesPorCategoria(mes).keySet().stream().findFirst();
    }

    /**
     * Promedio diario del mes.
     *
     * <p>Divide entre <b>todos</b> los dias del mes, no entre los transcurridos:
     * es un promedio del mes cerrado. Para el mes en curso el numero sale bajo a
     * proposito — a mitad de mes solo se lleva la mitad del gasto — y eso es una
     * decision, no un descuido; medir el gasto del mes con el divisor de medio
     * mes daria una proyeccion, que es otra cosa.
     *
     * @return el promedio con {@value #ESCALA} decimales
     */
    public BigDecimal promedioDiario(YearMonth mes) {
        return totalDe(mes).divide(BigDecimal.valueOf(mes.lengthOfMonth()), ESCALA, REDONDEO);
    }

    // ---------------------------------------------------------------
    //  Presupuestos
    // ---------------------------------------------------------------

    /**
     * @return cuanto queda del presupuesto de esa categoria ese mes; negativo si
     *         se paso. Vacio si la categoria no tiene presupuesto: no es lo
     *         mismo "le queda cero" que "no tiene tope".
     */
    public Optional<BigDecimal> presupuestoRestante(YearMonth mes, Categoria categoria) {
        return presupuestoDe(categoria).map(p -> p.subtract(totalDe(mes, categoria)));
    }

    /** @return {@code true} si esa categoria se paso de su presupuesto ese mes */
    public boolean presupuestoExcedido(YearMonth mes, Categoria categoria) {
        return presupuestoRestante(mes, categoria).map(r -> r.signum() < 0).orElse(false);
    }

    /** @return las categorias que se pasaron de presupuesto ese mes, en orden de declaracion */
    public List<Categoria> alertasDePresupuesto(YearMonth mes) {
        return presupuestos.keySet().stream()
                .filter(c -> presupuestoExcedido(mes, c))
                .sorted()
                .collect(Collectors.toUnmodifiableList());
    }

    // ---------------------------------------------------------------
    //  Busqueda
    // ---------------------------------------------------------------

    /**
     * @param texto parte de la descripcion; no distingue mayusculas.
     *              {@code null} devuelve lista vacia en vez de reventar.
     * @return los gastos cuya descripcion lo contiene
     */
    public List<Gasto> buscar(String texto) {
        if (texto == null) {
            return List.of();
        }
        String consulta = texto.trim().toLowerCase();
        return gastos.stream()
                .filter(g -> g.descripcion().toLowerCase().contains(consulta))
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * @param desde primer dia, incluido
     * @param hasta ultimo dia, incluido
     * @return los gastos del rango, ordenados por fecha
     * @throws IllegalArgumentException si alguna fecha es nula o el rango esta invertido
     */
    public List<Gasto> entre(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new IllegalArgumentException("Las fechas del rango no pueden ser nulas");
        }
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException(
                    "La fecha inicial (" + desde + ") no puede ser posterior a la final (" + hasta + ")");
        }
        return gastos.stream()
                .filter(g -> !g.fecha().isBefore(desde) && !g.fecha().isAfter(hasta))
                .sorted(Comparator.comparing(Gasto::fecha))
                .collect(Collectors.toUnmodifiableList());
    }

    // ---------------------------------------------------------------
    //  Reporte
    // ---------------------------------------------------------------

    /**
     * @param mes mes a reportar
     * @return el reporte en texto: total, promedio diario, desglose por
     *         categoria y las alertas de presupuesto
     */
    public String reporteMensual(YearMonth mes) {
        StringBuilder reporte = new StringBuilder();
        reporte.append("=== Reporte de ").append(mes).append(" ===\n");
        reporte.append("Total del mes: ").append(totalDe(mes).toPlainString()).append('\n');
        reporte.append("Promedio diario: ").append(promedioDiario(mes).toPlainString()).append('\n');

        reporte.append("\nPor categoria:\n");
        Map<Categoria, BigDecimal> porCategoria = totalesPorCategoria(mes);
        if (porCategoria.isEmpty()) {
            reporte.append("  (sin gastos)\n");
        } else {
            porCategoria.forEach((categoria, monto) -> reporte.append(String.format(
                    "  %-16s %10s%n", categoria.getEtiqueta(), monto.toPlainString())));
        }

        List<Categoria> alertas = alertasDePresupuesto(mes);
        if (!alertas.isEmpty()) {
            reporte.append("\nPresupuestos excedidos:\n");
            for (Categoria categoria : alertas) {
                BigDecimal exceso = presupuestoRestante(mes, categoria).orElseThrow().abs();
                reporte.append(String.format("  %-16s excedido por %s%n",
                        categoria.getEtiqueta(), exceso.toPlainString()));
            }
        }
        return reporte.toString();
    }

    /**
     * Suma con escala fija.
     *
     * <p>El valor inicial es {@link #CERO} — {@code 0.00} — y no
     * {@code BigDecimal.ZERO}, que tiene escala 0 y hace que la suma de una lista
     * vacia salga con distinto formato que las demas.
     */
    private BigDecimal sumar(List<Gasto> lista) {
        return lista.stream()
                .map(Gasto::monto)
                .reduce(CERO, BigDecimal::add)
                .setScale(ESCALA, REDONDEO);
    }
}
