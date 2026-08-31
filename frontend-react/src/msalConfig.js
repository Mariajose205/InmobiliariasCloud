import { PublicClientApplication } from '@azure/msal-browser';

// Configuración del administrador
export const ADMIN_EMAIL = 'administrador@inmobiliariaduoc.onmicrosoft.com';

// Determina si una cuenta de MSAL corresponde al administrador.
// La comparacion es insensible a mayusculas/minusculas porque Azure puede
// devolver el email con distinta capitalizacion segun el usuario que ingresa.
export const esCuentaAdmin = (cuenta) => {
  if (!cuenta) return false;
  const email = (cuenta.username || cuenta.idTokenClaims?.email || '').toLowerCase();
  return email === ADMIN_EMAIL.toLowerCase();
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
    clientId: 'f345fdc4-1687-49d0-b0cc-e0d45eb85731',
    authority: 'https://login.microsoftonline.com/680da6eb-42e0-4147-bda4-8c06e4819411',
    redirectUri: 'http://localhost:5173'
  },
  cache: {
    cacheLocation: 'sessionStorage',
    storeAuthStateInCookie: false
  }
};

export const msalInstance = new PublicClientApplication(msalConfig);

export const loginRequest = {
  scopes: ['User.Read']
};

export const graphConfig = {
  graphMeEndpoint: 'https://graph.microsoft.com/v1.0/me'
};
