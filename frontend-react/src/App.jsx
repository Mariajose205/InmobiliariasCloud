import React from 'react'
import { BrowserRouter as Router, Routes, Route, useNavigate } from 'react-router-dom'
import { MsalProvider } from '@azure/msal-react'
import { msalInstance } from './msalConfig'
import Home from './pages/Home'
import Login from './pages/Login'
import Admin from './pages/Admin'
import Dashboard from './pages/Dashboard'
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

function App() {
  return (
    <MsalProvider instance={msalInstance}>
      <Router>
        <div className="App">
          <PostLoginRedirect />
          <Routes>
            <Route path="/" element={<Home />} />
            <Route path="/login" element={<Login />} />
            <Route path="/admin" element={<Admin />} />
            <Route path="/admin/dashboard" element={<Dashboard />} />
          </Routes>
        </div>
      </Router>
    </MsalProvider>
  )
}

export default App
