import React, { useState, useEffect } from 'react';
import { Container, Button, Alert } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import { useMsal } from '@azure/msal-react';
import { loginRequest, obtenerRolCuenta } from '../msalConfig';

function Login() {
  const { instance, accounts, inProgress } = useMsal();
  const navigate = useNavigate();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleLogin = async () => {
    // Si ya hay una cuenta autenticada, no hace falta abrir el flujo de nuevo.
    if (accounts.length > 0) {
      navigate(obtenerRolCuenta(accounts[0]) ? '/admin' : '/');
      return;
    }

    if (inProgress === 'login') {
      setError('Ya hay un proceso de login en progreso. Por favor espera.');
      return;
    }

    setLoading(true);
    setError('');

    try {
      // Redireccion a Azure y de vuelta. handleRedirectPromise (main.jsx)
      // completa el login y redirige al panel admin o a home.
      await instance.loginRedirect(loginRequest);
    } catch (error) {
      console.error('Error de login:', error);
      setError('Error al iniciar sesión con Azure Entra ID');
      setLoading(false);
    }
  };

  // Redirección automática SOLO cuando no hay una interacción en curso.
  // Evita que al iniciar sesión vuelva a /login y se inicie sesión de nuevo.
  useEffect(() => {
    if (inProgress === 'none') {
      if (accounts.length > 0) {
        navigate(obtenerRolCuenta(accounts[0]) ? '/admin' : '/');
      }
    }
  }, [inProgress, accounts, navigate]);

  return (
    <div style={{
      fontFamily: "'Plus Jakarta Sans', sans-serif",
      background: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%)',
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      margin: 0
    }}>
      <Container className="p-4">
        <div style={{
          background: 'rgba(255, 255, 255, 0.95)',
          borderRadius: '20px',
          boxShadow: '0 20px 60px rgba(0, 0, 0, 0.3)',
          overflow: 'hidden',
          maxWidth: '500px',
          width: '100%',
          padding: '40px'
        }}>
          <div className="text-center mb-4">
            <i className="bi bi-house-door-fill" style={{ fontSize: '4rem', color: '#f08800' }}></i>
            <h3 className="mt-3 fw-bold">Inmobiliarias Duoc</h3>
            <p className="mb-0 text-muted">Inicia sesión con tu cuenta de Microsoft</p>
          </div>

          {error && (
            <Alert variant="danger" className="mb-3" onClose={() => setError('')} dismissible>
              {error}
            </Alert>
          )}

          <Button 
            onClick={handleLogin}
            disabled={loading || inProgress === 'login'}
            className="w-100 mb-3"
            style={{
              background: loading || inProgress === 'login' ? '#6c757d' : '#0078d4',
              border: 'none',
              borderRadius: '10px',
              padding: '12px 30px',
              fontWeight: '600',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '10px'
            }}
          >
            {loading || inProgress === 'login' ? (
              <>
                <span className="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
                Cargando...
              </>
            ) : (
              <>
                <svg width="20" height="20" viewBox="0 0 23 23" fill="none">
                  <path d="M11.5 0L0 11.5L11.5 23L23 11.5L11.5 0Z" fill="#F25022"/>
                  <path d="M11.5 0L0 11.5L11.5 23L23 11.5L11.5 0Z" fill="#00A4EF" transform="translate(0, 0)"/>
                  <path d="M11.5 0L0 11.5L11.5 23L23 11.5L11.5 0Z" fill="#7FBA00" transform="translate(0, 0)"/>
                  <path d="M11.5 0L0 11.5L11.5 23L23 11.5L11.5 0Z" fill="#FFB900" transform="translate(0, 0)"/>
                </svg>
                Iniciar sesión con Microsoft
              </>
            )}
          </Button>

          <div className="text-center mt-3">
            <Button variant="link" onClick={() => navigate('/')} className="text-decoration-none text-muted">
              <i className="bi bi-person me-1"></i>Ingresar como invitado
            </Button>
          </div>
        </div>
      </Container>
    </div>
  );
}

export default Login;
