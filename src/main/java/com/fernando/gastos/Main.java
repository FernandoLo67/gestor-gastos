package com.fernando.gastos;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Scanner;

/**
 * Menu de consola del gestor de gastos.
 *
 * <p>Solo lee del teclado y escribe en pantalla. Toda la logica vive en
 * {@link GestorGastos} y {@link RepositorioGastosCsv}.
 *
 * <p>El archivo que guarda y carga contiene ahora <b>gastos y presupuestos</b>.
 * Antes solo guardaba los gastos, asi que cargar el archivo al dia siguiente
 * dejaba el programa sin alertas de presupuesto y sin decirlo.
 */
public class Main {

    private static final Path ARCHIVO = Path.of("gastos.csv");

    public static void main(String[] args) {
        GestorGastos gestor = new GestorGastos();
        RepositorioGastosCsv repositorio = new RepositorioGastosCsv();
        Scanner scanner = new Scanner(System.in);
        boolean salir = false;

        System.out.println("=== Gestor de Gastos Personales ===");
        while (!salir) {
            System.out.println("\n1. Registrar gasto\n2. Definir presupuesto de una categoria"
                    + "\n3. Ver reporte mensual\n4. Ver gastos de un mes\n5. Buscar por descripcion"
                    + "\n6. Ver presupuestos definidos\n7. Guardar en CSV\n8. Cargar desde CSV\n0. Salir");
            System.out.print("Elige una opcion: ");

            try {
                switch (scanner.nextLine().trim()) {
                    case "1": {
                        LocalDate fecha = LocalDate.parse(pedir(scanner, "Fecha (2026-08-22): "));
                        String descripcion = pedir(scanner, "Descripcion: ");
                        BigDecimal monto = new BigDecimal(pedir(scanner, "Monto: "));
                        Categoria categoria = Categoria.desdeTexto(
                                pedir(scanner, "Categoria " + java.util.Arrays.toString(Categoria.values()) + ": "));
                        gestor.registrar(new Gasto(fecha, descripcion, monto, categoria));
                        System.out.println("Gasto registrado.");
                        break;
                    }
                    case "2": {
                        Categoria categoria = Categoria.desdeTexto(pedir(scanner, "Categoria: "));
                        gestor.definirPresupuesto(categoria, new BigDecimal(pedir(scanner, "Presupuesto mensual: ")));
                        System.out.println("Presupuesto definido.");
                        break;
                    }
                    case "3":
                        System.out.println(gestor.reporteMensual(
                                YearMonth.parse(pedir(scanner, "Mes (2026-08): "))));
                        break;
                    case "4":
                        gestor.gastosDe(YearMonth.parse(pedir(scanner, "Mes (2026-08): ")))
                                .forEach(System.out::println);
                        break;
                    case "5":
                        gestor.buscar(pedir(scanner, "Texto a buscar: ")).forEach(System.out::println);
                        break;
                    case "6": {
                        if (gestor.presupuestos().isEmpty()) {
                            System.out.println("(sin presupuestos definidos)");
                        } else {
                            gestor.presupuestos().forEach((categoria, monto) -> System.out.printf(
                                    "  %-16s %10s%n", categoria.getEtiqueta(), monto.toPlainString()));
                        }
                        break;
                    }
                    case "7": {
                        repositorio.guardar(gestor, ARCHIVO);
                        System.out.printf("Guardados %d gastos y %d presupuestos en %s%n",
                                gestor.gastos().size(), gestor.presupuestos().size(),
                                ARCHIVO.toAbsolutePath());
                        break;
                    }
                    case "8": {
                        gestor = repositorio.cargar(ARCHIVO);
                        System.out.printf("Cargados %d gastos y %d presupuestos.%n",
                                gestor.gastos().size(), gestor.presupuestos().size());
                        break;
                    }
                    case "0": {
                        salir = true;
                        System.out.println("Hasta luego!");
                        break;
                    }
                    default:
                        System.out.println("Opcion invalida.");
                        break;
                }
            } catch (DateTimeParseException e) {
                System.out.println("Error: formato de fecha invalido.");
            } catch (NumberFormatException e) {
                System.out.println("Error: se esperaba un numero valido.");
            } catch (IOException e) {
                System.out.println("Error de archivo: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
        scanner.close();
    }

    private static String pedir(Scanner scanner, String mensaje) {
        System.out.print(mensaje);
        return scanner.nextLine().trim();
    }
}
