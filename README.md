# Gestor de Gastos Personales

Registro de gastos por categoria, totales y promedios mensuales, presupuestos con alertas, busqueda, rangos de fechas, reporte en texto y persistencia en CSV.

Tenia un defecto que no se ve en una sesion, se ve **al dia siguiente**.

## El defecto: guardar y cargar apagaba las alertas

`guardar()` escribia los gastos. Los presupuestos no. Y `cargar()` devolvia un gestor nuevo, asi que tampoco conservaba los que hubiera en memoria:

```
antes de guardar:  presupuesto COMIDA = 20.00, excedido = true,  alertas = [COMIDA]
tras cargar:       presupuesto COMIDA = vacio, excedido = false, alertas = []
gastos conservados: 1
```

Los gastos volvian. Las alertas no. Sin ningun error, sin ningun aviso: el programa arranca al dia siguiente diciendo que todo esta dentro del presupuesto **porque ya no sabe cual era el presupuesto**.

Es el defecto tipico de la persistencia parcial: se guarda lo que "son los datos" y se olvida lo que parece configuracion, sin notar que la configuracion tambien es un dato que el usuario tecleo. Y el sintoma — ausencia de alertas — se parece demasiado a la buena noticia de que no hay nada que alertar.

Habia ademas una causa estructural: `GestorGastos` **no tenia forma de exponer sus presupuestos**. El repositorio no podia guardarlos aunque hubiera querido. Un dato al que no se puede acceder es un dato que se va a perder.

## La correccion: una columna que dice que es cada fila

```
tipo;fecha;categoria;monto;descripcion
PRESUPUESTO;;COMIDA;1500.00;
GASTO;2026-03-01;COMIDA;25.50;cafe
GASTO;2026-03-02;COMIDA;10.00;"pan; leche"
```

Los presupuestos van primero, para que al leer el archivo las alertas ya esten activas cuando entre el primer gasto.

Y la prueba que lo respalda no comprueba un campo: comprueba que **`cargar(guardar(g))` produce un gestor indistinguible del original**.

```java
assertEquals(gestor.gastos(),                 vuelta.gastos());
assertEquals(gestor.presupuestos(),           vuelta.presupuestos());
assertEquals(gestor.totalesPorCategoria(MARZO), vuelta.totalesPorCategoria(MARZO));
assertEquals(gestor.alertasDePresupuesto(MARZO), vuelta.alertasDePresupuesto(MARZO));
assertEquals(gestor.reporteMensual(MARZO),    vuelta.reporteMensual(MARZO));
```

Una prueba de ida y vuelta cubre de una sola vez todos los campos que alguien pueda olvidarse de escribir — **incluidos los que todavia no existen**. Si manana se agrega una categoria personalizada o un objetivo de ahorro y no se persiste, esta prueba falla sola. Una prueba campo por campo no lo habria hecho, porque nadie escribe la asercion de un campo que aun no invento.

## Segundo defecto: el formato prohibia en vez de escapar

```java
if (gasto.descripcion().contains(";")) {
    throw new IllegalArgumentException("La descripcion no puede contener ';'");
}
```

Un gasto perfectamente valido en memoria — `"cafe; pan"` — no se podia guardar. Y el error aparecia al pulsar "guardar", horas despues de teclearlo, cuando ya no se sabe cual de los doscientos gastos tiene el punto y coma.

**El formato tiene que aguantar los datos, no los datos adaptarse al formato.**

La prohibicion ni siquiera era simetrica: el lector usaba `split(";", 4)`, cuyo limite de 4 deja que el ultimo campo contenga punto y comas. Escribia mas estricto de lo que leia — que es la forma mas silenciosa de que un formato se rompa, porque los archivos que otro programa genere se leen bien hasta que no.

La clase `Csv` implementa el entrecomillado del RFC 4180: un campo que contiene el separador, una comilla o un salto de linea se envuelve en comillas dobles, y dentro las comillas se duplican. Es el mismo convenio que Excel, asi que los archivos se abren ahi sin traducciones.

Leerlo **no se puede hacer con `split`**: `split` no distingue un separador que separa de uno que va dentro de un campo entrecomillado. Hace falta un automata de dos estados — dentro y fuera de comillas — y son veinte lineas.

## Tercer defecto: el orden de los empates no estaba definido

```java
Map<Categoria, BigDecimal> crudo = gastosDe(mes).stream()
        .collect(Collectors.groupingBy(Gasto::categoria, ...));   // <- HashMap
return crudo.entrySet().stream()
        .sorted(Map.Entry.comparingByValue().reversed())          // <- estable
        ...
```

`groupingBy` construye un `HashMap` y `sorted` es **estable**: los empates conservan el orden del `HashMap`, que no esta especificado. Las claves son constantes de `enum`, cuyo `hashCode` es el de identidad.

Con cuatro categorias empatadas en `100.00`, el orden observado fue:

```
[TRANSPORTE, SALUD, OTROS, COMIDA]
```

Ni el de declaracion, ni el alfabetico, ni ninguno que alguien pueda predecir leyendo el codigo. Y `categoriaConMasGasto` devolvia el primero de esa lista: una respuesta arbitraria a una pregunta con cuatro respuestas igual de validas.

En esta maquina el orden salio igual en cinco ejecuciones seguidas — no es que cambie cada vez — pero **no esta garantizado por nada**, y eso alcanza: una prueba que dependa de el es una prueba que puede empezar a fallar al cambiar de JDK.

Ahora se agrupa en un `EnumMap` y el desempate es explicito:

```java
.sorted(Map.Entry.<Categoria, BigDecimal>comparingByValue().reversed()
        .thenComparing(Map.Entry.comparingByKey()))
```

Un desempate cualquiera esta bien. Lo que no esta bien es no tener ninguno.

**El mismo error casi vuelve por la puerta de atras.** El accesor nuevo `presupuestos()` empezo siendo `Map.copyOf(presupuestos)` — y el javadoc de `Map.copyOf` dice que el orden de iteracion no esta especificado. Ese mapa es justo el que se recorre para escribir el archivo. Se cambio a una copia en `EnumMap`.

## Cuarto defecto: los totales no tenian escala fija

Las sumas usaban `BigDecimal.ZERO` como valor inicial, y `BigDecimal.ZERO` tiene escala 0:

```
total de un mes vacio     = "0"
total de un mes con datos = "400.00"
```

El mismo metodo devolvia dinero con dos decimales o con ninguno segun hubiera datos o no. En un reporte se ve como un error de formato; en una comparacion es peor, porque `BigDecimal.equals` tambien compara la escala y `0` no es igual a `0.00`. Ahora todo sale con dos decimales, siempre — el monto de entrada tambien se normaliza al registrarlo.

## Las decisiones de diseno

**`presupuestoRestante` devuelve `Optional`.** "No tiene tope" y "le queda cero" son cosas distintas, y un `BigDecimal.ZERO` no puede decir cual de las dos es. Es la misma distincion que `sistema-notas-estudiantiles` hace entre "se saco cero" y "no rindio".

**El limite del presupuesto es inclusivo.** Gastar exactamente 100.00 de un presupuesto de 100.00 no lo excede; un centavo mas si. Hay una prueba en el limite exacto, porque es la clase de detalle que alguien "arregla" sin darse cuenta.

**El promedio diario divide entre todos los dias del mes**, no entre los transcurridos. Para el mes en curso el numero sale bajo a proposito: es el promedio del mes cerrado. Dividir entre los dias transcurridos daria una *proyeccion*, que es otra pregunta y merece otro metodo. Esta documentado en el codigo para que no parezca un descuido.

**Los importes se comparan con `compareTo` cuando interesa el valor y con `toPlainString` cuando interesa la escala.** `BigDecimal.equals` compara las dos cosas a la vez, que es justo lo que no sirve para afirmar una sola.

## Resumen anual

`ResumenAnual` agrega los datos de todos los meses en un solo vistazo:

- **Total anual** y **promedio mensual**
- **Totales por mes**: los doce meses, incluidos los que tienen cero
- **Totales por categoria**: ordenados de mayor a menor gasto
- **Mes con mas gasto**: desempata por orden cronologico

`generar()` produce un reporte formateado en texto plano, listo para imprimir o guardar. Los calculos usan `BigDecimal` con la misma escala de dos decimales que el resto del proyecto — un resumen que redondea distinto a los parciales no cuadra, y esa clase de descuadre es la que hace perder horas.

## Las 42 pruebas

**`GestorGastosTest` (21)**

| grupo | que cubre |
|---|---|
| **Escala** | Todos los totales con dos decimales, con datos y sin ellos |
| **Orden determinista** | Empates por orden de declaracion; mil repeticiones identicas; el monto manda sobre el enum |
| Totales y filtros | Separacion por mes; orden por fecha; promedio en meses de 31 y de 28 dias; rangos inclusivos e invertidos |
| Presupuestos | Sin tope contra tope cero; limite inclusivo; evaluacion mes a mes; mapa inmutable |
| Validaciones | Gasto invalido; nulos; categoria por nombre o etiqueta; busqueda |
| Reporte | Totales, alertas y el mes vacio |

**`RepositorioGastosCsvTest` (9)**

| grupo | que cubre |
|---|---|
| **Ida y vuelta** | El defecto principal: presupuestos y alertas sobreviven; todo el estado, reporte incluido; gestor vacio; orden del archivo |
| **CSV** | El separador dentro de una descripcion; comillas, separadores y los dos juntos; solo se entrecomilla lo necesario; comillas sin cerrar |
| Errores | Una linea mal formada dice cual es |

## Estructura

```
src/main/java/com/fernando/gastos/
├── Categoria.java              El enum y su lectura desde texto.
├── Gasto.java                  Record inmutable, se valida al construirlo.
├── GestorGastos.java           Totales, presupuestos, alertas y reporte.
├── Csv.java                    Entrecomillado RFC 4180: escapa, no prohibe.
├── ResumenAnual.java           Agregacion anual por mes y por categoria.
├── RepositorioGastosCsv.java   Guarda gastos Y presupuestos.
└── Main.java                   Solo lee del teclado y escribe en pantalla.
src/test/java/com/fernando/gastos/
├── GestorGastosTest.java          21 pruebas.
├── RepositorioGastosCsvTest.java   9 pruebas.
└── ResumenAnualTest.java          12 pruebas.
```

## Como ejecutar

```bash
mvn package
java -jar target/gestor-gastos-1.0.0.jar
```

## Como correr las pruebas

```bash
mvn test
```
