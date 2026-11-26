# Compatibilidad

## Requisitos

| Requisito | Valor |
|---|---|
| Android mínimo | 8.0 (API 26) |
| Android objetivo | 28 (API 28) — necesario para poder ejecutar el motor desde el almacenamiento privado de la app |
| Arquitectura | arm64-v8a |
| Root | No |
| Permiso de Internet | No |

## Probado

| Dispositivo | Android | SoC | ABI | Estado |
|---|---|---|---|---|
| POCO / RMX5070 | 15 (API 35) | MediaTek MT6899 | arm64-v8a | ✅ Motor, handshake, crackeo, reanudación, temas, ES/EN |
| Emulador Android | — | — | x86_64 | ❌ No soportado (el motor es arm64) |

Solo se ha probado en el dispositivo de la tabla. Si lo pruebas en otro
teléfono, abre un issue con:

- marca y modelo (`getprop ro.product.model`)
- versión de Android (`getprop ro.build.version.release`)
- ABI (`getprop ro.product.cpu.abi`)
- velocidad que muestra la app y si el handshake se detecta

## 32 bits

El APK no tiene carpeta `lib/`, así que Android **no bloquea la instalación**
por ABI. Pero el motor es un binario ELF **arm64**:

- En un teléfono de 32 bits (armeabi-v7a) la app **se instala y abre**.
- La interfaz, la detección de SSID y la conversión a `.22000` funcionan
  (son Java).
- Al pulsar **Iniciar**, el sistema no puede ejecutar el binario:
  `Exec format error`. La app lo captura y lo muestra en el registro, sin
  cerrarse.

Además, la mayoría de equipos de 32 bits tienen Android 7 o anterior, por
debajo del minSdk 26.

## Otros límites

- **WPA3/SAE**: no soportado (aircrack-ng 1.7 trabaja con handshakes WPA/WPA2
  PSK).
- **Redes enterprise (802.1X)**: no soportado.
- **Handshake incompleto**: si el pcap no tiene un 4-way handshake válido, la
  app lo indica y no inventa resultados.
- **Clave aleatoria de fábrica** (letras+números largos): no es atacable por
  diccionario; ninguna app puede “adivinarla”.
- **Fuerza bruta masiva**: límite físico del teléfono. A ~3.200 keys/s, 8
  dígitos numéricos son ~9 horas; 10 dígitos son semanas.
