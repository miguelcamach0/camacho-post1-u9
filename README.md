# Recordatorios Citas Service


## Descripción
Repositorio del post-contenido de la Unidad 9 de Patrones de Diseño
de Software — Sexto Semestre. Un único proyecto Spring Boot que
envía recordatorios de citas por el canal preferido del paciente,
construido desde el inicio con Strategy (ReminderChannel) y Factory
Method (ReminderChannelFactory) inyectados por constructor.

## Parte 1 — Pruebas Unitarias con JUnit 5 y Mockito
CitaReminderServiceImplTest aísla CitaReminderServiceImpl de sus
tres colaboradores. Ver "Decisiones de diseño" para la justificación
de por qué cada uno se mockeó, se stubeó o se resolvió con un fake.

## Parte 2 — Refactor DIP, Integración y CI/CD
enviarRecordatorioUrgente nació con un "new" hardcodeado que lo
hacía imposible de testear; el refactor lo movió a
config/CanalUrgenteConfig.java e inyectó la abstracción por
constructor. CitaRepositoryTest (@DataJpaTest) y
CitaReminderServiceIntegrationTest (@SpringBootTest) prueban la
persistencia y el flujo completo con H2 y un Clock fijo. El pipeline
.github/workflows/ci.yml ejecuta mvn verify en cada push.

## Decisiones de diseño

### ¿Por qué Strategy facilitó las pruebas?
El patrón *Strategy*, implementado mediante la interfaz *ReminderChannel* y sus implementaciones *EmailReminderChannel* y *SmsReminderChannel*, permitió desacoplar la lógica del servicio de los mecanismos concretos de notificación. Gracias a esta abstracción, CitaReminderServiceImpl puede utilizar diferentes canales mediante un contrato común, sin conocer los detalles de su implementación.

Esta decisión facilitó la prueba *enviarRecordatoriosPendientes_citaEnVentana_usaElCanalPreferido*, porque permitió sustituir la estrategia real por un Mock de *ReminderChannel* y verificar mediante verify(...) que el servicio invoca el método enviar() exactamente una vez, con la cita correspondiente. De esta manera, se comprueba la interacción entre el servicio y la estrategia seleccionada sin ejecutar operaciones reales de correo electrónico o SMS.

Sin esta abstracción, si los mecanismos concretos de envío estuvieran incorporados directamente en el servicio, sería necesario aislar dependencias de nivel inferior, como clientes SMTP o HTTP, aumentando el acoplamiento y la complejidad de las pruebas. Por tanto, Strategy favorece la aplicación del Principio de Inversión de Dependencias (DIP) y permite desarrollar pruebas unitarias centradas en el comportamiento del servicio, independientemente del canal utilizado.

### ¿Por qué Factory facilitó las pruebas? 
La implementación de Factory, mediante la interfaz *ReminderChannelFactory* y la clase *ReminderChannelFactoryImpl*, permitió separar la *responsabilidad* de seleccionar el canal de notificación de la lógica encargada de procesar los recordatorios. Así, *CitaReminderServiceImpl* solicita la estrategia correspondiente a través del método obtenerCanal(CanalNotificacion tipo), sin necesitar conocer ni construir directamente las implementaciones concretas.

Esta separación facilitó la prueba *enviarRecordatoriosPendientes_citaEnVentana_usaElCanalPreferido*, en la cual se configura un *Mock* de ReminderChannelFactory para que devuelva una estrategia simulada cuando recibe CanalNotificacion.EMAIL. Posteriormente, mediante verify(reminderChannelFactory).obtenerCanal(CanalNotificacion.EMAIL), se comprueba que el servicio solicita el canal correspondiente a la preferencia del paciente.

Sin una fábrica inyectable, la selección de canales podría quedar incorporada directamente en el servicio mediante condiciones if/else o switch, aumentando sus responsabilidades y dificultando el aislamiento de esa decisión durante las pruebas. Aunque sería técnicamente posible comprobar la selección sin una fábrica, su incorporación permite verificar explícitamente la colaboración entre componentes y sustituir el comportamiento de selección sin modificar el código del servicio.

### Mock vs. Stub vs. Fake (Parte 1). 
La selección de los Test Doubles para las pruebas unitarias de CitaReminderServiceImpl se fundamentó en la responsabilidad de cada colaborador y en el comportamiento que se necesitaba comprobar. Se utilizaron Mocks para controlar respuestas y verificar interacciones, mientras que la dependencia temporal se resolvió mediante un Fake Clock, permitiendo ejecutar las pruebas de forma determinista.

**CitaRepository → Mock.** Se utilizó un mock porque las pruebas requieren tanto controlar los resultados de las consultas como verificar las operaciones de persistencia. Mediante when(...).thenReturn(...) se simula la existencia o ausencia de citas pendientes, mientras que verify(...) permite comprobar que save() se invoque cuando corresponde y no se ejecute cuando no existen citas por notificar. Aunque el repositorio presenta un comportamiento de Stub al devolver datos predefinidos, su utilización como Mock se justifica porque también se verifican explícitamente sus interacciones con el servicio, sin acceder a una base de datos real.

**ReminderChannelFactory → Mock.** Se empleó un mock para controlar qué implementación de ReminderChannel devuelve la fábrica y verificar que el servicio solicita el canal correspondiente a la preferencia del paciente. En la prueba de una cita con preferencia EMAIL, se configura la respuesta de obtenerCanal(CanalNotificacion.EMAIL) y posteriormente se verifica su invocación. Esto permite comprobar la interacción del servicio con la fábrica sin depender de implementaciones concretas ni ejecutar operaciones reales de notificación.

**ReminderChannel → Mock.** Se utilizó un mock para verificar que la estrategia seleccionada recibe correctamente la cita y el mensaje mediante el método enviar(). La prueba comprueba que dicha operación se invoque una vez, sin realizar envíos reales de correo electrónico o SMS. Esta decisión permite aislar la lógica del servicio y comprobar su colaboración con la estrategia de notificación, aprovechando la interfaz común proporcionada por el patrón Strategy.

**Clock → Fake.** Se utilizó Clock.fixed(...), una implementación funcional de java.time.Clock proporcionada por el JDK, para sustituir el reloj del sistema por uno con un instante fijo. Esta decisión garantiza pruebas deterministas y reproducibles, permitiendo verificar que la consulta de citas pendientes se realice exactamente dentro de la ventana temporal de 24 horas. A diferencia de un Mock de Mockito, no requiere configurar artificialmente métodos como instant() o getZone(), ya que conserva el comportamiento real de la API temporal, pero con una referencia de tiempo controlada.


### El refactor DIP (Parte 2).

### Punto de decisión — diagnóstico antes del refactor.
En la implementación inicial de *enviarRecordatorioUrgente(Long citaId)* se identificó una violación del Principio de Inversión de Dependencias (DIP), esto se debe a que el servicio construye directamente las implementaciones concretas encargadas de enviar la notificación SMS.

Las líneas de código que están generando esta problemática son:

ReminderChannel canalUrgente =
    new SmsReminderChannel(
        new TwilioNotificadorSmsGateway()
    );

Esta construcción mediante new, dentro del cuerpo del método, genera un acoplamiento directo entre CitaReminderServiceImpl, que contiene la lógica de aplicación, y las clases concretas SmsReminderChannel y TwilioNotificadorSmsGateway. En lugar de depender exclusivamente de abstracciones suministradas desde el exterior, el servicio asume la responsabilidad adicional de crear sus propias dependencias, contraviniendo el propósito de DIP.

**¿Por qué esta implementación dificulta las pruebas unitarias?**
El principal inconveniente es que el canal urgente y su gateway se instancian internamente cada vez que se ejecuta el método. Por esta razón, no existe un punto de inyección convencional que permita sustituirlos por un Mock mediante el constructor del servicio.

Esto dificulta comprobar de forma aislada escenarios como una falla del gateway de SMS. En particular, necesitamos verificar que, si el envío genera una excepción, la cita no se marque como notificada y no se invoque citaRepository.save(cita). Sin embargo, la construcción interna impide configurar directamente el comportamiento del canal mediante los mecanismos habituales de Mockito utilizados en la actividad.

**Contraste con enviarRecordatoriosPendientes()**

A diferencia del método urgente, enviarRecordatoriosPendientes() fue diseñado desde la Parte 1 para recibir sus colaboradores mediante inyección por constructor, incluyendo CitaRepository, ReminderChannelFactory y Clock.

Esta arquitectura permitió sustituir el repositorio y la fábrica mediante Mocks, controlar la estrategia de notificación devuelta y utilizar Clock.fixed(...) para obtener resultados temporales deterministas. De esta manera, las pruebas unitarias verificaron las interacciones del servicio sin necesitar una base de datos ni ejecutar envíos reales.

Por tanto, mientras enviarRecordatoriosPendientes() facilita el aislamiento de sus dependencias mediante abstracciones inyectadas, enviarRecordatorioUrgente() introduce un acoplamiento innecesario al construir directamente los componentes concretos de notificación.

### Refactor DIP: aplicación del Principio de Inversión de Dependencias
El refactor de enviarRecordatorioUrgente(Long citaId) tuvo como propósito eliminar el acoplamiento directo entre la lógica del servicio y las implementaciones concretas encargadas del envío de notificaciones SMS. En la versión inicial, CitaReminderServiceImpl construía internamente las dependencias mediante new SmsReminderChannel(new TwilioNotificadorSmsGateway()), impidiendo sustituir el canal urgente a través de los mecanismos convencionales de inyección utilizados en las pruebas unitarias.

Para corregir este problema, se trasladó la responsabilidad de construcción a CanalUrgenteConfig, que actúa como parte del Composition Root de la aplicación. Allí se definió un bean de Spring que recibe la abstracción NotificadorSmsGateway y proporciona una instancia de ReminderChannel:

@Bean("canalUrgente")
public ReminderChannel canalUrgente(NotificadorSmsGateway gateway) {
    return new SmsReminderChannel(gateway);
}

Posteriormente, CitaReminderServiceImpl se modificó para recibir el canal urgente mediante inyección por constructor, utilizando @Qualifier("canalUrgente") para identificar el bean correspondiente. De esta manera, el servicio dejó de construir directamente sus colaboradores y pasó a depender de la abstracción ReminderChannel.

Este refactor mantuvo el comportamiento funcional del servicio y permitió sustituir el canal por un Mock. La prueba enviarRecordatorioUrgente_gatewayFalla_noMarcaComoEnviado() demuestra que, al simular un fallo mediante doThrow(...), la cita no se marca como notificada ni se invoca save().

Así, la aplicación de DIP redujo el acoplamiento y facilitó las pruebas unitarias sin modificar la lógica de negocio.

### ¿Por qué H2 real y un gateway mockeado?
En las pruebas de integración se utilizó H2 en memoria para verificar operaciones reales de persistencia mediante JPA, sin depender de un servidor externo. Aunque H2 no reproduce todas las particularidades de otras bases de datos, resulta adecuado para las consultas evaluadas en este laboratorio.

Por su parte, NotificadorSmsGateway se sustituyó mediante @MockBean para verificar las solicitudes de envío sin realizar llamadas SMS reales ni depender de credenciales, conectividad o disponibilidad del proveedor.

Esta combinación permite probar con componentes reales aquello que queremos verificar (persistencia) y simular las dependencias externas que no forman parte del objetivo de la prueba (gateway SMS).

Además, facilita ejecutar las pruebas mediante mvn verify en GitHub Actions sin contenedores Docker ni servicios externos.

### Punto de decisión — por qué H2 real pero gateway mockeado
El pipeline de GitHub Actions no requiere contenedores Docker externos porque las pruebas de persistencia utilizan H2 en memoria, que se ejecuta dentro del propio proceso Java. Asimismo, NotificadorSmsGateway se sustituye por un Mock, evitando conexiones con proveedores SMS.

Estas decisiones permiten ejecutar mvn verify de forma automatizada sin configurar servicios adicionales de base de datos, credenciales externas ni infraestructura de contenedores, simplificando el proceso de integración continua.

## Ejecutar las pruebas
```bash
mvn test                  # Pruebas unitarias
mvn verify                # Unitarias + integración
```

## Herramientas utilizadas
- Java 21, Spring Boot 3.3, Spring Data JPA, H2, JUnit 5, Mockito
- Apache Maven, GitHub Actions, Git, GitHub

## Conclusiones
[Párrafo de 3-5 oraciones con los aprendizajes más relevantes de ambas partes]
