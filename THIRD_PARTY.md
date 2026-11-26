# Third-party notices

NR AirCrack incluye o enlaza los siguientes componentes:

## aircrack-ng 1.7

- Web: https://www.aircrack-ng.org/
- Licencia: **GNU General Public License v2** (ver `LICENSE`).
- Código fuente correspondiente: `third_party/aircrack-ng-1.7.tar.gz`.
- Binario incluido: `app/assets/air/bin/aircrack-ng` y librerías en
  `app/assets/air/lib/`.
- Compilado sin modificaciones de código; solo se cambió el prefijo de
  instalación y se enlazó con Clang/NDK para arm64.

## OpenSSL 3

- Web: https://www.openssl.org/
- Licencia: Apache License 2.0 (ver `third_party/LICENSE.OpenSSL`).
- Librerías: `libssl.so.3`, `libcrypto.so.3`.

## SQLite

- Web: https://www.sqlite.org/
- Licencia: dominio público.
- Librería: `libsqlite3.so`.

## libc++ (LLVM)

- Web: https://libcxx.llvm.org/
- Licencia: Apache License 2.0 con excepción LLVM.
- Librería: `libc++_shared.so`.

## zlib

- Web: https://zlib.net/
- Licencia: zlib.
- Librería: `libz.so.1`.

## AndroidX / Material

No se usa AndroidX ni AppCompat; la interfaz es Android nativo
(`android.app.Activity` y vistas programáticas).

## Licencia del proyecto

El código propio de NR AirCrack (Java, recursos, scripts y documentación) se
distribuye bajo **GPLv2**, para ser compatible con aircrack-ng. Ver `LICENSE`.
