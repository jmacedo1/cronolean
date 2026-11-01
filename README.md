# JM Tiempos

App Android con un widget para registrar en Leantime el tiempo dedicado a cada tarea, sin pasar por Claude.

## Qué hace el widget

- **Tarea:** se toca para elegirla, con filtro de empresa (proyecto de Leantime), buscador y las últimas usadas primero. La empresa con la que se abre se elige en los ajustes.
- **Nota:** se toca y se escribe en una ventana pequeña. Android no admite cajas de texto dentro de un widget. Se envía como descripción del registro.
- **Cronómetro y botón único:** verde «Iniciar», rojo «Parar», naranja «Revisar».
- **Check «Hecha»:** marca la tarea como hecha. Con el cronómetro en marcha se aplica al parar; parado, pide confirmación.

## Cómo se comporta

- El tiempo corre en el móvil y se envía al parar, con la hora real de inicio (`Timesheets.logTime`).
- Sin cobertura no se pierde: queda pendiente y se reintenta solo.
- Menos de 30 segundos: se entiende que se ha pulsado sin querer y no se registra nada.
- 8 horas o más: pide revisar las horas antes de guardar, por si se olvidó parar.

## Configuración

En la app: dirección de Leantime y clave de API, que se guarda cifrada con Android Keystore.

- El usuario de la clave debe estar asignado a los proyectos para ver sus tareas.
- Las horas quedan a nombre del usuario de la clave. Para que queden a tu nombre, indica tu nº de usuario; Leantime solo lo respeta si la clave tiene rol de manager o superior.

## Compilar

Java, sin dependencias externas. `./gradlew assembleRelease`; requiere JDK 21 (`gradle.properties`) y, para firmar, un `keystore.properties` que no va al repo.
