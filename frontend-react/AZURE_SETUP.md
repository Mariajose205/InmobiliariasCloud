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

### 4. Configurar el archivo .env

Crea un archivo `.env` en la raíz del proyecto con las siguientes variables:

```env
VITE_AZURE_CLIENT_ID=tu_client_id_aqui
VITE_AZURE_AUTHORITY=https://login.microsoftonline.com/tu_tenant_id
VITE_AZURE_REDIRECT_URI=http://localhost:5173
```

**Importante**: Reemplaza los valores con los que obtuviste de Azure Portal.

### 5. Configurar permisos (opcional)

Si necesitas acceder a información del usuario desde Microsoft Graph API:

1. En tu aplicación registrada, ve a **API permissions**
2. Haz clic en **Add a permission**
3. Selecciona **Microsoft Graph**
4. Selecciona **Delegated permissions**
5. Busca y selecciona los permisos que necesitas (ej: `User.Read`)
6. Haz clic en **Add permissions**

### 6. Reiniciar el servidor

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
