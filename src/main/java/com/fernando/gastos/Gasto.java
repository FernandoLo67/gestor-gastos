package com.fernando.gastos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

public final class Gasto {

    private final LocalDate fecha;
    private final String descripcion;
    private final BigDecimal monto;
    private final Categoria categoria;

    public Gasto(LocalDate fecha, String descripcion, BigDecimal monto, Categoria categoria) {
        if (fecha == null) {
            throw new IllegalArgumentException("La fecha no puede ser nula");
        }
        if (descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException("La descripcion no puede estar vacia");
        }
        if (monto == null || monto.signum() <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a 0");
        }
        if (categoria == null) {
            throw new IllegalArgumentException("La categoria no puede ser nula");
        }
        descripcion = descripcion.trim();

        this.fecha = fecha;
        this.descripcion = descripcion;
        this.monto = monto;
        this.categoria = categoria;
    }

    public LocalDate fecha() {
        return fecha;
    }

    public String descripcion() {
        return descripcion;
    }

    public BigDecimal monto() {
        return monto;
    }

    public Categoria categoria() {
        return categoria;
    }

    public YearMonth mes() {
        return YearMonth.from(fecha);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Gasto otro = (Gasto) o;
        return Objects.equals(fecha, otro.fecha)
                && Objects.equals(descripcion, otro.descripcion)
                && Objects.equals(monto, otro.monto)
                && Objects.equals(categoria, otro.categoria);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fecha, descripcion, monto, categoria);
    }

    @Override
    public String toString() {
        return String.format("%s  %-16s %10s  %s",
                fecha, categoria.getEtiqueta(), monto.toPlainString(), descripcion);
    }
}
