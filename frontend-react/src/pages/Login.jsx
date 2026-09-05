import React, { useState, useEffect } from 'react';
import { Container, Button, Alert } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import { useMsal } from '@azure/msal-react';
import { loginRequest, esCuentaAdmin } from '../msalConfig';

function Login() {
  const { instance, accounts, inProgress } = useMsal();
  const navigate = useNavigate();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleLogin = async () => {
    // Si ya hay una cuenta autenticada, no hace falta abrir el flujo de nuevo.
    if (accounts.length > 0) {
      navigate(esCuentaAdmin(accounts[0]) ? '/admin' : '/');
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

  const handleGuestLogin = () => {
    navigate('/');
  };

  // Redirección automática SOLO cuando no hay una interacción en curso.
  // Evita que al iniciar sesión vuelva a /login y se inicie sesión de nuevo.
  useEffect(() => {
    if (inProgress === 'none') {
      if (accounts.length > 0) {
        const esAdmin = esCuentaAdmin(accounts[0]);
        navigate(esAdmin ? '/admin' : '/');
      }
    }
  }, [inProgress, accounts, navigate]);

  return (
    <div style={{
      fontFamily: "'Plus Jakarta Sans', sans-serif",
      background: 'linear-gradient(135deg, #141b22 0%, #02131f 50%, #1a1a2e 100%)',
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      margin: 0,
      position: 'relative',
      overflow: 'hidden'
    }}>
      <div style={{
        position: 'absolute',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        background: 'radial-gradient(circle at 20% 50%, rgba(160, 82, 61, 0.1) 0%, transparent 50%), radial-gradient(circle at 80% 50%, rgba(107, 180, 185, 0.1) 0%, transparent 50%)',
        pointerEvents: 'none'
      }}></div>
      
      <Container className="p-4" style={{ position: 'relative', zIndex: 1 }}>
        <div style={{
          background: 'rgba(255, 255, 255, 0.98)',
          borderRadius: '24px',
          boxShadow: '0 25px 80px rgba(0, 0, 0, 0.35), 0 0 0 1px rgba(255, 255, 255, 0.1)',
          overflow: 'hidden',
          maxWidth: '480px',
          width: '100%',
          padding: '48px 40px',
          backdropFilter: 'blur(10px)'
        }}>
          <div className="text-center mb-5">
            <div style={{
              width: '80px',
              height: '80px',
              background: 'linear-gradient(135deg, #a0523d 0%, #6bb4b9 100%)',
              borderRadius: '50%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 20px',
              boxShadow: '0 8px 24px rgba(160, 82, 61, 0.3)'
            }}>
              <i className="bi bi-house-door-fill" style={{ fontSize: '2.5rem', color: '#ffffff' }}></i>
            </div>
            <h2 className="fw-bold mb-2" style={{ color: '#141b22', fontSize: '1.8rem' }}>Inmobiliarias Duroc</h2>
            <p className="mb-0" style={{ color: '#718096', fontSize: '1rem' }}>Inicia sesión para acceder al panel</p>
          </div>

          {error && (
            <Alert variant="danger" className="mb-4" onClose={() => setError('')} dismissible style={{ borderRadius: '12px', border: 'none' }}>
              {error}
            </Alert>
          )}

          <div className="d-flex flex-column gap-3">
            <Button 
              onClick={handleLogin}
              disabled={loading || inProgress === 'login'}
              className="w-100"
              style={{
                background: loading || inProgress === 'login' ? '#6c757d' : '#0078d4',
                border: 'none',
                borderRadius: '12px',
                padding: '14px 24px',
                fontWeight: '600',
                fontSize: '1rem',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '12px',
                transition: 'all 0.3s ease',
                boxShadow: loading || inProgress === 'login' ? 'none' : '0 4px 14px rgba(0, 120, 212, 0.3)'
              }}
            >
              {loading || inProgress === 'login' ? (
                <>
                  <span className="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
                  Cargando...
                </>
              ) : (
                <>
                  <svg width="22" height="22" viewBox="0 0 23 23" fill="none">
                    <path d="M11.5 0L0 11.5L11.5 23L23 11.5L11.5 0Z" fill="#F25022"/>
                    <path d="M11.5 0L0 11.5L11.5 23L23 11.5L11.5 0Z" fill="#00A4EF" transform="translate(0, 0)"/>
                    <path d="M11.5 0L0 11.5L11.5 23L23 11.5L11.5 0Z" fill="#7FBA00" transform="translate(0, 0)"/>
                    <path d="M11.5 0L0 11.5L11.5 23L23 11.5L11.5 0Z" fill="#FFB900" transform="translate(0, 0)"/>
                  </svg>
                  Iniciar sesión con Microsoft
                </>
              )}
            </Button>

            <div className="text-center" style={{ position: 'relative', margin: '8px 0' }}>
              <div style={{
                position: 'absolute',
                left: 0,
                right: 0,
                top: '50%',
                height: '1px',
                background: 'linear-gradient(to right, transparent, #e2e8f0, transparent)'
              }}></div>
              <span style={{
                background: '#ffffff',
                padding: '0 16px',
                color: '#a0aec0',
                fontSize: '0.85rem',
                position: 'relative',
                zIndex: 1
              }}>o continúa como</span>
            </div>

            <Button 
              onClick={handleGuestLogin}
              variant="outline-secondary"
              className="w-100"
              style={{
                borderRadius: '12px',
                padding: '14px 24px',
                fontWeight: '600',
                fontSize: '1rem',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '10px',
                border: '2px solid #e2e8f0',
                color: '#4a5568',
                transition: 'all 0.3s ease'
              }}
              onMouseEnter={(e) => {
                e.target.style.borderColor = '#a0523d';
                e.target.style.color = '#a0523d';
                e.target.style.background = 'rgba(160, 82, 61, 0.05)';
              }}
              onMouseLeave={(e) => {
                e.target.style.borderColor = '#e2e8f0';
                e.target.style.color = '#4a5568';
                e.target.style.background = 'transparent';
              }}
            >
              <i className="bi bi-person" style={{ fontSize: '1.2rem' }}></i>
              Ingresar como invitado
            </Button>
          </div>
        </div>
      </Container>
    </div>
  );
}

export default Login;
