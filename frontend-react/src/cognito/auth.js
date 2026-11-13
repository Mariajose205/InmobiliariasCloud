import {
  AuthenticationDetails,
  CognitoUser,
  CognitoUserAttribute
} from 'amazon-cognito-identity-js';
import {
  COGNITO_CONFIGURADO,
  MENSAJE_SIN_CONFIGURAR,
  userPool
} from './config';

// Sesion del cliente publico (Amazon Cognito).
//
// Flujo implementado (T02 de la rubrica):
//  - registro con email + contrasena y confirmacion por codigo de email,
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

/** Devuelve la sesion guardada (solo para pintar la UI), o null. */
export function obtenerSesionCliente() {
  if (!COGNITO_CONFIGURADO || !userPool) return null;
  const email = sessionStorage.getItem(KEY_EMAIL);
  if (!email) return null;
  if (!userPool.getCurrentUser()) {
    limpiarDatosSesion();
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
  const sesion = usuario?.getSignInUserSession();
  if (!sesion) return null;
  const claims = sesion.getAccessToken().decodePayload();
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

/** Registro de un cliente nuevo: email, contrasena y nombre. */
export function registrarCliente(email, password, nombre) {
  return new Promise((resolve, reject) => {
    try {
      exigirConfiguracion();
    } catch (e) {
      reject(e);
      return;
    }

    const atributos = nombre
      ? [new CognitoUserAttribute({ Name: 'name', Value: nombre })]
      : [];

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

    const usuario = new CognitoUser({ Username: String(email).trim(), Pool: userPool });
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

    const usuario = new CognitoUser({ Username: String(email).trim(), Pool: userPool });
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

    const usuario = new CognitoUser({ Username: String(email).trim(), Pool: userPool });
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
 */
function asegurarSesion(usuario) {
  return new Promise((resolve, reject) => {
    const sesionActual = usuario.getSignInUserSession();
    if (sesionActual && sesionActual.isValid()) {
      resolve(sesionActual);
      return;
    }

    const refreshToken = sesionActual?.getRefreshToken();
    if (!refreshToken) {
      limpiarDatosSesion();
      reject(new Error('La sesion expiro. Vuelve a iniciar sesion.'));
      return;
    }

    usuario.refreshSession(refreshToken, (err, sesionRenovada) => {
      if (err || !sesionRenovada) {
        limpiarDatosSesion();
        reject(traducirError(err || {}));
        return;
      }
      guardarDatosSesion(usuario, sesionRenovada);
      resolve(sesionRenovada);
    });
  });
}

/** Renueva explicitamente el token (boton "Renovar token" de Mi cuenta). */
export async function renovarTokenCliente() {
  exigirConfiguracion();
  const usuario = userPool.getCurrentUser();
  if (!usuario) throw new Error('No hay una sesion de cliente activa.');
  const sesion = await asegurarSesion(usuario);
  return {
    accessToken: sesion.getAccessToken().getJwtToken(),
    expira: sesion.getAccessToken().getExpiration()
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
    throw new Error('No hay una sesion de cliente activa.');
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

    const usuario = new CognitoUser({ Username: String(email).trim(), Pool: userPool });
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

    const usuario = new CognitoUser({ Username: String(email).trim(), Pool: userPool });
    usuario.confirmPassword(String(codigo).trim(), nuevaPassword, {
      onSuccess: () => resolve(true),
      onFailure: (err) => reject(traducirError(err))
    });
  });
}

/** Cierra la sesion de Cognito y borra los datos locales. */
export function cerrarSesionCliente() {
  try {
    const usuario = userPool?.getCurrentUser();
    if (usuario) usuario.signOut();
  } catch (_) {
    // Si el almacenamiento ya estaba limpio, no hay nada que cerrar.
  }
  limpiarDatosSesion();
}

/** Payload del ultimo access_token, para diagnostico en la consola. */
export function leerTokenActual() {
  const usuario = userPool?.getCurrentUser();
  const sesion = usuario?.getSignInUserSession();
  if (!sesion) return {};
  return decodificarJwt(sesion.getAccessToken().getJwtToken());
}
