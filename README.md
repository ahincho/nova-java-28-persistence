# nova-java-28-persistence

La capacidad de persistencia de Nova Platform. Trae lo que cada servicio con base de datos escribía a mano:
la **paginación por cursor** para el scroll infinito, la **entidad auditable** con quién y cuándo la creó o
la cambió y su versión, y la **traducción de los errores de la base** a los de ADR-031, para que una clave
duplicada sea un 409 y una base caída un 503, en lugar de un 500 genérico.

Las decisiones están en [ADR-054](https://github.com/ahincho/nova-shared-01-docs/blob/main/adrs/shared/ADR-054-persistencia-reutilizable-con-paginacion-por-cursor.md),
y la forma del repositorio —un contrato y sus implementaciones juntos, con una sola versión— en
[ADR-041](https://github.com/ahincho/nova-shared-01-docs/blob/main/adrs/java/ADR-041-un-repositorio-por-capacidad.md).

## Módulos

| Módulo | `groupId` | Qué es |
|---|---|---|
| `nova-persistence` | `pe.edu.nova.java.libs` | el contrato del scroll: `CursorPage`, `CursorRequest`, `CursorLimits` y el códec del cursor; Java puro, sin Spring, JPA ni una librería de JSON |
| `nova-persistence-spring-boot-starter` | `pe.edu.nova.java.starters` | lo reutilizable de JPA: `AuditableEntity`, `CursorPages` sobre el keyset de Spring Data, el `CursorRequest` como argumento de un controlador y la traducción de errores en los buses de CQRS, bajo `nova.persistence.*` |

Los dos se publican en `https://maven.pkg.github.com/ahincho/nova-java-28-persistence` con la misma versión.

## Desde la 1.0.0, la API es estable

La 0.1.0 salió sin consumidor. La validó el servicio de pedidos de Plaza
([`nova-plaza-03-spring-boot-orders`](https://github.com/ahincho/nova-plaza-03-spring-boot-orders)), que lista
sus pedidos por cursor, hereda `AuditableEntity` y responde 409 por un dato duplicado, y con eso la capacidad
pasó a la 1.0.0. Desde aquí un cambio incompatible de la API, del formato del cursor o del orden de los
comportamientos es una versión mayor.

**No entra en el meta-starter** (ADR-052): trae JPA, y un servicio sin base de datos no lo necesita. Se
declara aparte.

## El scroll por cursor

Un scroll infinito no pide «la página 40»: pide «los que vienen después del último que vi». El cursor guarda
los valores del orden del último elemento entregado, y la página siguiente salta ahí por el índice, con un
costo que no crece con cada página y sin repetir ni saltar un elemento si entra uno nuevo mientras se navega.

Por HTTP, la petición lleva `?limit=` (20 por defecto, 100 como máximo) y `?cursor=`, y la página va en `data`
del sobre de Nova:

```json
{
  "success": true,
  "status": 200,
  "data": {
    "items": [ { "id": "…", "status": "PENDING" } ],
    "nextCursor": "eyJ2IjoxLCJzIjoibmV3ZXN0Iiwia…",
    "hasNext": true
  }
}
```

- En la última página, `hasNext` es `false` y `nextCursor` es `null`.
- No hay total ni página anterior: un scroll infinito solo avanza, y contar recorre la tabla entera.
- El cursor es **opaco**: el cliente lo devuelve tal como llegó. Es Base64 URL-safe de un JSON con la
  versión del formato, el nombre del orden y los valores de su clave, con su tipo. No se firma: los filtros
  los pone el servidor y no viajan en él.
- Un cursor mal formado, de otra versión o de otro orden es un **400** con el campo `cursor`, y un límite
  fuera de rango es un 400 con el campo `limit`.

### Cómo se usa

El orden se declara una vez, con nombre, y **termina en la clave primaria**, para que dos filas con el mismo
`createdAt` nunca se repitan ni se salten. Sus columnas no admiten nulos.

```java
static final CursorSort NEWEST =
        CursorSort.of("newest", Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
```

El repositorio devuelve un `Window` de Spring Data:

```java
Window<Order> findByCustomerId(String customerId, ScrollPosition position, Limit limit, Sort sort);
```

El controlador recibe el `CursorRequest` ya validado, y el handler arma la página:

```java
@GetMapping
public CursorPage<OrderResponse> list(@RequestHeader(CUSTOMER_HEADER) String customerId, CursorRequest page) {
    return queries.execute(new ListOrders(customerId, page));
}

public CursorPage<OrderResponse> handle(ListOrders query) {
    Window<Order> window = orders.findByCustomerId(
            query.customerId(), CursorPages.position(query.page(), NEWEST), CursorPages.limit(query.page()),
            NEWEST.sort());
    return CursorPages.page(window, NEWEST, OrderViews::of);
}
```

**Una página nunca hace `fetch join` de una colección.** Hibernate aplicaría el límite en memoria, después de
traer todas las filas. Se pagina la raíz y las colecciones se cargan por lotes, con `@BatchSize`.

## La entidad auditable

```java
@Entity
public class Order extends AuditableEntity { … }
```

`AuditableEntity` es una `@MappedSuperclass` con cinco columnas, que la auditoría de Spring Data llena al
guardar:

| Columna | Qué guarda | Nulos |
|---|---|---|
| `created_at` | cuándo se creó, con el `Clock` del servicio, en microsegundos | no |
| `updated_at` | cuándo cambió por última vez | no |
| `created_by` | quién la creó | sí |
| `updated_by` | quién la cambió por última vez | sí |
| `version` | la versión del bloqueo optimista | no |

**El actor es el mismo que el de la auditoría de los buses:** el `ActorResolver` de CQRS (ADR-053). Sin
CQRS, el nombre de la autenticación de Spring Security; sin ninguno de los dos, nadie. Un servicio que ya
declara su propio `@EnableJpaAuditing` conserva el suyo.

Una entidad nueva usa un UUID ordenable por tiempo, versión 7, generado por la aplicación, que ordena las
inserciones en el índice y sirve de desempate para el cursor.

## La traducción de errores

Dos comportamientos se suman a los buses de CQRS, si el servicio los usa:

| Orden | Comportamiento | Qué hace |
|---|---|---|
| 450 | `PersistenceErrorBehavior` | traduce lo que falla en la base, en comandos y consultas, por fuera de la transacción para alcanzar también lo que falla al abrirla o confirmarla |
| 600 | `FlushBehavior` | hace `flush` al terminar cada comando, por dentro de la transacción |

| Excepción | Error de ADR-031 | HTTP |
|---|---|---|
| bloqueo optimista, al pisar una versión | `CONFLICT` con el código `CONCURRENT_MODIFICATION` | 409 |
| una restricción única o de integridad | `CONFLICT` con el código `DATA_CONFLICT` | 409 |
| una base caída o una conexión que no llega | `UNAVAILABLE`, con la base como dependencia | 503 |
| una consulta que pasa su timeout | `TIMEOUT`, con la base como dependencia | 504 |

**El `flush` es lo que hace posible la traducción.** Sin él, un `save` llega a la base recién en el commit, y
el commit puede estar fuera del bus: en pedidos lo hace la idempotencia, alrededor del controlador. Con el
`flush` al final del handler, el conflicto falla dentro del bus y se responde 409.

Reconoce las excepciones que Spring ya tradujo y las de JPA y JDBC que llegan sin traducir, y si no encuentra
un tipo conocido mira el SQLState del estándar. Lo demás sigue siendo un 500. Un servicio sin CQRS usa
`PersistenceErrors.translate` en su propio manejo.

## Las propiedades

| Propiedad | Por defecto | Qué hace |
|---|---|---|
| `nova.persistence.pagination.default-limit` | `20` | cuántos elementos trae una página sin `?limit=` |
| `nova.persistence.pagination.max-limit` | `100` | el límite más alto que se acepta |
| `nova.persistence.auditing.enabled` | `true` | la auditoría de `AuditableEntity` |
| `nova.persistence.error-translation.enabled` | `true` | la traducción de errores en los buses |
| `nova.persistence.flush.enabled` | `true` | el `flush` de cada comando |

## Instalación

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/ahincho/nova-java-28-persistence")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("pe.edu.nova.java.starters:nova-persistence-spring-boot-starter:1.0.0")
}
```

El starter trae `nova-persistence` y `nova-api-standard` 1.1.0. Spring Boot, Spring Data JPA, Spring MVC,
CQRS y Spring Security los trae el servicio.

## Desarrollo

```bash
./gradlew build
```

Corre Spotless, Checkstyle, las pruebas con la cobertura mínima de 80 % y el Javadoc. El build pide un
`GITHUB_TOKEN` con `read:packages` para el toolchain, `nova-api-standard` y `nova-cqrs`.

| Módulo | Pruebas |
|---|---|
| `nova-persistence` | el códec del cursor con cada tipo de clave y cada forma de cursor inválido, la petición y sus límites, la página, el JSON mínimo, y que el núcleo no dependa de ningún framework |
| `nova-persistence-spring-boot-starter` | sobre H2 con los buses de CQRS: el scroll que recorre cada fila una vez aunque compartan el valor del orden, la auditoría con el reloj y el actor, el 409 por duplicado y por versión pisada, y el argumento del controlador; con `ApplicationContextRunner`, cada pieza encendida y apagada, y la traducción de cada excepción |

## Licencia

Eclipse Public License 2.0. Ver [LICENSE](LICENSE).
