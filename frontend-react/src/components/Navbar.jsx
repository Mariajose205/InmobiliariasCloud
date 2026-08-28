import React from 'react';
import { Navbar as BootstrapNavbar, Container, Nav, NavDropdown } from 'react-bootstrap';
import { Link } from 'react-router-dom';

const Navbar = ({ user, onLogout }) => {
  return (
    <BootstrapNavbar expand="lg" className="navbar-dark">
      <Container>
        <BootstrapNavbar.Brand as={Link} to="#" className="border border-white text-white px-3 py-2 rounded d-inline-flex align-items-center" style={{ fontWeight: 600 }}>
          <i className="bi bi-house-door-fill me-2 text-white"></i>
          Duoc Propiedades
        </BootstrapNavbar.Brand>
        <BootstrapNavbar.Toggle aria-controls="navbarNav" />
        <BootstrapNavbar.Collapse id="navbarNav">
          <Nav className="ms-auto">
            <Nav.Item>
              <Nav.Link as={Link} to="#" className="active">Inicio</Nav.Link>
            </Nav.Item>
            <Nav.Item>
              <Nav.Link href="#propiedades">Propiedades</Nav.Link>
            </Nav.Item>
            <Nav.Item>
              <Nav.Link href="#contacto">Contacto</Nav.Link>
            </Nav.Item>
            <Nav.Item id="authSection">
              {user ? (
                <NavDropdown title={<span><i className="bi bi-person-check-fill me-1"></i>{user.name}</span>} id="user-dropdown">
                  <NavDropdown.Item as={Link} to="/admin">
                    <i className="bi bi-gear me-2"></i>Panel Admin
                  </NavDropdown.Item>
                  <NavDropdown.Item onClick={onLogout}>
                    <i className="bi bi-box-arrow-right me-2"></i>Cerrar Sesión
                  </NavDropdown.Item>
                </NavDropdown>
              ) : (
                <Nav.Link as={Link} to="/login">
                  <i className="bi bi-person-circle me-1"></i>Iniciar Sesión
                </Nav.Link>
              )}
            </Nav.Item>
          </Nav>
        </BootstrapNavbar.Collapse>
      </Container>
    </BootstrapNavbar>
  );
};

export default Navbar;
