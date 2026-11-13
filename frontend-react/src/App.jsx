import React from 'react'
import { BrowserRouter as Router, Routes, Route, useNavigate, useLocation } from 'react-router-dom'
import { MsalProvider } from '@azure/msal-react'
import { msalInstance } from './msalConfig'
import Home from './pages/Home'
import Login from './pages/Login'
import Admin from './pages/Admin'
import Dashboard from './pages/Dashboard'
import Wishlist from './pages/Wishlist'
import Cliente from './pages/Cliente'
import './App.css'

// Consume la ruta post-login guardada tras un inicio de sesion por
// redireccion, y navega a ella (admin si es el administrador, si no home).
function PostLoginRedirect() {
  const navigate = useNavigate();

  React.useEffect(() => {
    const target = sessionStorage.getItem('inmobiliaria_post_login');
    if (target) {
      sessionStorage.removeItem('inmobiliaria_post_login');
      navigate(target, { replace: true });
    }
  }, [navigate]);

  return null;
}

// Al cambiar de pagina (p.ej. de /wishlist a / con "Inicio"), la SPA
// mantiene la posicion de scroll. Sin esto, volver desde favoritos dejaba
// la pagina principal "a mitad de camino". Si la navegacion trae hash
// (#propiedades / #contacto), Home se encarga de bajar a esa seccion.
function ScrollToTop() {
  const { pathname, hash } = useLocation();

  React.useEffect(() => {
    if (!hash) {
      window.scrollTo(0, 0);
    }
  }, [pathname, hash]);

  return null;
}

function App() {
  return (
    <MsalProvider instance={msalInstance}>
      <Router>
        <div className="App">
          <PostLoginRedirect />
          <ScrollToTop />
          <Routes>
            <Route path="/" element={<Home />} />
            <Route path="/login" element={<Login />} />
            <Route path="/wishlist" element={<Wishlist />} />
            {/* Clientes publicos: identidad propia en Amazon Cognito (T02-T05). */}
            <Route path="/cliente" element={<Cliente />} />
            <Route path="/admin" element={<Admin />} />
            <Route path="/admin/dashboard" element={<Dashboard />} />
          </Routes>
        </div>
      </Router>
    </MsalProvider>
  )
}

export default App
