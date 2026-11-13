import { obtenerAccessTokenCliente } from '../cognito/auth';

// Cliente HTTP de las rutas /api/cliente/** (T03/T04).
//
// Todas estas rutas son PROTEGIDAS con el authorizer JWT de Cognito del API
// Gateway: el access_token viaja como Bearer y API Gateway valida firma,
// issuer y audience (app client) antes de reenviar la peticion al
// wishlist-service. Si el token falta o es de Entra ID, AWS responde 401/403
// y la peticion nunca llega al backend.
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/+$/, '');

function validarBase() {
  if (!API_BASE_URL || API_BASE_URL.includes('REEMPLAZAR')) {
    throw new Error(
      'Falta VITE_API_BASE_URL. Define la URL del API Gateway (salida ApiInvokeUrl ' +
        'del stack de CloudFormation) en .env antes de compilar.'
    );
  }
}

async function leerError(response) {
  let mensaje = `Error ${response.status}`;
  try {
    const body = await response.json();
    if (body?.error) mensaje = body.error;
    else if (body?.message) mensaje = body.message;
  } catch (_) {
    // El cuerpo no era JSON (p.ej. respuesta del authorizer de API Gateway).
  }
  return mensaje;
}

/** GET publico del catalogo: no requiere token. */
export async function obtenerPropiedadesPublicas() {
  validarBase();
  const response = await fetch(`${API_BASE_URL}/api/propiedades`);
  if (!response.ok) throw new Error(await leerError(response));
  return response.json();
}

/** Invoca /api/cliente/** adjuntando el access_token de Cognito como Bearer. */
async function apiFetchCliente(path, options = {}) {
  validarBase();
  const token = await obtenerAccessTokenCliente();

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      ...(options.headers || {})
    }
  });

  if (!response.ok) {
    throw new Error(await leerError(response));
  }
  if (response.status === 204) return null;
  return response.json();
}

/** Perfil del cliente autenticado (email, nombre, rol del claim). */
export function obtenerPerfilCliente() {
  return apiFetchCliente('/api/cliente/perfil');
}

export function obtenerFavoritosCliente() {
  return apiFetchCliente('/api/cliente/favoritos');
}

export function agregarFavoritoCliente(datos) {
  return apiFetchCliente('/api/cliente/favoritos', {
    method: 'POST',
    body: JSON.stringify(datos)
  });
}

export function eliminarFavoritoCliente(propiedadId) {
  return apiFetchCliente(`/api/cliente/favoritos/${propiedadId}`, {
    method: 'DELETE'
  });
}

export function obtenerAlertasCliente() {
  return apiFetchCliente('/api/cliente/alertas');
}

export function obtenerNovedadesCliente() {
  return apiFetchCliente('/api/cliente/novedades');
}

export function descartarNovedadCliente(resumenId) {
  return apiFetchCliente(`/api/cliente/novedades/${resumenId}`, {
    method: 'DELETE'
  });
}

/** Normaliza las respuestas del backend (lista o envoltorio con clave). */
export function comoLista(data, clave) {
  if (Array.isArray(data)) return data;
  if (data && Array.isArray(data[clave])) return data[clave];
  return [];
}
