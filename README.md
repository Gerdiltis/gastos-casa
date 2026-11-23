# Gastos Casa

App Android privada para dividir gastos entre amigos (estilo Splitwise). Se instala con un `.apk`, sin Play Store.

- **App**: Kotlin + Jetpack Compose (Material 3), MVVM, Hilt, Navigation Compose.
- **Nube**: Supabase (tier gratuito): Auth con email/contraseña, Postgres con Row Level Security y Realtime.
- **Distribución**: GitHub Actions compila y firma el APK, y lo publica en GitHub Releases. La app avisa cuando hay versión nueva y se actualiza sola.

## Funcionalidades

- Cuenta por persona. Un usuario puede estar en varios grupos.
- Crear un grupo, o unirse con un **código de 6 letras** (botón Compartir).
- Gastos con importe, descripción y categoría. La fecha y la hora se guardan automáticamente.
- Por defecto paga quien introduce el gasto (se puede cambiar). El reparto puede ser entre una persona o entre varias, y los céntimos sobrantes se reparten de uno en uno.
- Categorías propias de cada grupo (emoji + color).
- Saldos por persona y pagos sugeridos para saldar con el mínimo de transferencias. Botón **Registrar pago**.
- **Archivar** los gastos cuando todas las deudas están a 0, sin borrar el grupo. Los gastos archivados se pueden consultar y siguen contando en las estadísticas.
- Estadísticas por categoría, por mes y por año, de tu parte o del total del grupo.
- Tiempo real: los gastos que añaden tus amigos aparecen al instante.

## Puesta en marcha (una sola vez)

### 1. Supabase
1. Crea un proyecto gratuito en <https://supabase.com>.
2. Abre **SQL Editor → New query**, pega todo [`supabase/migrations/001_init.sql`](supabase/migrations/001_init.sql) y pulsa **Run**.
3. Ve a **Authentication → Sign In / Providers → Email** y desactiva **Confirm email**. Así nadie tiene que confirmar el correo, y además el tier gratis solo permite enviar unos pocos emails por hora.
4. En **Project Settings → API** copia la **Project URL** y la clave **anon public**.

### 2. Compilar en local
```bash
cp local.properties.example local.properties   # y rellena SUPABASE_URL, SUPABASE_ANON_KEY, GITHUB_REPO
```
```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleRelease
```
El APK queda en `app/build/outputs/apk/release/app-release.apk`.

### 3. Firma (¡muy importante!)
La keystore está en `keystore/release.jks` y sus contraseñas en `keystore.properties`. Ninguno de los dos archivos se sube a git.
**Guarda una copia en lugar seguro (por ejemplo, en tu gestor de contraseñas).** Si la pierdes, Android no dejará actualizar la app instalada y habrá que desinstalarla, aunque los datos siguen en Supabase.

### 4. GitHub (actualizaciones automáticas)
1. Crea un repo **público** (el actualizador consulta la API pública de Releases, y el repo no contiene secretos) y sube el proyecto.
2. En **Settings → Secrets and variables → Actions** añade:

| Secret | Valor |
|---|---|
| `KEYSTORE_BASE64` | salida de `base64 -i keystore/release.jks` |
| `KEYSTORE_PASSWORD` | de `keystore.properties` |
| `KEY_ALIAS` | `gastoscasa` |
| `KEY_PASSWORD` | de `keystore.properties` |
| `SUPABASE_URL` | Project URL |
| `SUPABASE_ANON_KEY` | anon public key |

3. Para publicar una versión:
```bash
git tag v1.0.0 && git push origin v1.0.0
```
El workflow `Release APK` pasa los tests, firma y adjunta `gastos-casa-1.0.0.apk` a la release. Al abrir la app, tus amigos verán el aviso **Nueva versión → Actualizar**.

El workflow `Supabase keep-alive` hace un ping diario para que el proyecto gratuito no se pause (Supabase lo pausa tras 7 días sin actividad).

### 5. Instalar en un móvil
Descarga el `.apk` de la última release desde el móvil, ábrelo y acepta **Instalar apps desconocidas** la primera vez.

## Estructura
```
app/src/main/java/com/gastoscasa/
├─ data/      Supabase (Auth, Postgrest, Realtime), repositorios, actualizador
├─ domain/    Modelos y lógica pura: reparto, saldos, liquidación, estadísticas (con tests)
├─ ui/        Tema, componentes (tarjetas, píldoras, gráficos Canvas), pantallas
└─ update/    Instalación del APK descargado (FileProvider)
supabase/migrations/001_init.sql   Tablas, RLS y funciones RPC
.github/workflows/                 release.yml y keepalive.yml
```

Los importes se guardan en **céntimos (enteros)** para evitar errores de redondeo.
