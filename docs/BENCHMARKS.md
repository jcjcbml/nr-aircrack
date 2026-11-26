# Benchmarks reales

Todos los datos fueron medidos en el mismo dispositivo, con el motor incluido
en el APK (`aircrack-ng 1.7`, compilado con Clang/NDK, `-O2`, NEON).

## Dispositivo

| Campo | Valor |
|---|---|
| Marca / modelo | POCO / RMX5070 *(según `getprop`)* |
| Android | 15 (API 35) |
| SoC | MediaTek MT6899 |
| Núcleos | 8: 4× 2,1 GHz + 3× 3,0 GHz + 1× 3,25 GHz |
| ABI | arm64-v8a |

## Escalado de hilos

Comando: `aircrack-ng -S -p N`, 20 s por punto, app en primer plano, campaña
de fondo pausada.

| Hilos | keys/s (promedio de las últimas muestras) | Pico |
|---:|---:|---:|
| 1 | 713 | 714 |
| 2 | 1.441 | 1.444 |
| 4 | 2.155 | 2.161 |
| 6 | 2.991 | 3.024 |
| 7 | 2.935 | 3.045 |
| **8** | **3.222** | **3.247** |
| 9 | 2.868 | 3.004 |
| 10 | 2.978 | 2.996 |
| 12 | 2.990 | 3.012 |
| 8 procesos × `-p 1` | 2.799 (suma) | — |

Conclusión: **8 hilos es el óptimo**. Más hilos no ayudan porque solo hay 8
núcleos y aumenta la contención; dividir en procesos independientes es peor
(2.799 vs 3.222).

## Ráfagas y uso real

- Pico observado: **5.228 keys/s** durante una ráfaga corta con el teléfono
  frío (el power HAL permite subir de 2,2 a 2,8–3,25 GHz unos segundos).
- Crackeo real sostenido: **~2.900–3.700 keys/s**.
- El límite sostenido del fabricante es **2,2 GHz**; el HAL de MediaTek
  reimpone ese valor aunque se escriba 3,25 GHz con root.
- **Cargando**: el techo baja a **2,0 GHz** y el rendimiento a ~1.100–1.500
  keys/s. Para máxima velocidad: cargar al 100 %, desconectar y enfriar.

## Prueba `-O3`

Se recompiló aircrack-ng con `-O3 -funroll-loops` y se comparó con el binario
`-O2` en 8 pares intercalados (mismo estado térmico):

| Par | O2 (keys/s) | O3 (keys/s) | O3/O2 |
|---:|---:|---:|---:|
| 1 | 1.337 | 1.604 | 1,199 |
| 2 | 1.394 | 1.366 | 0,980 |
| 3 | 1.621 | 1.985 | 1,224 |
| 4 | 1.747 | 1.672 | 0,957 |
| 5 | 1.953 | 1.992 | 1,020 |
| 6 | 1.732 | 1.502 | 0,867 |
| 7 | 1.385 | 1.392 | 1,005 |
| 8 | 1.447 | 1.407 | 0,972 |

**Mediana: 0,99**. No hay ganancia; se mantiene `-O2`.

## Comparación con hashcat

En el mismo teléfono, con el runtime PoCL:

- hashcat `-m 22000`: ~1.187 H/s por worker (~2.374 con 2 workers).
- Con `-n 4` crasheaba a los ~5 min (`stack corruption`, SIGSEGV/SIGABRT).
- aircrack-ng: ~3.200 keys/s y estable.

## Metodología

- `-S` de aircrack-ng es su benchmark interno; se descartan las primeras
  muestras y se promedian las últimas 8–12.
- Para comparar O2/O3 se alternaron las ejecuciones y se usó la mediana de
  las proporciones, porque la frecuencia cambia con la temperatura.
- Los valores absolutos bajan mucho con el calor: un teléfono frío puede
  duplicar el resultado de uno caliente.

## Reproducir

```sh
# en el teléfono, con el binario del APK
LD_LIBRARY_PATH=.../assets/air/lib \
  .../assets/air/bin/aircrack-ng -S -p 8
```

Los resultados dependen del SoC, la temperatura, la carga del sistema y el
estado de la batería. Los de esta tabla son de referencia, no una garantía.
