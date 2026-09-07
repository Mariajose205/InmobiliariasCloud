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
   - URI: `http://localhost:5173`
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
VITE_AZURE_REDIRECT_URI=http://localhost:5173
VITE_AZURE_API_SCOPE=api://tu_client_id_aqui/access_as_user
VITE_API_BASE_URL=
```

**Importante**: Reemplaza los valores con los que obtuviste de Azure Portal.
`VITE_API_BASE_URL` vacío usa el proxy del dev server de Vite (api → 8080).

### 6. Configurar el backend (api-gateway)

En `backend/api-gateway/src/main/resources/application.yml`:

```yaml
azure:
  tenant-id: "TU_TENANT_ID"
  client-id: "TU_CLIENT_ID"
  admin-email: "administrador@inmobiliariaduoc.onmicrosoft.com"
  app-id-uri: "api://TU_CLIENT_ID"
  scope: "access_as_user"
  audiences: "api://TU_CLIENT_ID,TU_CLIENT_ID"
```

Spring valida `iss` (issuer-uri del tenant) y `aud` (acepta el App ID URI
`api://...` y/o el GUID del client-id). Además exige el claim `scp` =
`access_as_user`, por lo que el **ID Token** y el **access token de Graph** ya
no sirven para las rutas privadas:
- ID Token: `aud` = client-id de la SPA y **no trae `scp`** → rechazado.
- Token de Graph: `aud` = `https://graph.microsoft.com` y `scp` = `User.Read` → rechazado.
- Access token de nuestra API: `aud` = `api://{client-id}`, `scp` = `access_as_user` → aceptado.

### 7. Configurar los roles de usuario (admin y corredor)

El sistema distingue dos roles internos (sin necesidad de grupos en Entra):

- **ADMIN** (`administrador@inmobiliariaduoc.onmicrosoft.com`): puede crear,
  editar y eliminar propiedades.
- **CORREDOR** (`Corredor@InmobiliariaDuoc.onmicrosoft.com`): puede **publicar
  (POST)** y **eliminar (DELETE)** propiedades, pero **NO editar (PUT)**.

Los roles se configuran en `backend/api-gateway/src/main/resources/application.yml`:

```yaml
azure:
  tenant-id: "680da6eb-42e0-4147-bda4-8c06e4819411"
  client-id: "f345fdc4-1687-49d0-b0cc-e0d45eb85731"
  admin-email: "administrador@inmobiliariaduoc.onmicrosoft.com"
  admin-object-id: ""   # opcional, ver abajo
  corredor-email: "Corredor@InmobiliariaDuoc.onmicrosoft.com"
  corredor-object-id: "0a73de94-2722-4221-836c-bd7f3af16980"
  app-id-uri: "api://f345fdc4-1687-49d0-b0cc-e0d45eb85731"
  scope: "access_as_user"
  audiences: "api://f345fdc4-1687-49d0-b0cc-e0d45eb85731,f345fdc4-1687-49d0-b0cc-e0d45eb85731"
```

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

Cuando despliegues a producción:

1. Agrega la URL de producción como Redirect URI en Azure Portal
2. Actualiza `VITE_AZURE_REDIRECT_URI` en tu archivo `.env.production`
3. Considera usar `loginRedirect` en lugar de `loginPopup` para una mejor experiencia móvil
