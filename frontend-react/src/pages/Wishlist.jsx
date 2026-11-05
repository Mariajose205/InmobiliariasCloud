import React, { useState, useEffect } from 'react';
import { Container, Card, Table, Badge, Button, Form, Spinner } from 'react-bootstrap';
import { Link } from 'react-router-dom';
import { useMsal } from '@azure/msal-react';
import { haySesion } from '../msalConfig';
import Navbar from '../components/Navbar';
import Footer from '../components/Footer';
import {
  obtenerFavoritos,
  obtenerAlertas,
  obtenerComunas,
  eliminarFavorito,
  seguirComuna,
  dejarDeSeguirComuna,
  COMUNAS,
  extraerComunaLocal
} from '../api/apiClient';
import '../App.css';

function Wishlist() {
  const { accounts } = useMsal();
  const isLoggedIn = accounts.length > 0 || haySesion();

  const [favoritos, setFavoritos] = useState([]);
  const [alertas, setAlertas] = useState([]);
  const [comunas, setComunas] = useState(COMUNAS);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [comunaSeleccionada, setComunaSeleccionada] = useState('');

  const formatearPrecio = (precio) => {
    const num = parseInt(String(precio).replace(/\./g, '')) || 0;
    return new Intl.NumberFormat('es-CL', {
      style: 'currency',
      currency: 'CLP',
      minimumFractionDigits: 0
    }).format(num);
  };

  const normalizarAlertas = (data) => {
    const lista = Array.isArray(data) ? data : (data?.alertas || data?.comunas || []);
    return lista
      .map((item) => (typeof item === 'string' ? item : item?.comuna ?? item?.nombre ?? null))
      .filter(Boolean);
  };

  const normalizarFavoritos = (data) => {
    const lista = Array.isArray(data) ? data : (data?.favoritos || []);
    return lista;
  };

  const cargarDatos = async () => {
    if (!isLoggedIn) {
      setCargando(false);
      return;
    }
    setCargando(true);
    setError(null);
    try {
      const favoritosData = await obtenerFavoritos();
      setFavoritos(normalizarFavoritos(favoritosData));
    } catch (e) {
      setError(e.message || 'No se pudieron cargar los favoritos');
    }
    try {
      const alertasData = await obtenerAlertas();
      setAlertas(normalizarAlertas(alertasData));
    } catch (_) {
      setAlertas([]);
    }
    try {
      const comunasData = await obtenerComunas();
      setComunas(Array.isArray(comunasData) && comunasData.length > 0 ? comunasData : COMUNAS);
    } catch (_) {
      setComunas(COMUNAS);
    }
    setCargando(false);
  };

  useEffect(() => {
    cargarDatos();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleQuitar = async (propiedadId) => {
    try {
      await eliminarFavorito(propiedadId);
      await cargarDatos();
    } catch (e) {
      alert(e.message || 'No se pudo quitar el favorito');
    }
  };

  const handleDejarDeSeguir = async (comuna) => {
    try {
      await dejarDeSeguirComuna(comuna);
      await cargarDatos();
    } catch (e) {
      alert(e.message || 'No se pudo dejar de seguir la comuna');
    }
  };

  const handleSeguir = async () => {
    if (!comunaSeleccionada) {
      alert('Selecciona una comuna');
      return;
    }
    try {
      await seguirComuna(comunaSeleccionada);
      setComunaSeleccionada('');
      await cargarDatos();
    } catch (e) {
      alert(e.message || 'No se pudo seguir la comuna');
    }
  };

  if (!isLoggedIn) {
    return (
      <div className="App">
        <Navbar />
        <Container className="py-5 mt-5">
          <Card className="shadow-sm border-0">
            <Card.Body className="p-5 text-center">
              <i className="bi bi-heart-fill fs-1 text-danger d-block mb-3"></i>
              <h2 className="fw-bold mb-3">Inicia sesión para ver tus favoritos</h2>
              <p className="text-muted mb-4">Necesitas iniciar sesión para ver tus favoritos y alertas por comuna.</p>
              <Button as={Link} to="/login" variant="primary">
                <i className="bi bi-person-circle me-2"></i>Ir a Iniciar Sesión
              </Button>
            </Card.Body>
          </Card>
        </Container>
        <Footer />
      </div>
    );
  }

  return (
    <div className="App">
      <Navbar />
      <Container className="py-5 mt-5">
        <h2 className="text-center fw-bold mb-2">Mis favoritos y alertas</h2>
        <p className="text-center text-muted mb-4">Gestiona tus propiedades guardadas y las comunas que sigues</p>

        {error && (
          <div className="alert alert-danger d-flex align-items-center" role="alert">
            <i className="bi bi-exclamation-triangle-fill me-2"></i>{error}
          </div>
        )}

        {cargando ? (
          <div className="text-center py-5">
            <Spinner animation="border" role="status" className="me-2" />
            <span>Cargando tus favoritos...</span>
          </div>
        ) : (
          <>
            <Card className="shadow-sm border-0 mb-4">
              <Card.Header className="bg-white fw-bold">
                <i className="bi bi-heart-fill text-danger me-2"></i>Favoritos ({favoritos.length})
              </Card.Header>
              <Card.Body className="p-0">
                {favoritos.length === 0 ? (
                  <div className="text-center text-muted p-4">
                    <i className="bi bi-heart fs-3 d-block mb-2"></i>
                    Aún no tienes favoritos guardados.
                  </div>
                ) : (
                  <Table striped hover responsive className="mb-0 wishlist-table">
                    <thead>
                      <tr>
                        <th>Título</th>
                        <th>Precio</th>
                        <th>Ubicación</th>
                        <th>Comuna</th>
                        <th className="text-end">Acción</th>
                      </tr>
                    </thead>
                    <tbody>
                      {favoritos.map((fav, idx) => {
                        const propiedadId = fav?.propiedadId ?? fav?.id;
                        const comuna = fav?.comuna || extraerComunaLocal(fav?.ubicacion);
                        return (
                          <tr key={propiedadId ?? idx}>
                            <td className="fw-semibold">{fav?.titulo || 'Sin título'}</td>
                            <td className="text-success fw-bold">
                              {fav?.precio ? formatearPrecio(fav.precio) : '-'}
                            </td>
                            <td>{fav?.ubicacion || '-'}</td>
                            <td>
                              {comuna ? <Badge bg="secondary">{comuna}</Badge> : <span className="text-muted">-</span>}
                            </td>
                            <td className="text-end">
                              <Button
                                size="sm"
                                variant="danger"
                                onClick={() => handleQuitar(propiedadId)}
                              >
                                <i className="bi bi-trash-fill me-1"></i>Quitar
                              </Button>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </Table>
                )}
              </Card.Body>
            </Card>

            <Card className="shadow-sm border-0 mb-4">
              <Card.Header className="bg-white fw-bold">
                <i className="bi bi-bell-fill text-primary me-2"></i>Comunas que sigues ({alertas.length})
              </Card.Header>
              <Card.Body>
                {alertas.length === 0 ? (
                  <p className="text-muted mb-0">
                    <i className="bi bi-bell me-2"></i>No sigues ninguna comuna todavía.
                  </p>
                ) : (
                  <div className="d-flex flex-wrap gap-2">
                    {alertas.map((comuna) => (
                      <Badge key={comuna} bg="primary" className="comuna-badge p-2">
                        <span className="me-2">{comuna}</span>
                        <Button
                          size="sm"
                          variant="link"
                          className="text-white p-0 text-decoration-none"
                          onClick={() => handleDejarDeSeguir(comuna)}
                          title={`Dejar de seguir ${comuna}`}
                        >
                          <i className="bi bi-x-lg"></i>
                        </Button>
                      </Badge>
                    ))}
                  </div>
                )}
              </Card.Body>
            </Card>

            <Card className="shadow-sm border-0">
              <Card.Header className="bg-white fw-bold">
                <i className="bi bi-bell-plus-fill text-primary me-2"></i>Seguir una comuna
              </Card.Header>
              <Card.Body>
                <p className="text-muted small">Te avisaremos cuando se publique una nueva propiedad en la comuna seleccionada.</p>
                <Form className="d-flex gap-2 flex-wrap">
                  <Form.Select
                    aria-label="Selecciona una comuna"
                    value={comunaSeleccionada}
                    onChange={(e) => setComunaSeleccionada(e.target.value)}
                    className="flex-grow-1"
                  >
                    <option value="">Selecciona una comuna...</option>
                    {comunas.map((c) => (
                      <option key={c} value={c}>{c}</option>
                    ))}
                  </Form.Select>
                  <Button variant="primary" onClick={handleSeguir} disabled={!comunaSeleccionada}>
                    <i className="bi bi-bell-fill me-1"></i>Seguir
                  </Button>
                </Form>
              </Card.Body>
            </Card>
          </>
        )}
      </Container>
      <Footer />
    </div>
  );
}

export default Wishlist;
