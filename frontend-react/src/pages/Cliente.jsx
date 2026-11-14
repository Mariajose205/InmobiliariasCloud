import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  ButtonGroup,
  Card,
  Col,
  Container,
  Form,
  Row,
  Spinner
} from 'react-bootstrap';
import { Link } from 'react-router-dom';
import Navbar from '../components/Navbar';
import Footer from '../components/Footer';
import {
  cerrarSesionCliente,
  confirmarNuevaPassword,
  confirmarRegistroCliente,
  haySesionCliente,
  iniciarSesionCliente,
  obtenerClaimsTokenCliente,
  obtenerSesionCliente,
  reenviarCodigoConfirmacion,
  registrarCliente,
  renovarTokenCliente,
  solicitarRecuperacionPassword
} from '../cognito/auth';
import {
  comoLista,
  descartarNovedadCliente,
  eliminarFavoritoCliente,
  obtenerAlertasCliente,
  obtenerFavoritosCliente,
  obtenerNovedadesCliente,
  obtenerPerfilCliente,
  obtenerPropiedadesPublicas
} from '../api/apiCliente';
import '../App.css';

// Pagina del CLIENTE PUBLICO (Cognito, T02-T05).
//
// Sin sesion muestra las pantallas propias de registro, confirmacion de email,
// login y recuperacion de contrasena (no se usa el hosted UI). Con sesion
// muestra el perfil y los datos del cliente, siempre llamando a /api/cliente/**
// con el access_token como Bearer.
const VISTA_LOGIN = 'login';
const VISTA_REGISTRO = 'registro';
const VISTA_CONFIRMAR = 'confirmar';
const VISTA_RECUPERAR = 'recuperar';
const VISTA_NUEVA_PASSWORD = 'nuevaPassword';

function Cliente() {
  const [sesion, setSesion] = useState(() => obtenerSesionCliente());
  const [vista, setVista] = useState(VISTA_LOGIN);

  // Formularios
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [nombre, setNombre] = useState('');
  const [apellido, setApellido] = useState('');
  const [codigo, setCodigo] = useState('');
  const [nuevaPassword, setNuevaPassword] = useState('');
  const [confirmacionPassword, setConfirmacionPassword] = useState('');

  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');

  // Datos del cliente autenticado
  const [seccion, setSeccion] = useState('perfil');
  const [perfil, setPerfil] = useState(null);
  const [claims, setClaims] = useState(null);
  const [favoritos, setFavoritos] = useState([]);
  const [alertas, setAlertas] = useState([]);
  const [novedades, setNovedades] = useState([]);
  const [catalogo, setCatalogo] = useState([]);

  const limpiarMensajes = () => {
    setError('');
    setAviso('');
  };

  // La sesion de Cognito ya no sirve (refresh revocado o vencido): se vuelve
  // al formulario de ingreso en vez de dejar el panel con datos a medias.
  const expirarSesion = (mensaje) => {
    setSesion(null);
    setPerfil(null);
    setClaims(null);
    setFavoritos([]);
    setAlertas([]);
    setNovedades([]);
    setVista(VISTA_LOGIN);
    setError(mensaje || 'Tu sesión expiró. Vuelve a iniciar sesión.');
  };

  const formatearPrecio = (precio) => {
    const num = parseInt(String(precio ?? '').replace(/\./g, '')) || 0;
    return new Intl.NumberFormat('es-CL', {
      style: 'currency',
      currency: 'CLP',
      minimumFractionDigits: 0
    }).format(num);
  };

  const cargarDatosCliente = useCallback(async () => {
    setCargando(true);
    setError('');
    try {
      const [perfilData, favoritosData, alertasData, novedadesData, catalogoData] =
        await Promise.allSettled([
          obtenerPerfilCliente(),
          obtenerFavoritosCliente(),
          obtenerAlertasCliente(),
          obtenerNovedadesCliente(),
          obtenerPropiedadesPublicas()
        ]);

      if (perfilData.status === 'rejected') {
        throw perfilData.reason;
      }

      setPerfil(perfilData.value);
      setClaims(obtenerClaimsTokenCliente());

      setFavoritos(
        favoritosData.status === 'fulfilled' ? comoLista(favoritosData.value, 'favoritos') : []
      );
      setAlertas(
        alertasData.status === 'fulfilled'
          ? comoLista(alertasData.value, 'alertas')
              .map((item) => (typeof item === 'string' ? item : item?.comuna ?? item?.nombre ?? ''))
              .filter(Boolean)
          : []
      );
      setNovedades(
        novedadesData.status === 'fulfilled' ? comoLista(novedadesData.value, 'novedades') : []
      );
      setCatalogo(
        catalogoData.status === 'fulfilled' && Array.isArray(catalogoData.value)
          ? catalogoData.value
          : []
      );
    } catch (e) {
      if (e?.code === 'SESSION_EXPIRED') {
        // Mensaje fijo: el detalle tecnico ("No hay una sesion...") solo
        // confunde; lo importante es volver al formulario de ingreso.
        expirarSesion('Tu sesión expiró. Vuelve a iniciar sesión.');
      } else {
        setError(e.message || 'No se pudieron cargar los datos del cliente.');
      }
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    if (sesion) {
      cargarDatosCliente();
    }
  }, [sesion, cargarDatosCliente]);

  const entrar = (datos) => {
    setSesion(datos);
    setSeccion('perfil');
    setPassword('');
    setNuevaPassword('');
    setConfirmacionPassword('');
    setCodigo('');
    limpiarMensajes();
    setAviso(`Sesion iniciada como ${datos.email}.`);
  };

  const handleLogin = async (e) => {
    e.preventDefault();
    limpiarMensajes();
    setCargando(true);
    try {
      const datos = await iniciarSesionCliente(email, password);
      // Verifica que la sesion haya quedado realmente en sessionStorage: si
      // el navegador la bloquea o el SDK no pudo guardar los tokens, es mejor
      // avisarlo aca que entrar a un panel que va a fallar en cada llamada.
      if (!haySesionCliente()) {
        throw new Error(
          'No se pudo guardar la sesión en este navegador. Revisá que no esté bloqueando el almacenamiento del sitio.'
        );
      }
      entrar(datos);
    } catch (err) {
      setError(err.message);
    } finally {
      setCargando(false);
    }
  };

  const handleRegistro = async (e) => {
    e.preventDefault();
    limpiarMensajes();
    if (!nombre.trim() || !apellido.trim()) {
      setError('Ingresa tu nombre y tu apellido.');
      return;
    }
    if (password !== confirmacionPassword) {
      setError('Las contrasenas no coinciden.');
      return;
    }
    setCargando(true);
    try {
      const resultado = await registrarCliente(email, password, nombre, apellido);
      setVista(VISTA_CONFIRMAR);
      setAviso(
        resultado.confirmado
          ? 'Cuenta creada. Ya puedes iniciar sesion.'
          : `Cuenta creada. Enviamos un codigo de confirmacion a ${resultado.destino}.`
      );
    } catch (err) {
      setError(err.message);
    } finally {
      setCargando(false);
    }
  };

  const handleConfirmar = async (e) => {
    e.preventDefault();
    limpiarMensajes();
    setCargando(true);
    try {
      await confirmarRegistroCliente(email, codigo);
      setVista(VISTA_LOGIN);
      setCodigo('');
      setAviso('Cuenta confirmada. Ya puedes iniciar sesion con tu correo y contrasena.');
    } catch (err) {
      setError(err.message);
    } finally {
      setCargando(false);
    }
  };

  const handleReenviarCodigo = async () => {
    limpiarMensajes();
    setCargando(true);
    try {
      const destino = await reenviarCodigoConfirmacion(email);
      setAviso(`Enviamos un codigo nuevo a ${destino}. Revisa tambien la carpeta de spam.`);
    } catch (err) {
      setError(err.message);
    } finally {
      setCargando(false);
    }
  };

  const handleRecuperar = async (e) => {
    e.preventDefault();
    limpiarMensajes();
    setCargando(true);
    try {
      const resultado = await solicitarRecuperacionPassword(email);
      setVista(VISTA_NUEVA_PASSWORD);
      setAviso(`Enviamos un codigo de recuperacion a ${resultado.destino}.`);
    } catch (err) {
      setError(err.message);
    } finally {
      setCargando(false);
    }
  };

  const handleNuevaPassword = async (e) => {
    e.preventDefault();
    limpiarMensajes();
    if (nuevaPassword !== confirmacionPassword) {
      setError('Las contrasenas no coinciden.');
      return;
    }
    setCargando(true);
    try {
      await confirmarNuevaPassword(email, codigo, nuevaPassword);
      setVista(VISTA_LOGIN);
      setCodigo('');
      setNuevaPassword('');
      setConfirmacionPassword('');
      setAviso('Contrasena actualizada. Inicia sesion con tu nueva contrasena.');
    } catch (err) {
      setError(err.message);
    } finally {
      setCargando(false);
    }
  };

  const handleSalir = () => {
    cerrarSesionCliente();
    setSesion(null);
    setPerfil(null);
    setClaims(null);
    setFavoritos([]);
    setAlertas([]);
    setNovedades([]);
    setVista(VISTA_LOGIN);
    setSeccion('perfil');
    limpiarMensajes();
    setAviso('Sesion cerrada.');
  };

  const handleRenovar = async () => {
    limpiarMensajes();
    try {
      const { expira } = await renovarTokenCliente();
      setClaims(obtenerClaimsTokenCliente());
      setAviso(`Token renovado. Vence a las ${new Date(expira * 1000).toLocaleTimeString('es-CL')}.`);
    } catch (err) {
      if (err?.code === 'SESSION_EXPIRED') {
        expirarSesion('Tu sesión expiró. Vuelve a iniciar sesión.');
        return;
      }
      setError(err.message);
    }
  };

  const handleQuitarFavorito = async (propiedadId) => {
    limpiarMensajes();
    try {
      await eliminarFavoritoCliente(propiedadId);
      setFavoritos((lista) => lista.filter((f) => (f.propiedadId ?? f.id) !== propiedadId));
    } catch (err) {
      if (err?.code === 'SESSION_EXPIRED') {
        expirarSesion('Tu sesión expiró. Vuelve a iniciar sesión.');
        return;
      }
      setError(err.message);
    }
  };

  const handleDescartarNovedad = async (resumenId) => {
    limpiarMensajes();
    try {
      await descartarNovedadCliente(resumenId);
      setNovedades((lista) => lista.filter((n) => n.id !== resumenId));
    } catch (err) {
      if (err?.code === 'SESSION_EXPIRED') {
        expirarSesion('Tu sesión expiró. Vuelve a iniciar sesión.');
        return;
      }
      setError(err.message);
    }
  };

  const catalogoPorId = catalogo.reduce((acc, p) => {
    if (p && p.id !== null && p.id !== undefined) acc[String(p.id)] = p;
    return acc;
  }, {});

  // ---------------------------------------------------------------------
  // Sin sesion: registro / confirmacion / login / recuperacion
  // ---------------------------------------------------------------------
  if (!sesion) {
    return (
      <div className="App">
        <Navbar />
        <Container className="py-5 mt-5" style={{ maxWidth: '620px' }}>
          <Card className="shadow-sm border-0">
            <Card.Body className="p-4 p-md-5">
              <div className="text-center mb-4">
                <i className="bi bi-person-badge-fill" style={{ fontSize: '3rem', color: '#f08800' }}></i>
                <h3 className="fw-bold mt-2 mb-1">Mi cuenta</h3>
                <p className="text-muted mb-0">
                  Registro y acceso para clientes (Amazon Cognito). El personal interno sigue
                  usando <Link to="/login">Entra ID</Link>.
                </p>
              </div>

              {(error || aviso) && (
                <Alert
                  variant={error ? 'danger' : 'success'}
                  dismissible
                  onClose={() => (error ? setError('') : setAviso(''))}
                >
                  {error || aviso}
                </Alert>
              )}

              <div className="d-flex justify-content-center mb-4">
                <ButtonGroup size="sm">
                  <Button
                    variant={vista === VISTA_LOGIN ? 'primary' : 'outline-primary'}
                    onClick={() => { setVista(VISTA_LOGIN); limpiarMensajes(); }}
                  >
                    Ingresar
                  </Button>
                  <Button
                    variant={vista === VISTA_REGISTRO ? 'primary' : 'outline-primary'}
                    onClick={() => { setVista(VISTA_REGISTRO); limpiarMensajes(); }}
                  >
                    Crear cuenta
                  </Button>
                  <Button
                    variant={vista === VISTA_CONFIRMAR ? 'primary' : 'outline-primary'}
                    onClick={() => { setVista(VISTA_CONFIRMAR); limpiarMensajes(); }}
                  >
                    Confirmar
                  </Button>
                  <Button
                    variant={vista === VISTA_RECUPERAR || vista === VISTA_NUEVA_PASSWORD ? 'primary' : 'outline-primary'}
                    onClick={() => { setVista(VISTA_RECUPERAR); limpiarMensajes(); }}
                  >
                    Recuperar
                  </Button>
                </ButtonGroup>
              </div>

              {vista === VISTA_LOGIN && (
                <Form onSubmit={handleLogin}>
                  <Form.Group className="mb-3">
                    <Form.Label>Correo electronico</Form.Label>
                    <Form.Control
                      type="email"
                      required
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      placeholder="cliente@correo.cl"
                      autoComplete="username"
                    />
                  </Form.Group>
                  <Form.Group className="mb-3">
                    <Form.Label>Contrasena</Form.Label>
                    <Form.Control
                      type="password"
                      required
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      autoComplete="current-password"
                    />
                  </Form.Group>
                  <Button type="submit" className="w-100" disabled={cargando}>
                    {cargando ? <Spinner size="sm" animation="border" className="me-2" /> : null}
                    Iniciar sesion
                  </Button>
                </Form>
              )}

              {vista === VISTA_REGISTRO && (
                <Form onSubmit={handleRegistro}>
                  <Row className="g-3 mb-3">
                    <Col sm={6}>
                      <Form.Group>
                        <Form.Label>Nombre</Form.Label>
                        <Form.Control
                          required
                          value={nombre}
                          onChange={(e) => setNombre(e.target.value)}
                          placeholder="Maria"
                          autoComplete="given-name"
                        />
                      </Form.Group>
                    </Col>
                    <Col sm={6}>
                      <Form.Group>
                        <Form.Label>Apellido</Form.Label>
                        <Form.Control
                          required
                          value={apellido}
                          onChange={(e) => setApellido(e.target.value)}
                          placeholder="Perez"
                          autoComplete="family-name"
                        />
                      </Form.Group>
                    </Col>
                  </Row>
                  <Form.Group className="mb-3">
                    <Form.Label>Correo electronico</Form.Label>
                    <Form.Control
                      type="email"
                      required
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      autoComplete="email"
                    />
                  </Form.Group>
                  <Form.Group className="mb-3">
                    <Form.Label>Contrasena</Form.Label>
                    <Form.Control
                      type="password"
                      required
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      autoComplete="new-password"
                    />
                    <Form.Text className="text-muted">
                      Minimo 8 caracteres, con mayuscula, minuscula y numero.
                    </Form.Text>
                  </Form.Group>
                  <Form.Group className="mb-3">
                    <Form.Label>Repetir contrasena</Form.Label>
                    <Form.Control
                      type="password"
                      required
                      value={confirmacionPassword}
                      onChange={(e) => setConfirmacionPassword(e.target.value)}
                      autoComplete="new-password"
                    />
                  </Form.Group>
                  <Button type="submit" className="w-100" disabled={cargando}>
                    {cargando ? <Spinner size="sm" animation="border" className="me-2" /> : null}
                    Crear cuenta
                  </Button>
                </Form>
              )}

              {vista === VISTA_CONFIRMAR && (
                <Form onSubmit={handleConfirmar}>
                  <p className="text-muted">
                    Ingresa el codigo de 6 digitos que Cognito envio al correo con el que te
                    registraste.
                  </p>
                  <Form.Group className="mb-3">
                    <Form.Label>Correo electronico</Form.Label>
                    <Form.Control
                      type="email"
                      required
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      autoComplete="email"
                    />
                  </Form.Group>
                  <Form.Group className="mb-3">
                    <Form.Label>Codigo de confirmacion</Form.Label>
                    <Form.Control
                      required
                      value={codigo}
                      onChange={(e) => setCodigo(e.target.value)}
                      inputMode="numeric"
                      placeholder="123456"
                    />
                  </Form.Group>
                  <Button type="submit" className="w-100 mb-2" disabled={cargando}>
                    {cargando ? <Spinner size="sm" animation="border" className="me-2" /> : null}
                    Confirmar cuenta
                  </Button>
                  <Button
                    type="button"
                    variant="link"
                    className="w-100"
                    disabled={cargando}
                    onClick={handleReenviarCodigo}
                  >
                    Reenviar codigo
                  </Button>
                </Form>
              )}

              {vista === VISTA_RECUPERAR && (
                <Form onSubmit={handleRecuperar}>
                  <p className="text-muted">
                    Te enviaremos un codigo para definir una contrasena nueva.
                  </p>
                  <Form.Group className="mb-3">
                    <Form.Label>Correo electronico</Form.Label>
                    <Form.Control
                      type="email"
                      required
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      autoComplete="email"
                    />
                  </Form.Group>
                  <Button type="submit" className="w-100" disabled={cargando}>
                    {cargando ? <Spinner size="sm" animation="border" className="me-2" /> : null}
                    Enviar codigo
                  </Button>
                </Form>
              )}

              {vista === VISTA_NUEVA_PASSWORD && (
                <Form onSubmit={handleNuevaPassword}>
                  <p className="text-muted">
                    Ingresa el codigo enviado a <strong>{email}</strong> y tu nueva contrasena.
                  </p>
                  <Form.Group className="mb-3">
                    <Form.Label>Codigo</Form.Label>
                    <Form.Control
                      required
                      value={codigo}
                      onChange={(e) => setCodigo(e.target.value)}
                      inputMode="numeric"
                      placeholder="123456"
                    />
                  </Form.Group>
                  <Form.Group className="mb-3">
                    <Form.Label>Nueva contrasena</Form.Label>
                    <Form.Control
                      type="password"
                      required
                      value={nuevaPassword}
                      onChange={(e) => setNuevaPassword(e.target.value)}
                      autoComplete="new-password"
                    />
                  </Form.Group>
                  <Form.Group className="mb-3">
                    <Form.Label>Repetir nueva contrasena</Form.Label>
                    <Form.Control
                      type="password"
                      required
                      value={confirmacionPassword}
                      onChange={(e) => setConfirmacionPassword(e.target.value)}
                      autoComplete="new-password"
                    />
                  </Form.Group>
                  <Button type="submit" className="w-100" disabled={cargando}>
                    {cargando ? <Spinner size="sm" animation="border" className="me-2" /> : null}
                    Cambiar contrasena
                  </Button>
                </Form>
              )}
            </Card.Body>
          </Card>
        </Container>
        <Footer />
      </div>
    );
  }

  // ---------------------------------------------------------------------
  // Con sesion: perfil, favoritos, alertas y novedades del cliente
  // ---------------------------------------------------------------------
  const nombreCliente = perfil?.nombre || sesion.nombre || sesion.email;
  const rolCliente = perfil?.rol || sesion.rol || 'CLIENTE';

  return (
    <div className="App">
      <Navbar />
      <Container className="py-5 mt-5">
        <Card className="shadow-sm border-0 mb-4">
          <Card.Body className="p-4">
            <div className="d-flex flex-wrap justify-content-between align-items-center gap-3">
              <div>
                <h3 className="fw-bold mb-1">
                  <i className="bi bi-person-circle me-2 text-warning"></i>
                  {nombreCliente}
                </h3>
                <div className="text-muted">
                  {perfil?.email || sesion.email}
                  <Badge bg="warning" text="dark" className="ms-2">{rolCliente}</Badge>
                </div>
              </div>
              <div className="d-flex gap-2">
                <Button variant="outline-primary" size="sm" onClick={handleRenovar}>
                  <i className="bi bi-arrow-clockwise me-1"></i>Renovar token
                </Button>
                <Button variant="outline-danger" size="sm" onClick={handleSalir}>
                  <i className="bi bi-box-arrow-right me-1"></i>Cerrar sesion
                </Button>
              </div>
            </div>

            {claims && (
              <div className="mt-3 pt-3 border-top small text-muted">
                <div>
                  <strong>Token:</strong> tipo <code>{claims.token_use}</code>, rol en claim{' '}
                  <code>{claims.rol || '(sin rol)'}</code>, cliente <code>{claims.client_id}</code>
                </div>
                <div className="text-truncate">
                  <strong>Issuer:</strong> <code>{claims.iss}</code>
                </div>
              </div>
            )}
          </Card.Body>
        </Card>

        {(error || aviso) && (
          <Alert
            variant={error ? 'danger' : 'success'}
            dismissible
            onClose={() => (error ? setError('') : setAviso(''))}
          >
            {error || aviso}
          </Alert>
        )}

        <div className="d-flex justify-content-center mb-4">
          <ButtonGroup>
            <Button
              variant={seccion === 'perfil' ? 'primary' : 'outline-primary'}
              onClick={() => setSeccion('perfil')}
            >
              <i className="bi bi-person me-1"></i>Perfil
            </Button>
            <Button
              variant={seccion === 'favoritos' ? 'primary' : 'outline-primary'}
              onClick={() => setSeccion('favoritos')}
            >
              <i className="bi bi-heart me-1"></i>Favoritos ({favoritos.length})
            </Button>
            <Button
              variant={seccion === 'alertas' ? 'primary' : 'outline-primary'}
              onClick={() => setSeccion('alertas')}
            >
              <i className="bi bi-bell me-1"></i>Alertas ({alertas.length})
            </Button>
            <Button
              variant={seccion === 'novedades' ? 'primary' : 'outline-primary'}
              onClick={() => setSeccion('novedades')}
            >
              <i className="bi bi-newspaper me-1"></i>Novedades ({novedades.length})
            </Button>
          </ButtonGroup>
        </div>

        {cargando ? (
          <div className="text-center py-5">
            <Spinner animation="border" className="me-2" />
            <span>Cargando tus datos...</span>
          </div>
        ) : (
          <>
            {seccion === 'perfil' && (
              <Card className="shadow-sm border-0">
                <Card.Body className="p-4">
                  <h5 className="fw-bold mb-3">Datos de la cuenta</h5>
                  <Row className="g-3">
                    <Col md={6}>
                      <div className="text-muted small">Nombre completo</div>
                      <div className="fw-semibold">{perfil?.nombre || '(sin nombre)'}</div>
                    </Col>
                    <Col md={6}>
                      <div className="text-muted small">Correo</div>
                      <div className="fw-semibold">{perfil?.email || sesion.email}</div>
                    </Col>
                  </Row>
                </Card.Body>
              </Card>
            )}

            {seccion === 'favoritos' && (
              <>
                {favoritos.length === 0 ? (
                  <Card className="shadow-sm border-0">
                    <Card.Body className="text-center text-muted p-5">
                      <i className="bi bi-heart fs-3 d-block mb-2"></i>
                      Aun no tienes propiedades guardadas. Explora el{' '}
                      <Link to="/#propiedades">catalogo</Link> y guarda las que te interesen.
                    </Card.Body>
                  </Card>
                ) : (
                  <Row className="g-3">
                    {favoritos.map((fav) => {
                      const propiedadId = fav.propiedadId ?? fav.id;
                      const cat = catalogoPorId[String(propiedadId)] || {};
                      const imagen =
                        (Array.isArray(cat.imagenes) ? cat.imagenes[0] : null) || cat.imagen || null;
                      return (
                        <Col md={6} lg={4} key={propiedadId}>
                          <Card className="shadow-sm border-0 h-100">
                            {imagen && (
                              <Card.Img
                                variant="top"
                                src={imagen}
                                alt={fav.titulo || cat.titulo || 'Propiedad'}
                                style={{ height: '160px', objectFit: 'cover' }}
                              />
                            )}
                            <Card.Body>
                              <h6 className="fw-bold mb-1">{fav.titulo || cat.titulo || 'Sin titulo'}</h6>
                              <div className="text-muted small mb-2">
                                <i className="bi bi-geo-alt me-1"></i>
                                {fav.ubicacion || cat.ubicacion || '-'}
                              </div>
                              <div className="fw-semibold text-warning mb-3">
                                {formatearPrecio(fav.precio ?? cat.precio)}
                              </div>
                              <Button
                                variant="outline-danger"
                                size="sm"
                                onClick={() => handleQuitarFavorito(propiedadId)}
                              >
                                <i className="bi bi-trash me-1"></i>Quitar
                              </Button>
                            </Card.Body>
                          </Card>
                        </Col>
                      );
                    })}
                  </Row>
                )}
              </>
            )}

            {seccion === 'alertas' && (
              <Card className="shadow-sm border-0">
                <Card.Body className="p-4">
                  <h5 className="fw-bold mb-2">Comunas con aviso automatico</h5>
                  <p className="text-muted">
                    Se crean solas al guardar un favorito en una comuna. Recibiras un resumen
                    agrupado cada 6 propiedades nuevas y un barrido diario.
                  </p>
                  {alertas.length === 0 ? (
                    <div className="text-muted">Todavia no sigues ninguna comuna.</div>
                  ) : (
                    <div className="d-flex flex-wrap gap-2">
                      {alertas.map((comuna) => (
                        <Badge key={comuna} bg="primary" className="fs-6 fw-normal px-3 py-2">
                          <i className="bi bi-bell me-1"></i>{comuna}
                        </Badge>
                      ))}
                    </div>
                  )}
                </Card.Body>
              </Card>
            )}

            {seccion === 'novedades' && (
              <>
                {novedades.length === 0 ? (
                  <Card className="shadow-sm border-0">
                    <Card.Body className="text-center text-muted p-5">
                      <i className="bi bi-newspaper fs-3 d-block mb-2"></i>
                      No hay novedades pendientes en tus comunas.
                    </Card.Body>
                  </Card>
                ) : (
                  novedades.map((resumen) => (
                    <Card className="shadow-sm border-0 mb-3" key={resumen.id}>
                      <Card.Body>
                        <div className="d-flex justify-content-between align-items-start">
                          <div>
                            <h6 className="fw-bold mb-1">
                              <i className="bi bi-bell-fill text-warning me-2"></i>
                              {resumen.titulo || `${resumen.cantidad} nuevas propiedades`}
                            </h6>
                            <div className="text-muted small mb-2">
                              {(resumen.comunas || []).join(', ')}
                            </div>
                          </div>
                          <Button
                            variant="outline-secondary"
                            size="sm"
                            onClick={() => handleDescartarNovedad(resumen.id)}
                          >
                            Descartar
                          </Button>
                        </div>
                        <ul className="mb-0">
                          {(resumen.items || []).map((item) => (
                            <li key={item.propiedadId}>
                              <strong>{item.titulo || 'Propiedad'}</strong> — {item.comuna} —{' '}
                              {formatearPrecio(item.precio)}
                            </li>
                          ))}
                        </ul>
                      </Card.Body>
                    </Card>
                  ))
                )}
              </>
            )}
          </>
        )}

        <div className="text-center mt-4">
          <Button as={Link} to="/" variant="link" className="text-decoration-none">
            <i className="bi bi-arrow-left me-1"></i>Volver al catalogo
          </Button>
        </div>
      </Container>
      <Footer />
    </div>
  );
}

export default Cliente;
