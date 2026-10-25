# SHADOWHAT — COMPILACIÓN GRATUITA DE APK DESDE TU CELULAR ANDROID

Este paquete contiene los 24 archivos completos del proyecto nativo Android (Kotlin + Jetpack Compose + Room + WorkManager + Iconos Vectoriales + GitHub Actions).

## MÉTODO 1 (RECOMENDADO): Compilar gratis en la nube desde el navegador de tu celular (GitHub Actions)
No necesitas computadora ni instalar gigabytes en tu teléfono:
1. Descomprime este archivo `SHADOWHAT-Android-Kotlin-Compose.zip` en tu celular Android (con el gestor de archivos nativo o ZArchiver).
2. Entra desde el navegador de tu celular a **https://github.com**, inicia sesión (es 100% gratis) y crea un repositorio nuevo llamado `shadowhat-apk`.
3. Sube los archivos descomprimidos (incluyendo la carpeta `.github/workflows/build-apk.yml`).
4. Entra a la pestaña **Actions** de tu repositorio desde tu celular: verás el flujo **"Compilar SHADOWHAT APK"** ejecutándose automáticamente.
5. En ~2 minutos terminará con un check verde ✅. Toca el flujo y descarga el archivo **`SHADOWHAT-Instalable-APK`** que contiene `app-debug.apk` listo para instalar en tu teléfono.

## MÉTODO 2: Compilar localmente dentro de tu celular Android con AndroidIDE
1. Instala la aplicación gratuita y de código abierto **AndroidIDE** en tu celular Android.
2. Descomprime el ZIP en una carpeta de tu memoria interna.
3. Abre la carpeta en AndroidIDE y pulsa el botón **Build / Run** (`gradle assembleDebug`).
4. El APK se generará en `app/build/outputs/apk/debug/app-debug.apk`.

## MÉTODO 3: Desde PC con Android Studio
1. Abre la carpeta en Android Studio.
2. Ve a **Build > Build Bundle(s) / APK(s) > Build APK(s)**.