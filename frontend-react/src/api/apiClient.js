import { msalInstance, API_SCOPE } from '../msalConfig';

// Base URL del API Gateway. Vacía en desarrollo: el proxy de Vite redirige
// /api -> http://localhost:8080. En producción apunta al dominio del gateway.
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