import { msalInstance, API_SCOPE } from '../msalConfig';

// Base URL del API Gateway de AWS. La entrega el despliegue del stack de
// CloudFormation (salida ApiInvokeUrl) y Vite la incorpora al bundle en tiempo
// de build. Sin barra final.
//
// El navegador llama DIRECTO a la API, ya no al dominio del frontend: el proxy
// /api de nginx fue eliminado. Como son origenes distintos, el CORS lo resuelve
// el propio API Gateway.
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/+$/, '');

const API_SCOPE_LIST = [API_SCOPE];

/**
 * Obtiene el ACCESS TOKEN de nuestra API con MSAL. Pide el scope delegado
 * api://{client-id}/access_as_user; si el silencioso falla (consentimiento
 * pendiente) intenta un popup.
 */
export async function obtenerAccessToken() {
  const account = msalInstance.getActiveAccount() || msalInstance.getAllAccounts()[0];
  if (!account) {
    throw new Error('No hay una sesión iniciada con Microsoft.');
  }

  const request = { scopes: API_SCOPE_LIST, account };

  try {
    const response = await msalInstance.acquireTokenSilent(request);
    return response.accessToken;
  } catch (error) {
    // Si el token silencioso no se puede renovar (consentimiento/interacción
    // requerida), se pide de forma interactiva en popup.
    if (error && (error.name === 'InteractionRequiredAuthError' || error.errorCode === 'interaction_required')) {
      const response = await msalInstance.acquireTokenPopup(request);
      return response.accessToken;
    }
    throw error;
  }
}

/**
 * Invoca la API adjuntando el access token como Bearer. Requiere sesión.
 */
async function apiFetchAutenticado(path, options = {}) {
  const token = await obtenerAccessToken();
  return apiFetch(path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      ...(options.headers || {})
    }
  });
}

/**
 * Invoca la API sin token (lectura pública del catálogo).
 */
async function apiFetch(path, options = {}) {
  if (!API_BASE_URL || API_BASE_URL.includes('REEMPLAZAR')) {
    throw new Error(
      'Falta VITE_API_BASE_URL. Define la URL del API Gateway (salida ApiInvokeUrl ' +
      'del stack de CloudFormation) en .env antes de arrancar la app.'
    );
  }

  const url = `${API_BASE_URL}${path}`;
  const response = await fetch(url, options);

  if (!response.ok) {
    let mensaje = `Error ${response.status}`;
    try {
      const body = await response.json();
      if (body?.error) mensaje = body.error;
    } catch (_) {
      // cuerpo no es JSON
    }
    throw new Error(mensaje);
  }

  if (response.status === 204) {
    return null;
  }
  return response.json();
}

/**
 * GET público del catálogo (no requiere sesión).
 */
export function obtenerPropiedadesPublicas() {
  return apiFetch('/api/propiedades');
}

export function obtenerPropiedades() {
  return apiFetchAutenticado('/api/propiedades');
}

export function obtenerPropiedad(id) {
  return apiFetchAutenticado(`/api/propiedades/${id}`);
}

export function crearPropiedad(propiedad) {
  return apiFetchAutenticado('/api/propiedades', {
    method: 'POST',
    body: JSON.stringify(propiedad)
  });
}

export function actualizarPropiedad(id, propiedad) {
  return apiFetchAutenticado(`/api/propiedades/${id}`, {
    method: 'PUT',
    body: JSON.stringify(propiedad)
  });
}

export function eliminarPropiedad(id) {
  return apiFetchAutenticado(`/api/propiedades/${id}`, {
    method: 'DELETE'
  });
}

/**
 * Devuelve la identidad del usuario autenticado (exige access token de la API).
 */
export function obtenerUsuarioActual() {
  return apiFetchAutenticado('/api/me');
}

/**
 * Devuelve el registro de auditoria (quien y cuando realizo acciones sobre
 * las propiedades). Solo el administrador puede consultarla.
 */
export function obtenerAuditoria() {
  return apiFetchAutenticado('/api/auditoria');
}