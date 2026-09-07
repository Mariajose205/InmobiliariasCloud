import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.jsx'
import './index.css'
import { msalInstance, obtenerRolCuenta } from './msalConfig'

async function bootstrap() {
  try {
    // Procesa el flujo de redireccion (si Azure devolvio el codigo en la URL,
    // p. ej. #code=...). Sin esto el login nunca se completa y el codigo se
    // queda pegado en la barra de direcciones.
    await msalInstance.initialize();
    const response = await msalInstance.handleRedirectPromise();

    // Restaura la cuenta activa para que la UI la detecte al cargar.
    const accounts = msalInstance.getAllAccounts();
    if (accounts.length > 0) {
      msalInstance.setActiveAccount(accounts[0]);
    }

    // Si venimos de un login reciente por redireccion, guardamos a donde
    // redirigir una vez montada la app (panel interno para admin y corredor).
    if (response?.account) {
      const rol = obtenerRolCuenta(response.account);
      sessionStorage.setItem('inmobiliaria_post_login', rol ? '/admin' : '/');
      guardarEmailSesion(response.account);
    }
  } catch (error) {
    console.error('Error al inicializar MSAL:', error);
  }

  ReactDOM.createRoot(document.getElementById('root')).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>,
  )
}

function guardarEmailSesion(account) {
  const email = account.username || account.idTokenClaims?.email;
  if (email) {
    sessionStorage.setItem('inmobiliaria_msal_email', email);
  }
}

bootstrap();
