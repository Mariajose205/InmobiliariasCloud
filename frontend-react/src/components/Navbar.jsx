import React from 'react';
import { Navbar as BootstrapNavbar, Container, Nav, Button } from 'react-bootstrap';
import { Link } from 'react-router-dom';
import { useMsal } from '@azure/msal-react';
import { obtenerRolCuenta, haySesion, limpiarSesion } from '../msalConfig';

const Navbar = () => {
  const { instance, accounts } = useMsal();

  // Se considera que hay sesion si MSAL tiene una cuenta activa O si se
  // persistio manualmente el inicio de sesion (robusto frente a la cache).
  const isLoggedIn = accounts.length > 0 || haySesion();

  const handleLogout = async () => {
    limpiarSesion();
    localStorage.removeItem('inmobiliaria_session');
    try {
      await instance.logoutPopup({ postLogoutRedirectUri: '/login' });
    } catch (e) {
      console.error('Error al cerrar sesión:', e);
    }
    window.location.href = '/login';
  };

  const isAdmin = obtenerRolCuenta(accounts[0]) === 'admin';
  const isCorredor = obtenerRolCuenta(accounts[0]) === 'corredor';
  const puedeUsarPanel = isAdmin || isCorredor;

  return (
    <BootstrapNavbar expand="lg" className="navbar-dark">
      <Container>
        <BootstrapNavbar.Brand as={Link} to="#" className="border border-white text-white px-3 py-2 rounded d-inline-flex align-items-center" style={{ fontWeight: 600 }}>
          <i className="bi bi-house-door-fill me-2 text-white"></i>
          Duoc Propiedades
        </BootstrapNavbar.Brand>
        <BootstrapNavbar.Toggle aria-controls="navbarNav" />
        <BootstrapNavbar.Collapse id="navbarNav">
          <Nav className="ms-auto align-items-center">
            <Nav.Item>
              <Nav.Link as={Link} to="#" className="active">Inicio</Nav.Link>
            </Nav.Item>
            <Nav.Item>
              <Nav.Link href="#propiedades">Propiedades</Nav.Link>
            </Nav.Item>
            <Nav.Item>
              <Nav.Link href="#contacto">Contacto</Nav.Link>
            </Nav.Item>
            {puedeUsarPanel && (
              <Nav.Item>
                <Nav.Link as={Link} to="/admin">
                  <i className="bi bi-gear-fill me-1"></i>Admin
                </Nav.Link>
              </Nav.Item>
            )}
            {isAdmin && (
              <Nav.Item>
                <Nav.Link as={Link} to="/admin/dashboard">
                  <i className="bi bi-bar-chart-line-fill me-1"></i>Dashboard
                </Nav.Link>
              </Nav.Item>
            )}
            <Nav.Item id="authSection" className="ms-2">
              {isLoggedIn ? (
                <>
                  <Button 
                    variant="danger" 
                    size="sm" 
                    onClick={handleLogout}
                    className="ms-2"
                  >
                    <i className="bi bi-box-arrow-right me-1"></i>Cerrar Sesión
                  </Button>
                </>
              ) : (
                <Nav.Item as={Link} to="/login" className="text-decoration-none ms-2">
                  <Button variant="primary" size="sm" style={{ borderRadius: '10px', padding: '8px 20px', fontWeight: 600 }}>
                    <i className="bi bi-person-circle me-1"></i>Iniciar Sesión
                  </Button>
                </Nav.Item>
              )}
            </Nav.Item>
          </Nav>
        </BootstrapNavbar.Collapse>
      </Container>
    </BootstrapNavbar>
  );
};

export default Navbar;
