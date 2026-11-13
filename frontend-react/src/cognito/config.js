import { CognitoUserPool } from 'amazon-cognito-identity-js';

// Configuracion del User Pool PUBLICO de clientes (T02 de la rubrica).
//
// Los ids los entrega el stack de CloudFormation (salidas ClienteUserPoolId y
// ClienteUserPoolClientId) y Vite los incorpora al bundle en tiempo de build.
// No hay secreto de cliente: es una SPA publica y el SDK usa SRP.
const REGION = import.meta.env.VITE_COGNITO_REGION || 'us-east-1';
const USER_POOL_ID = (import.meta.env.VITE_COGNITO_USER_POOL_ID || '').trim();
const CLIENT_ID = (import.meta.env.VITE_COGNITO_CLIENT_ID || '').trim();

export const COGNITO_REGION = REGION;
export const COGNITO_USER_POOL_ID = USER_POOL_ID;
export const COGNITO_CLIENT_ID = CLIENT_ID;

// La app sigue funcionando (sin login de clientes) aunque falten las
// variables: la pagina /cliente muestra un aviso claro en vez de romper el
// build o el resto del sitio.
export const COGNITO_CONFIGURADO = Boolean(
  USER_POOL_ID && CLIENT_ID && !USER_POOL_ID.includes('REEMPLAZAR') && !CLIENT_ID.includes('REEMPLAZAR')
);

export const userPool = COGNITO_CONFIGURADO
  ? new CognitoUserPool({
      UserPoolId: USER_POOL_ID,
      ClientId: CLIENT_ID
    })
  : null;

export const MENSAJE_SIN_CONFIGURAR =
  'El registro de clientes aun no esta configurado. Falta VITE_COGNITO_USER_POOL_ID / ' +
  'VITE_COGNITO_CLIENT_ID en el build del frontend (los entrega el stack de CloudFormation).';
