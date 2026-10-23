package com.fernando.gastos;

import java.util.ArrayList;
import java.util.List;

/**
 * Escritura y lectura de campos CSV con comillas, segun el RFC 4180.
 *
 * <h2>Por que existe esta clase</h2>
 *
 * El repositorio anterior no escapaba nada: <b>prohibia</b> el separador.
 *
 * <pre>{@code
 * if (gasto.descripcion().contains(";")) {
 *     throw new IllegalArgumentException("La descripcion no puede contener ';'");
 * }
 * }</pre>
 *
 * <p>El resultado era que un gasto perfectamente valido en memoria —
 * {@code "cafe; pan"} — no se podia guardar, y el error aparecia al pulsar
 * "guardar", horas despues de haberlo tecleado, cuando ya no se sabe cual de los
 * doscientos gastos tiene el punto y coma.
 *
 * <p>Prohibir un caracter en los datos porque el formato no sabe representarlo
 * es resolver el problema al reves: <b>el formato tiene que aguantar los datos,
 * no los datos adaptarse al formato</b>. Y la prohibicion ni siquiera era
 * simetrica — el lector usaba {@code split(";", 4)}, cuyo limite de 4 deja que
 * el ultimo campo contenga punto y comas: escribia mas estricto de lo que leia.
 *
 * <h2>Las reglas</h2>
 *
 * <ul>
 *   <li>Un campo que contiene el separador, una comilla o un salto de linea se
 *       envuelve en comillas dobles.</li>
 *   <li>Dentro de las comillas, cada comilla se escribe dos veces.</li>
 *   <li>Todo lo demas se escribe tal cual.</li>
 * </ul>
 *
 * <p>Es el mismo convenio que usan Excel y las hojas de calculo, asi que los
 * archivos que produce se abren ahi sin traducciones.
 */
final class Csv {

    static final char SEPARADOR = ';';
    private static final char COMILLA = '"';

    private Csv() {
    }

    /**
     * Une varios campos en una linea CSV, entrecomillando los que lo necesiten.
     *
     * @param campos los campos, en orden
     * @return la linea
     */
    static String unir(String... campos) {
        StringBuilder linea = new StringBuilder();
        for (int i = 0; i < campos.length; i++) {
            if (i > 0) {
                linea.append(SEPARADOR);
            }
            linea.append(escapar(campos[i]));
        }
        return linea.toString();
    }

    /**
     * @param campo texto a escribir
     * @return el campo listo para la linea, entrecomillado si hace falta
     */
    static String escapar(String campo) {
        boolean necesitaComillas = campo.indexOf(SEPARADOR) >= 0
                || campo.indexOf(COMILLA) >= 0
                || campo.indexOf('\n') >= 0
                || campo.indexOf('\r') >= 0;
        if (!necesitaComillas) {
            return campo;
        }
        return COMILLA + campo.replace(String.valueOf(COMILLA), "\"\"") + COMILLA;
    }

    /**
     * Parte una linea CSV en sus campos, respetando las comillas.
     *
     * <p>Es un automata de dos estados — dentro y fuera de comillas — porque
     * {@code split} no puede hacerlo: no distingue un separador que separa de
     * uno que va dentro de un campo entrecomillado.
     *
     * @param linea la linea a leer
     * @return los campos, ya sin comillas
     * @throws IllegalArgumentException si las comillas no cierran
     */
    static List<String> partir(String linea) {
        List<String> campos = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean dentroDeComillas = false;

        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);

            if (dentroDeComillas) {
                if (c != COMILLA) {
                    actual.append(c);
                } else if (i + 1 < linea.length() && linea.charAt(i + 1) == COMILLA) {
                    actual.append(COMILLA);   // comilla escapada: "" -> "
                    i++;
                } else {
                    dentroDeComillas = false;
                }
            } else if (c == COMILLA && actual.length() == 0) {
                dentroDeComillas = true;
            } else if (c == SEPARADOR) {
                campos.add(actual.toString());
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }

        if (dentroDeComillas) {
            throw new IllegalArgumentException("Comillas sin cerrar en: " + linea);
        }
        campos.add(actual.toString());
        return campos;
    }
}
