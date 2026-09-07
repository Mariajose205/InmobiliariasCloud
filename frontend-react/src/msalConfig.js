import { PublicClientApplication } from '@azure/msal-browser';

// Configuración de Azure Entra ID (desde .env, con respaldo hardcodeado).
const VITE_AZURE_CLIENT_ID = import.meta.env.VITE_AZURE_CLIENT_ID || 'f345fdc4-1687-49d0-b0cc-e0d45eb85731';
const VITE_AZURE_AUTHORITY = import.meta.env.VITE_AZURE_AUTHORITY || 'https://login.microsoftonline.com/680da6eb-42e0-4147-bda4-8c06e4819411';
const VITE_AZURE_REDIRECT_URI = import.meta.env.VITE_AZURE_REDIRECT_URI || 'http://localhost:5173';

// Scope delegado de NUESTRA API (Expose an API -> api://{client-id}/access_as_user).
// Con este scope Entra ID emite un ACCESS TOKEN cuya audiencia es la API, no
// Graph ni el client de la SPA. Ese es el token que se envia a Spring.
export const API_SCOPE = import.meta.env.VITE_AZURE_API_SCOPE
    || `api://${VITE_AZURE_CLIENT_ID}/access_as_user`;

// Configuración del administrador
export const ADMIN_EMAIL = 'administrador@inmobiliariaduoc.onmicrosoft.com';

// Configuración del corredor (puede publicar y eliminar, pero no editar)
export const CORREDOR_EMAIL = 'Corredor@InmobiliariaDuoc.onmicrosoft.com';

// Determina si una cuenta de MSAL corresponde al administrador.
// La comparacion es insensible a mayusculas/minusculas porque Azure puede
// devolver el email con distinta capitalizacion segun el usuario que ingresa.
export const esCuentaAdmin = (cuenta) => {
  if (!cuenta) return false;
  const email = (cuenta.username || cuenta.idTokenClaims?.email || '').toLowerCase();
  return email === ADMIN_EMAIL.toLowerCase();
};

// Determina si una cuenta de MSAL corresponde al corredor.
export const esCuentaCorredor = (cuenta) => {
  if (!cuenta) return false;
  const email = (cuenta.username || cuenta.idTokenClaims?.email || '').toLowerCase();
  return email === CORREDOR_EMAIL.toLowerCase();
};

// Devuelve 'admin', 'corredor' o null según la cuenta de MSAL.
export const obtenerRolCuenta = (cuenta) => {
  if (esCuentaAdmin(cuenta)) return 'admin';
  if (esCuentaCorredor(cuenta)) return 'corredor';
  return null;
};

// Obtiene la cuenta activa (si existe) y devuelve si es administrador.
export const obtenerEstadoAdmin = (accounts) => {
  const cuenta = accounts && accounts.length > 0 ? accounts[0] : null;
  return {
    cuenta,
    isAdmin: esCuentaAdmin(cuenta),
    isLoggedIn: !!cuenta
  };
};

// Persistencia manual de la sesión en sessionStorage. Sirve para que la
// barra de navegacion muestre "Cerrar Sesion" de forma fiable incluso si la
// cache interna de MSAL todavia no ha poblado la lista de cuentas, y para
// que el inicio de sesion reconozca al admin.
const SESSION_FLAG_KEY = 'inmobiliaria_msal_email';

export const guardarSesion = (email) => {
  if (email) {
    sessionStorage.setItem(SESSION_FLAG_KEY, email);
  }
};

export const obtenerEmailSesion = () => {
  return sessionStorage.getItem(SESSION_FLAG_KEY) || '';
};

export const limpiarSesion = () => {
  sessionStorage.removeItem(SESSION_FLAG_KEY);
};

export const haySesion = () => {
  return !!sessionStorage.getItem(SESSION_FLAG_KEY);
};

const msalConfig = {
  auth: {
    clientId: VITE_AZURE_CLIENT_ID,
    authority: VITE_AZURE_AUTHORITY,
    redirectUri: VITE_AZURE_REDIRECT_URI
  },
  cache: {
    cacheLocation: 'sessionStorage',
    storeAuthStateInCookie: false
  }
};

export const msalInstance = new PublicClientApplication(msalConfig);

export const loginRequest = {
  scopes: [API_SCOPE]
};
