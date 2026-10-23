package com.fernando.gastos;

public enum Categoria {
    COMIDA("Comida"),
    TRANSPORTE("Transporte"),
    VIVIENDA("Vivienda"),
    SALUD("Salud"),
    ENTRETENIMIENTO("Entretenimiento"),
    EDUCACION("Educacion"),
    OTROS("Otros");

    private final String etiqueta;

    Categoria(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public static Categoria desdeTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("La categoria no puede estar vacia");
        }
        String normalizado = texto.trim().toUpperCase();
        for (Categoria categoria : values()) {
            if (categoria.name().equals(normalizado) || categoria.etiqueta.equalsIgnoreCase(texto.trim())) {
                return categoria;
            }
        }
        throw new IllegalArgumentException("Categoria desconocida: " + texto);
    }
}
