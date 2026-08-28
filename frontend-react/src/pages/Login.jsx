import React, { useState, useEffect } from 'react';
import { Container, Row, Col, Form, Button, Alert, Tabs, Tab } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';

const USERS_KEY = 'inmobiliaria_users';
const SESSION_KEY = 'inmobiliaria_session';

function Login() {
  const [activeTab, setActiveTab] = useState('login');
  const [loginEmail, setLoginEmail] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [registerName, setRegisterName] = useState('');
  const [registerEmail, setRegisterEmail] = useState('');
  const [registerPassword, setRegisterPassword] = useState('');
  const [registerConfirmPassword, setRegisterConfirmPassword] = useState('');
  const [loginAlert, setLoginAlert] = useState({ show: false, message: '', type: '' });
  const [registerAlert, setRegisterAlert] = useState({ show: false, message: '', type: '' });
  
  const navigate = useNavigate();

  const getUsers = () => {
    const users = localStorage.getItem(USERS_KEY);
    return users ? JSON.parse(users) : [];
  };

  const saveUsers = (users) => {
    localStorage.setItem(USERS_KEY, JSON.stringify(users));
  };

  const showAlert = (type, message, isLogin) => {
    if (isLogin) {
      setLoginAlert({ show: true, message, type });
    } else {
      setRegisterAlert({ show: true, message, type });
    }
    setTimeout(() => {
      if (isLogin) {
        setLoginAlert({ show: false, message: '', type: '' });
      } else {
        setRegisterAlert({ show: false, message: '', type: '' });
      }
    }, 5000);
  };

  const handleLogin = (e) => {
    e.preventDefault();
    const users = getUsers();
    const user = users.find(u => u.email === loginEmail && u.password === loginPassword);
    
    if (user) {
      const session = {
        email: user.email,
        name: user.name,
        loggedIn: true
      };
      localStorage.setItem(SESSION_KEY, JSON.stringify(session));
      showAlert('success', '¡Sesión iniciada correctamente! Redirigiendo...', true);
      
      setTimeout(() => {
        navigate('/');
      }, 1500);
    } else {
      showAlert('danger', 'Correo o contraseña incorrectos', true);
    }
  };

  const handleRegister = (e) => {
    e.preventDefault();
    
    if (registerPassword !== registerConfirmPassword) {
      showAlert('danger', 'Las contraseñas no coinciden', false);
      return;
    }
    
    if (registerPassword.length < 6) {
      showAlert('warning', 'La contraseña debe tener al menos 6 caracteres', false);
      return;
    }

    const users = getUsers();
    
    if (users.find(u => u.email === registerEmail)) {
      showAlert('warning', 'Este correo ya está registrado', false);
      return;
    }
    
    const newUser = {
      name: registerName,
      email: registerEmail,
      password: registerPassword,
      createdAt: new Date().toISOString()
    };
    
    users.push(newUser);
    saveUsers(users);
    
    showAlert('success', '¡Cuenta creada exitosamente! Ahora puedes iniciar sesión', false);
    
    setTimeout(() => {
      setActiveTab('login');
      setLoginEmail(registerEmail);
    }, 1500);
  };

  useEffect(() => {
    const session = localStorage.getItem(SESSION_KEY);
    if (session) {
      const parsedSession = JSON.parse(session);
      if (parsedSession.loggedIn) {
        navigate('/');
      }
    }
  }, [navigate]);

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
          maxWidth: '900px',
          width: '100%'
        }}>
          <Row className="g-0">
            <Col lg={5} className="d-none d-lg-flex flex-column" style={{
              background: 'linear-gradient(135deg, #f08800b0 0%, #000000 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'white',
              padding: '40px'
            }}>
              <div className="text-center">
                <i className="bi bi-house-door-fill" style={{ fontSize: '4rem' }}></i>
                <h3 className="mt-3 fw-bold">Inmobiliarias Duroc</h3>
                <p className="mb-0">Tu hogar ideal está aquí y en Duroc</p>
              </div>
            </Col>
            <Col lg={7} style={{ padding: '40px' }}>
              <Tabs activeKey={activeTab} onSelect={(k) => setActiveTab(k)} className="mb-4" style={{ border: 'none' }}>
                <Tab eventKey="login" title={<span><i className="bi bi-box-arrow-in-right me-2"></i>Iniciar Sesión</span>}>
                  {loginAlert.show && (
                    <Alert variant={loginAlert.type} className="mb-3" onClose={() => setLoginAlert({ show: false, message: '', type: '' })} dismissible>
                      {loginAlert.message}
                    </Alert>
                  )}
                  <Form onSubmit={handleLogin}>
                    <Form.Group className="mb-3">
                      <Form.Label className="fw-semibold">Correo Electrónico</Form.Label>
                      <Form.Control
                        type="email"
                        value={loginEmail}
                        onChange={(e) => setLoginEmail(e.target.value)}
                        required
                        placeholder="tu@email.com"
                        style={{ borderRadius: '10px', padding: '12px 15px', border: '2px solid #e0e0e0' }}
                      />
                    </Form.Group>
                    <Form.Group className="mb-4">
                      <Form.Label className="fw-semibold">Contraseña</Form.Label>
                      <Form.Control
                        type="password"
                        value={loginPassword}
                        onChange={(e) => setLoginPassword(e.target.value)}
                        required
                        placeholder="••••••••"
                        style={{ borderRadius: '10px', padding: '12px 15px', border: '2px solid #e0e0e0' }}
                      />
                    </Form.Group>
                    <Button type="submit" className="w-100" style={{
                      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                      border: 'none',
                      borderRadius: '10px',
                      padding: '12px 30px',
                      fontWeight: '600'
                    }}>
                      <i className="bi bi-box-arrow-in-right me-2"></i>Iniciar Sesión
                    </Button>
                  </Form>
                  <div className="text-center mt-3">
                    <Button variant="link" onClick={() => navigate('/')} className="text-decoration-none text-muted">
                      <i className="bi bi-arrow-left me-1"></i>Volver al inicio
                    </Button>
                  </div>
                </Tab>
                <Tab eventKey="register" title={<span><i className="bi bi-person-plus me-2"></i>Registrarse</span>}>
                  {registerAlert.show && (
                    <Alert variant={registerAlert.type} className="mb-3" onClose={() => setRegisterAlert({ show: false, message: '', type: '' })} dismissible>
                      {registerAlert.message}
                    </Alert>
                  )}
                  <Form onSubmit={handleRegister}>
                    <Form.Group className="mb-3">
                      <Form.Label className="fw-semibold">Nombre Completo</Form.Label>
                      <Form.Control
                        type="text"
                        value={registerName}
                        onChange={(e) => setRegisterName(e.target.value)}
                        required
                        placeholder="Tu nombre"
                        style={{ borderRadius: '10px', padding: '12px 15px', border: '2px solid #e0e0e0' }}
                      />
                    </Form.Group>
                    <Form.Group className="mb-3">
                      <Form.Label className="fw-semibold">Correo Electrónico</Form.Label>
                      <Form.Control
                        type="email"
                        value={registerEmail}
                        onChange={(e) => setRegisterEmail(e.target.value)}
                        required
                        placeholder="tu@email.com"
                        style={{ borderRadius: '10px', padding: '12px 15px', border: '2px solid #e0e0e0' }}
                      />
                    </Form.Group>
                    <Form.Group className="mb-3">
                      <Form.Label className="fw-semibold">Contraseña</Form.Label>
                      <Form.Control
                        type="password"
                        value={registerPassword}
                        onChange={(e) => setRegisterPassword(e.target.value)}
                        required
                        placeholder="Mínimo 6 caracteres"
                        minLength={6}
                        style={{ borderRadius: '10px', padding: '12px 15px', border: '2px solid #e0e0e0' }}
                      />
                    </Form.Group>
                    <Form.Group className="mb-4">
                      <Form.Label className="fw-semibold">Confirmar Contraseña</Form.Label>
                      <Form.Control
                        type="password"
                        value={registerConfirmPassword}
                        onChange={(e) => setRegisterConfirmPassword(e.target.value)}
                        required
                        placeholder="••••••••"
                        style={{ borderRadius: '10px', padding: '12px 15px', border: '2px solid #e0e0e0' }}
                      />
                    </Form.Group>
                    <Button type="submit" className="w-100" style={{
                      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                      border: 'none',
                      borderRadius: '10px',
                      padding: '12px 30px',
                      fontWeight: '600'
                    }}>
                      <i className="bi bi-person-plus me-2"></i>Crear Cuenta
                    </Button>
                  </Form>
                </Tab>
              </Tabs>
            </Col>
          </Row>
        </div>
      </Container>
    </div>
  );
}

export default Login;
