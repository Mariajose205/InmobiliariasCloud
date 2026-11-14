import {
  AuthenticationDetails,
  CognitoUser,
  CognitoUserAttribute
} from 'amazon-cognito-identity-js';
import {
  COGNITO_CLIENT_ID,
  COGNITO_CONFIGURADO,
  MENSAJE_SIN_CONFIGURAR,
  userPool
} from './config';

// Sesion del cliente publico (Amazon Cognito).
//
// Flujo implementado (T02 de la rubrica):
//  - registro con nombre, apellido, email y contrasena, con confirmacion por
//    codigo de email,
//  - login con SRP (la contrasena no viaja al servidor),
//  - refresh del access_token usando el refresh_token,
//  - forgot-password y confirmacion del nuevo password.
//
// El access_token es el que se envia como Bearer a /api/cliente/** (T03). El
// rol viaja en el claim `rol=CLIENTE`, que agrega el trigger PreTokenGeneration
// del User Pool; aqui solo se lee para mostrarlo en la interfaz, nunca se
// decide con el si se permite una operacion (eso lo exige el backend).

const KEY_EMAIL = 'inmobiliaria_cognito_email';
const KEY_NOMBRE = 'inmobiliaria_cognito_nombre';
const KEY_ROL = 'inmobiliaria_cognito_rol';
const KEY_SUB = 'inmobiliaria_cognito_sub';

function exigirConfiguracion() {
  if (!COGNITO_CONFIGURADO || !userPool) {
    throw new Error(MENSAJE_SIN_CONFIGURAR);
  }
}

/**
 * Crea un CognitoUser con el MISMO storage que el pool.
 *
 * Ojo: `new CognitoUser({ Username, Pool })` NO hereda `pool.storage`; el SDK
 * usa el storage por defecto (localStorage). Con el pool configurado en
 * sessionStorage, el login guardaba los tokens en localStorage y
 * `userPool.getCurrentUser()` (que lee sessionStorage) no los encontraba:
 * la sesion parecia iniciada pero desaparecia al instante. Por eso hay que
 * pasar `Storage: userPool.storage` en cada instancia.
 */
function crearUsuario(email) {
  return new CognitoUser({
    Username: String(email).trim(),
    Pool: userPool,
    Storage: userPool.storage
  });
}

/** Traduce los errores de Cognito a mensajes accionables en espanol. */
function traducirError(err) {
  const codigo = err?.code || '';
  const mensajes = {
    UsernameExistsException: 'Ya existe una cuenta registrada con ese correo.',
    AliasExistsException: 'Ya existe una cuenta registrada con ese correo.',
    InvalidPasswordException:
      'La contrasena no cumple la politica: minimo 8 caracteres, con mayuscula, minuscula y numero.',
    InvalidParameterException: 'Revisa los datos ingresados e intentalo de nuevo.',
    CodeMismatchException: 'El codigo no es correcto o ya expiro.',
    ExpiredCodeException: 'El codigo expiro. Solicita uno nuevo.',
    NotAuthorizedException: 'Correo o contrasena incorrectos (o la sesion ya expiro).',
    UserNotConfirmedException:
      'La cuenta aun no esta confirmada. Revisa el codigo enviado a tu correo.',
    UserNotFoundException: 'No existe una cuenta registrada con ese correo.',
    LimitExceededException: 'Demasiados intentos seguidos. Espera unos minutos antes de reintentar.',
    TooManyRequestsException: 'Demasiadas solicitudes. Espera unos minutos antes de reintentar.',
    TooManyFailedAttemptsException: 'Demasiados intentos fallidos. Solicita un codigo nuevo.',
    PasswordResetRequiredException:
      'Cognito exige cambiar la contrasena antes de continuar. Usa "Recuperar contrasena".'
  };
  const error = new Error(mensajes[codigo] || err?.message || 'Ocurrio un error inesperado.');
  error.code = codigo;
  return error;
}

/** Decodifica el payload de un JWT (sin validar la firma: la valida la API). */
function decodificarJwt(token) {
  if (!token) return {};
  try {
    const parte = token.split('.')[1];
    if (!parte) return {};
    const base64 = parte.replace(/-/g, '+').replace(/_/g, '/');
    const relleno = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
    const json = decodeURIComponent(
      atob(relleno)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    return JSON.parse(json);
  } catch (_) {
    return {};
  }
}

function guardarDatosSesion(usuario, sesion) {
  const claimsAcceso = sesion.getAccessToken().decodePayload();
  const claimsId = sesion.getIdToken().decodePayload();

  const email = claimsAcceso.email || claimsId.email || usuario.getUsername() || '';
  const nombre = claimsId.name || claimsAcceso.name || '';
  const rol = claimsAcceso.rol || '';

  sessionStorage.setItem(KEY_EMAIL, email);
  sessionStorage.setItem(KEY_NOMBRE, nombre);
  sessionStorage.setItem(KEY_ROL, rol);
  sessionStorage.setItem(KEY_SUB, claimsAcceso.sub || '');

  return { email, nombre, rol, sub: claimsAcceso.sub || '' };
}

function limpiarDatosSesion() {
  sessionStorage.removeItem(KEY_EMAIL);
  sessionStorage.removeItem(KEY_NOMBRE);
  sessionStorage.removeItem(KEY_ROL);
  sessionStorage.removeItem(KEY_SUB);
}

/**
 * Borra los tokens que el SDK de Cognito guarda en sessionStorage:
 * `CognitoIdentityServiceProvider.<clientId>.<usuario>.<tipo>` y la clave
 * `LastAuthUser`. Se usa cuando la sesion ya no sirve (refresh revocado o
 * vencido): si quedan, `getCurrentUser()` sigue devolviendo un usuario y cada
 * llamada vuelve a intentar el refresh contra Cognito (el POST que responde
 * 400 "Invalid Refresh Token").
 */
function limpiarTokensSdk() {
  try {
    const prefijo = `CognitoIdentityServiceProvider.${COGNITO_CLIENT_ID}.`;
    [window.sessionStorage, window.localStorage].forEach((storage) => {
      Object.keys(storage)
        .filter((clave) => clave.startsWith(prefijo))
        .forEach((clave) => storage.removeItem(clave));
    });
  } catch (_) {
    // El almacenamiento puede no estar disponible (modo privado estricto).
  }
}

/**
 * Version anterior del login creaba los CognitoUser sin `Storage`, asi que
 * los tokens quedaban en localStorage aunque el pool leyera sessionStorage.
 * Al cargar el modulo se borran esos restos: ya no sirven y no queremos que
 * queden tokens de sesiones viejas en el navegador.
 */
function limpiarTokensLocalesHeredados() {
  try {
    const prefijo = `CognitoIdentityServiceProvider.${COGNITO_CLIENT_ID}.`;
    Object.keys(window.localStorage)
      .filter((clave) => clave.startsWith(prefijo))
      .forEach((clave) => window.localStorage.removeItem(clave));
  } catch (_) {
    // Sin localStorage no hay nada que migrar.
  }
}

limpiarTokensLocalesHeredados();

/** Error con codigo propio para que la UI distinga "sesion caida" de un fallo de red. */
function errorSesion(mensaje) {
  const error = new Error(mensaje || 'Tu sesion expiro. Vuelve a iniciar sesion.');
  error.code = 'SESSION_EXPIRED';
  return error;
}

/** Devuelve la sesion guardada (solo para pintar la UI), o null. */
export function obtenerSesionCliente() {
  if (!COGNITO_CONFIGURADO || !userPool) return null;
  const email = sessionStorage.getItem(KEY_EMAIL);
  if (!email) return null;
  if (!userPool.getCurrentUser()) {
    // Quedaron restos de una sesion anterior (por ejemplo, tokens del SDK sin
    // la clave LastAuthUser): se limpian para no reintentar un refresh muerto.
    limpiarDatosSesion();
    limpiarTokensSdk();
    return null;
  }
  return {
    email,
    nombre: sessionStorage.getItem(KEY_NOMBRE) || '',
    rol: sessionStorage.getItem(KEY_ROL) || 'CLIENTE',
    sub: sessionStorage.getItem(KEY_SUB) || ''
  };
}

export function haySesionCliente() {
  return Boolean(obtenerSesionCliente());
}

/** Claims utiles del access_token vigente (para mostrar en "Mi cuenta"). */
export function obtenerClaimsTokenCliente() {
  const usuario = userPool?.getCurrentUser();
  if (!usuario) return null;

  // getCurrentUser() devuelve un CognitoUser nuevo sin sesion en memoria, asi
  // que getSignInUserSession() normalmente es null. El token se lee del mismo
  // storage que usa el SDK.
  let token = null;
  try {
    token = usuario.getSignInUserSession()?.getAccessToken()?.getJwtToken() || null;
  } catch (_) {
    token = null;
  }
  if (!token) {
    try {
      token = userPool?.storage?.getItem(
        `CognitoIdentityServiceProvider.${COGNITO_CLIENT_ID}.${usuario.getUsername()}.accessToken`
      );
    } catch (_) {
      token = null;
    }
  }
  if (!token) return null;

  const claims = decodificarJwt(token);
  return {
    iss: claims.iss,
    client_id: claims.client_id || claims.aud,
    token_use: claims.token_use,
    exp: claims.exp,
    rol: claims.rol,
    email: claims.email,
    sub: claims.sub
  };
}

/** Registro de un cliente nuevo: email, contrasena, nombre y apellido. */
export function registrarCliente(email, password, nombre, apellido) {
  return new Promise((resolve, reject) => {
    try {
      exigirConfiguracion();
    } catch (e) {
      reject(e);
      return;
    }

    // `name` es el atributo que el trigger PreTokenGeneration copia al claim
    // `name`, y de ahi sale el header X-User-Name que el backend devuelve como
    // `nombre` en GET /api/cliente/perfil. Se guarda completo para que el
    // perfil muestre nombre y apellido juntos; ademas se persisten
    // `given_name` y `family_name` por separado en el User Pool.
    const nombreLimpio = String(nombre || '').trim();
    const apellidoLimpio = String(apellido || '').trim();
    const nombreCompleto = [nombreLimpio, apellidoLimpio].filter(Boolean).join(' ');

    const atributos = [];
    if (nombreCompleto) {
      atributos.push(new CognitoUserAttribute({ Name: 'name', Value: nombreCompleto }));
    }
    if (nombreLimpio) {
      atributos.push(new CognitoUserAttribute({ Name: 'given_name', Value: nombreLimpio }));
    }
    if (apellidoLimpio) {
      atributos.push(new CognitoUserAttribute({ Name: 'family_name', Value: apellidoLimpio }));
    }

    userPool.signUp(String(email).trim(), password, atributos, null, (err, resultado) => {
      if (err) {
        reject(traducirError(err));
        return;
      }
      resolve({
        usuario: resultado.user.getUsername(),
        confirmado: resultado.userConfirmed,
        destino: resultado.codeDeliveryDetails?.Destination || String(email).trim()
      });
    });
  });
}

/** Confirma la cuenta con el codigo que Cognito envio por email. */
export function confirmarRegistroCliente(email, codigo) {
  return new Promise((resolve, reject) => {
    try {
      exigirConfiguracion();
    } catch (e) {
      reject(e);
      return;
    }

    const usuario = crearUsuario(email);
    usuario.confirmRegistration(String(codigo).trim(), true, (err) => {
      if (err) {
        reject(traducirError(err));
        return;
      }
      resolve(true);
    });
  });
}

/** Reenvia el codigo de confirmacion (si se perdio el correo). */
export function reenviarCodigoConfirmacion(email) {
  return new Promise((resolve, reject) => {
    try {
      exigirConfiguracion();
    } catch (e) {
      reject(e);
      return;
    }

    const usuario = crearUsuario(email);
    usuario.resendConfirmationCode((err, resultado) => {
      if (err) {
        reject(traducirError(err));
        return;
      }
      resolve(resultado?.CodeDeliveryDetails?.Destination || String(email).trim());
    });
  });
}

/** Login con SRP. Guarda la sesion y devuelve email, nombre y rol. */
export function iniciarSesionCliente(email, password) {
  return new Promise((resolve, reject) => {
    try {
      exigirConfiguracion();
    } catch (e) {
      reject(e);
      return;
    }

    const usuario = crearUsuario(email);
    const detalles = new AuthenticationDetails({
      Username: String(email).trim(),
      Password: password
    });

    usuario.authenticateUser(detalles, {
      onSuccess: (sesion) => {
        resolve(guardarDatosSesion(usuario, sesion));
      },
      onFailure: (err) => reject(traducirError(err)),
      newPasswordRequired: () =>
        reject(new Error('Cognito exige definir una contrasena nueva. Usa "Recuperar contrasena".')),
      mfaRequired: () =>
        reject(new Error('La cuenta tiene MFA habilitado y este flujo no lo soporta.'))
    });
  });
}

/**
 * Devuelve una sesion valida. Si el access_token expiro, lo renueva con el
 * refresh_token (T02) sin volver a pedir la contrasena.
 *
 * Se usa `usuario.getSession()`, NO `usuario.getSignInUserSession()`: el SDK
 * guarda los tokens en sessionStorage y `getCurrentUser()` devuelve un
 * `CognitoUser` NUEVO en cada llamada, sin sesion en memoria. `getSession()`
 * es el unico metodo que reconstruye la sesion desde el storage y, si el
 * access token vencio, dispara el refresh solo. Con `getSignInUserSession()`
 * la sesion parecia vencida en cada navegacion.
 *
 * Las llamadas concurrentes comparten la misma promesa: el panel del cliente
 * pide perfil, favoritos, alertas y novedades a la vez, y sin esto cada una
 * lanzaria su propio refresh contra Cognito.
 */
let promesaSesion = null;

function asegurarSesion(usuario) {
  if (promesaSesion) return promesaSesion;

  promesaSesion = new Promise((resolve, reject) => {
    usuario.getSession((err, sesion) => {
      if (err || !sesion || !sesion.isValid()) {
        // El refresh token fue revocado o vencio: se borran los tokens del
        // SDK para que no se repita el POST que responde 400 en cada intento.
        try {
          usuario.signOut(); // sin callback: solo limpia el storage, sin red
        } catch (_) {
          // El storage ya estaba limpio.
        }
        limpiarDatosSesion();
        limpiarTokensSdk();
        reject(errorSesion());
        return;
      }
      guardarDatosSesion(usuario, sesion);
      resolve(sesion);
    });
  }).finally(() => {
    promesaSesion = null;
  });

  return promesaSesion;
}

/** Renueva explicitamente el token (boton "Renovar token" de Mi cuenta). */
export async function renovarTokenCliente() {
  exigirConfiguracion();
  const usuario = userPool.getCurrentUser();
  if (!usuario) {
    limpiarDatosSesion();
    limpiarTokensSdk();
    throw errorSesion('No hay una sesion de cliente activa.');
  }

  // No alcanza con asegurarSesion(): si el access token todavia es valido,
  // getSession() devuelve el mismo token. El boton debe forzar el refresh.
  const nueva = await new Promise((resolve, reject) => {
    asegurarSesion(usuario).then((sesion) => {
      usuario.refreshSession(sesion.getRefreshToken(), (err, renovada) => {
        if (err || !renovada) {
          reject(traducirError(err || {}));
          return;
        }
        guardarDatosSesion(usuario, renovada);
        resolve(renovada);
      });
    }, reject);
  });

  return {
    accessToken: nueva.getAccessToken().getJwtToken(),
    expira: nueva.getAccessToken().getExpiration()
  };
}

/**
 * ACCESS TOKEN vigente para llamar a /api/cliente/** como Bearer (T03).
 * Renueva automaticamente si esta vencido.
 */
export async function obtenerAccessTokenCliente() {
  exigirConfiguracion();
  const usuario = userPool.getCurrentUser();
  if (!usuario) {
    limpiarDatosSesion();
    limpiarTokensSdk();
    throw errorSesion('No hay una sesion de cliente activa.');
  }
  const sesion = await asegurarSesion(usuario);
  return sesion.getAccessToken().getJwtToken();
}

/** Inicia el flujo de forgot-password: Cognito envia un codigo al email. */
export function solicitarRecuperacionPassword(email) {
  return new Promise((resolve, reject) => {
    try {
      exigirConfiguracion();
    } catch (e) {
      reject(e);
      return;
    }

    const usuario = crearUsuario(email);
    usuario.forgotPassword({
      onSuccess: (resultado) => {
        resolve({
          destino: resultado?.CodeDeliveryDetails?.Destination || String(email).trim(),
          medio: resultado?.CodeDeliveryDetails?.DeliveryMedium || 'EMAIL'
        });
      },
      onFailure: (err) => reject(traducirError(err))
    });
  });
}

/** Confirma el codigo y define la nueva contrasena. */
export function confirmarNuevaPassword(email, codigo, nuevaPassword) {
  return new Promise((resolve, reject) => {
    try {
      exigirConfiguracion();
    } catch (e) {
      reject(e);
      return;
    }

    const usuario = crearUsuario(email);
    usuario.confirmPassword(String(codigo).trim(), nuevaPassword, {
      onSuccess: () => resolve(true),
      onFailure: (err) => reject(traducirError(err))
    });
  });
}

/**
 * Cierra la sesion del cliente y, ademas de borrar los tokens locales,
 * REVOCA el refresh token en Cognito.
 *
 * `CognitoUser.signOut(callback)` reconstruye la sesion desde el storage,
 * llama al endpoint Revoke con el refresh token (por eso la plantilla tiene
 * `EnableTokenRevocation: true`) y recien despues limpia los tokens. Sin la
 * revocacion, un refresh token robado seguiria sirviendo 30 dias aunque el
 * usuario hubiera cerrado sesion.
 *
 * Devuelve una promesa que se resuelve siempre (exito, error o timeout): el
 * cierre de sesion local nunca debe quedar bloqueado por la red.
 */
export function cerrarSesionCliente() {
  limpiarDatosSesion();

  let usuario = null;
  try {
    usuario = userPool?.getCurrentUser();
  } catch (_) {
    usuario = null;
  }
  if (!usuario) {
    limpiarTokensSdk();
    return Promise.resolve();
  }

  return new Promise((resolve) => {
    let resuelto = false;
    const terminar = () => {
      if (resuelto) return;
      resuelto = true;
      // signOut() sin callback limpia los tokens del storage aunque el
      // Revoke haya fallado (token vencido, sin red, etc).
      try {
        usuario.signOut();
      } catch (_) {
        // El storage ya estaba limpio.
      }
      limpiarTokensSdk();
      resolve();
    };

    try {
      usuario.signOut(() => terminar());
    } catch (_) {
      terminar();
      return;
    }
    // Tope de espera: si Cognito no responde, se cierra igual la sesion local.
    setTimeout(terminar, 2500);
  });
}

/** Payload del ultimo access_token, para diagnostico en la consola. */
export function leerTokenActual() {
  const usuario = userPool?.getCurrentUser();
  const sesion = usuario?.getSignInUserSession();
  if (!sesion) return {};
  return decodificarJwt(sesion.getAccessToken().getJwtToken());
}
