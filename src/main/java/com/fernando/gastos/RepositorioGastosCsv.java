package com.fernando.gastos;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Guarda y recupera un {@link GestorGastos} en un archivo de texto separado por
 * punto y coma.
 *
 * <h2>El defecto: guardar y cargar apagaba las alertas</h2>
 *
 * La version anterior escribia los gastos y nada mas. Los presupuestos — que son
 * la mitad util del programa, porque son los que producen las alertas — no se
 * guardaban.
 *
 * <p>Y como {@code cargar()} devolvia un gestor <b>nuevo</b>, tampoco se
 * conservaban los que ya estaban en memoria. La secuencia completa:
 *
 * <pre>
 * antes de guardar:  presupuesto COMIDA = 20.00, excedido = true,  alertas = [COMIDA]
 * tras cargar:       presupuesto COMIDA = vacio, excedido = false, alertas = []
 * gastos conservados: 1
 * </pre>
 *
 * <p>Los gastos volvian. Las alertas no. Y no hay ningun error, ningun aviso:
 * el programa arranca al dia siguiente diciendo que todo esta dentro del
 * presupuesto porque ya no sabe cual era el presupuesto.
 *
 * <p>Es un defecto tipico de la persistencia parcial: se guarda lo que "son los
 * datos" y se olvida lo que parece configuracion, sin notar que la configuracion
 * tambien es un dato que el usuario tecleo.
 *
 * <h2>La correccion: una columna que dice que es cada fila</h2>
 *
 * El archivo guarda los dos tipos de registro con un primer campo que los
 * distingue:
 *
 * <pre>
 * tipo;fecha;categoria;monto;descripcion
 * PRESUPUESTO;;COMIDA;1500.00;
 * GASTO;2026-03-01;COMIDA;25.50;cafe
 * GASTO;2026-03-02;COMIDA;10.00;"pan; leche"
 * </pre>
 *
 * <p>Los presupuestos van primero para que al leerlos las alertas ya esten
 * activas cuando entren los gastos. Y la prueba que respalda todo esto es de
 * ida y vuelta: {@code cargar(guardar(g))} tiene que producir un gestor
 * indistinguible del original — mismos gastos, mismos presupuestos, mismas
 * alertas.
 *
 * <h2>Y el separador ya no esta prohibido</h2>
 *
 * Ver {@link Csv}: los campos se entrecomillan cuando hace falta en vez de
 * rechazar los datos que el formato no sabia representar.
 */
public class RepositorioGastosCsv {

    private static final String CABECERA = "tipo;fecha;categoria;monto;descripcion";
    private static final String GASTO = "GASTO";
    private static final String PRESUPUESTO = "PRESUPUESTO";
    private static final int CAMPOS = 5;

    /**
     * Escribe el gestor completo — presupuestos y gastos — en el archivo.
     *
     * @param gestor  gestor a guardar
     * @param archivo destino; se sobrescribe
     * @throws IOException si falla la escritura
     */
    public void guardar(GestorGastos gestor, Path archivo) throws IOException {
        List<String> lineas = new ArrayList<>();
        lineas.add(CABECERA);

        // Los presupuestos primero: al cargar, las alertas quedan activas antes
        // de que entre el primer gasto.
        for (Map.Entry<Categoria, BigDecimal> entrada : gestor.presupuestos().entrySet()) {
            lineas.add(Csv.unir(PRESUPUESTO, "", entrada.getKey().name(),
                    entrada.getValue().toPlainString(), ""));
        }
        for (Gasto gasto : gestor.gastos()) {
            lineas.add(Csv.unir(GASTO, gasto.fecha().toString(), gasto.categoria().name(),
                    gasto.monto().toPlainString(), gasto.descripcion()));
        }

        Files.write(archivo, lineas, StandardCharsets.UTF_8);
    }

    /**
     * Lee un gestor completo del archivo.
     *
     * @param archivo origen
     * @return un gestor con los mismos gastos y presupuestos que se guardaron
     * @throws IOException si falla la lectura o alguna linea esta mal formada
     */
    public GestorGastos cargar(Path archivo) throws IOException {
        GestorGastos gestor = new GestorGastos();
        List<String> lineas = Files.readAllLines(archivo, StandardCharsets.UTF_8);

        for (int i = 0; i < lineas.size(); i++) {
            String linea = lineas.get(i);
            if (linea.isBlank() || (i == 0 && linea.startsWith("tipo"))) {
                continue;
            }
            leerLinea(gestor, linea, i + 1);
        }
        return gestor;
    }

    private void leerLinea(GestorGastos gestor, String linea, int numeroDeLinea) throws IOException {
        List<String> campos;
        try {
            campos = Csv.partir(linea);
        } catch (IllegalArgumentException e) {
            throw new IOException("Linea " + numeroDeLinea + ": " + e.getMessage(), e);
        }
        if (campos.size() != CAMPOS) {
            throw new IOException("Linea " + numeroDeLinea + " invalida: se esperaban "
                    + CAMPOS + " campos y hay " + campos.size());
        }

        try {
            Categoria categoria = Categoria.desdeTexto(campos.get(2));
            BigDecimal monto = new BigDecimal(campos.get(3));

            switch (campos.get(0)) {
                case PRESUPUESTO:
                    gestor.definirPresupuesto(categoria, monto);
                    break;
                case GASTO:
                    gestor.registrar(new Gasto(
                            LocalDate.parse(campos.get(1)), campos.get(4), monto, categoria));
                    break;
                default:
                    throw new IOException("Linea " + numeroDeLinea
                            + ": tipo desconocido '" + campos.get(0) + "'");
            }
        } catch (IllegalArgumentException e) {
            // IllegalArgumentException cubre NumberFormatException y las validaciones
            // de Gasto, Categoria y definirPresupuesto. DateTimeParseException va aparte
            // porque desciende de DateTimeException, no de IllegalArgumentException.
            throw new IOException("Linea " + numeroDeLinea + " con formato invalido: "
                    + e.getMessage(), e);
        } catch (DateTimeParseException e) {
            throw new IOException("Linea " + numeroDeLinea + " con fecha invalida: "
                    + e.getMessage(), e);
        }
    }
}
