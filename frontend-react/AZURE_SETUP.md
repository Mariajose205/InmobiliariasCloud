# Configuración de Azure Entra ID para Autenticación

## Pasos para configurar Azure Entra ID

### 1. Crear una aplicación en Azure Portal

1. Ve a [Azure Portal](https://portal.azure.com/)
2. Inicia sesión con tu cuenta de Microsoft
3. Busca y selecciona **Azure Active Directory** (ahora llamado **Microsoft Entra ID**)
4. En el menú izquierdo, selecciona **App registrations**
5. Haz clic en **New registration**

### 2. Registrar la aplicación

1. **Name**: Escribe un nombre para tu aplicación (ej: "Inmobiliarias Duroc")
2. **Supported account types**: Selecciona "Accounts in any organizational directory and personal Microsoft accounts" si quieres permitir acceso a cualquier usuario, o "Accounts in this organizational directory only" para solo tu organización
3. **Redirect URI (optional)**:
   - Selecciona **Web**
   - URI: `https://inmobiliariasduoc.duckdns.org/`
4. Haz clic en **Register**

### 3. Obtener las credenciales

1. Después de registrar, copia los siguientes valores:
   - **Application (client) ID** → Este es tu `VITE_AZURE_CLIENT_ID`
   - **Directory (tenant) ID** → Este es tu tenant ID para la authority

2. Para obtener la authority URL:
   - Ve a **Endpoints** en el menú izquierdo
   - Copia el **Microsoft Graph API endpoint** o usa este formato:
   - `https://login.microsoftonline.com/TU_TENANT_ID`

### 4. Exponer la API (Expose an API) y crear el scope `access_as_user`

El backend Spring actúa como **Resource Server**: debe recibir un `access token`
cuya **audiencia** sea nuestra API. Para lograrlo, la misma app registrada (o una
app separada dedicada a la API) debe exponer un scope:

1. En **App registrations** → tu app → **Expose an API**
2. En **Application ID URI**, haz clic en **Set** y usa:
   ```
   api://f345fdc4-1687-49d0-b0cc-e0d45eb85731
   ```
3. En **Scopes defined by this API**, haz clic en **Add a scope**
   - **Scope name**: `access_as_user`
   - **Who can consent?**: Admins and users
   - **Admin consent display name**: `Inmobiliaria Duroc: Acceso de usuarios`
   - **Admin consent description**: `Permite que un usuario use la app con su identidad`
   - Habilita **State**: `Enabled`
   - Haz clic en **Add scope**
4. El scope completo queda como:
   ```
   api://f345fdc4-1687-49d0-b0cc-e0d45eb85731/access_as_user
   ```
5. (Opcional pero recomendado) En **Authorized client applications** agrega el
   `client-id` de la SPA para que el consentimiento sea de un solo clic.

> Con esto, Entra ID emite **access tokens v2.0** con `aud` =
> `api://{client-id}` y `scp` = `access_as_user`.

### 5. Configurar el archivo .env

Crea un archivo `.env` en la raíz del proyecto con las siguientes variables:

```env
VITE_AZURE_CLIENT_ID=tu_client_id_aqui
VITE_AZURE_AUTHORITY=https://login.microsoftonline.com/tu_tenant_id
VITE_AZURE_REDIRECT_URI=https://inmobiliariasduoc.duckdns.org/
VITE_AZURE_API_SCOPE=api://tu_client_id_aqui/access_as_user
VITE_API_BASE_URL=https://TU_API_ID.execute-api.TU_REGION.amazonaws.com
```

**Importante**: Reemplaza los valores con los que obtuviste de Azure Portal.
`VITE_AZURE_REDIRECT_URI` debe coincidir EXACTAMENTE (incluida la barra final,
si así lo registraste) con el Redirect URI de Azure.

`VITE_API_BASE_URL` es la URL del **API Gateway de AWS**, sin barra final. Como
el navegador llama directo a AWS y no al dominio del frontend, ambos son orígenes
distintos y el CORS lo resuelve el propio API Gateway. El redirect URI **sigue
siendo el del frontend**: en Entra ID no hay que registrar nada nuevo.

### 6. Configurar la API en AWS

El token ya no lo valida una aplicación propia: lo valida el autorizador JWT
nativo del API Gateway. En `backend/aws/api-gateway.yaml` (o en la consola) hay que
que coincidan con lo de arriba:

```yaml
# Identidad (mismos valores que VITE_AZURE_*)
AzureTenantId: "TU_TENANT_ID"
AzureClientId: "TU_CLIENT_ID"
AzureIssuer: "https://login.microsoftonline.com/TU_TENANT_ID/v2.0"
AzureApiScope: "api://TU_CLIENT_ID/access_as_user"

# CORS: el origen debe ser el del FRONTEND, no el de la API
AllowedOriginProd: "https://inmobiliariasduoc.duckdns.org"
```

API Gateway valida `iss`, `aud`, `exp`, `nbf`, `iat` y el claim `scp`. Como cada
ruta exige el scope `access_as_user`, el **ID Token** y el **access token de
Graph** no sirven como credencial:
- ID Token: `aud` = client-id de la SPA y **no trae `scp`** → rechazado.
- Token de Graph: `aud` = `https://graph.microsoft.com` y `scp` = `User.Read` → rechazado.
- Access token de nuestra API: `aud` = `api://{client-id}`, `scp` = `access_as_user` → aceptado.

El detalle completo del despliegue está en `backend/aws/README.md`.

### 7. Configurar los roles de usuario (admin y corredor)

El sistema distingue dos roles internos (sin necesidad de grupos en Entra):

- **ADMIN** (`administrador@inmobiliariaduoc.onmicrosoft.com`): puede crear,
  editar y eliminar propiedades.
- **CORREDOR** (`Corredor@InmobiliariaDuoc.onmicrosoft.com`): puede **publicar
  (POST)** y **eliminar (DELETE)** propiedades, pero **NO editar (PUT)**.

La autenticación la hace API Gateway, pero **el rol lo resuelve el microservicio**,
porque API Gateway no conoce los roles del negocio. Se configura en
`backend/inmobiliaria-service/src/main/resources/application.yml`:

```yaml
seguridad:
  admin-email: "administrador@inmobiliariaduoc.onmicrosoft.com"
  admin-oid: ""   # opcional, ver abajo
  corredor-email: "Corredor@InmobiliariaDuoc.onmicrosoft.com"
  corredor-oid: "0a73de94-2722-4221-836c-bd7f3af16980"
```

En producción llegan por variables de entorno desde `docker-compose.yml`
(`ADMIN_EMAIL`, `ADMIN_OID`, `CORREDOR_EMAIL`, `CORREDOR_OID`).

> **Importante sobre el `oid` (object-id):** los access tokens de Entra **no
> incluyen `email` ni `preferred_username` por defecto**. Para que la
> identificación por email funcione hay que añadir **optional claims**
> (`email`, `preferred_username`) al access token en **Token configuration →
> Add optional claim → Access token**. Mientras tanto, los roles se resuelven
> de forma confiable por **object-id** (`oid`), que sí está siempre presente.
> Para cambiar de usuario/revisar su `oid`: **Entra ID → Users → clic en el
> usuario → copiar Object ID**.

### 8. Reiniciar el servidor

Después de crear/modificar el archivo `.env`, reinicia el servidor de desarrollo:

```bash
npm run dev
```

## Uso

- **Iniciar sesión**: Ve a `/login` y haz clic en "Iniciar sesión con Microsoft"
- **Cerrar sesión**: En el navbar, haz clic en tu nombre y selecciona "Cerrar Sesión"

## Solución de problemas

### Error: "AADSTS50011: The reply URL specified in the request does not match the reply URLs configured for the application"

- Verifica que el `VITE_AZURE_REDIRECT_URI` coincida exactamente con el configurado en Azure Portal
- Asegúrate de incluir el protocolo (http:// o https://)

### Error: "AADSTS700016: Application with identifier was not found in the directory"

- Verifica que el `VITE_AZURE_CLIENT_ID` sea correcto
- Asegúrate de que la aplicación esté registrada en el directorio correcto

### Error: "AADSTS50001: The application was not found in the directory"

- Verifica que la authority URL sea correcta
- Asegúrate de que el tenant ID sea correcto

## Para producción

El proyecto ya apunta SOLO a producción (`https://inmobiliariasduoc.duckdns.org/`):

1. Este dominio debe estar registrado como **Redirect URI** en Azure Portal
   (App registrations → Authentication → Add a platform → Web). Sin la barra
   final, agrega también `https://inmobiliariasduoc.duckdns.org`.
2. `VITE_AZURE_REDIRECT_URI` apunta a ese dominio, pero `VITE_API_BASE_URL`
   apunta a la URL del **API Gateway** (`https://<id>.execute-api.<region>.amazonaws.com`).
   Son orígenes distintos a propósito, y el CORS lo resuelve la API.
3. En el CI/CD (GitHub Actions → repo settings → Variables), `VITE_API_BASE_URL`
   debe estar seteado a la URL del API Gateway para que el build de Docker lo
   inyecte (los build-args del workflow sobreescriben los `.env`).
4. El origen permitido por CORS en la API debe ser exactamente el dominio del
   frontend. Si no coincide, el navegador descarta todas las respuestas y la
   consola muestra un error de CORS aunque la API responda bien. Ver
   `backend/aws/README.md`.
