import React, { useState, useEffect } from 'react';
import { Container, Row, Col, Card, Badge, Button, Spinner } from 'react-bootstrap';
import { Link } from 'react-router-dom';
import { useMsal } from '@azure/msal-react';
import { haySesion } from '../msalConfig';
import Navbar from '../components/Navbar';
import Footer from '../components/Footer';
import {
  obtenerFavoritos,
  obtenerAlertas,
  obtenerPropiedadesPublicas,
  eliminarFavorito,
  extraerComunaLocal,
  agruparGuardadasPorComuna
} from '../api/apiClient';
import '../App.css';

function Wishlist() {
  const { accounts } = useMsal();
  const isLoggedIn = accounts.length > 0 || haySesion();

  const [favoritos, setFavoritos] = useState([]);
  const [alertas, setAlertas] = useState([]);
  const [catalogo, setCatalogo] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);

  const formatearPrecio = (precio) => {
    const num = parseInt(String(precio).replace(/\./g, '')) || 0;
    return new Intl.NumberFormat('es-CL', {
      style: 'currency',
      currency: 'CLP',
      minimumFractionDigits: 0
    }).format(num);
  };

  const normalizarFavoritos = (data) => {
    const lista = Array.isArray(data) ? data : (data?.favoritos || []);
    return lista;
  };

  const normalizarAlertas = (data) => {
    const lista = Array.isArray(data) ? data : (data?.alertas || data?.comunas || []);
    return lista
      .map((item) => (typeof item === 'string' ? item : item?.comuna ?? item?.nombre ?? null))
      .filter(Boolean);
  };

  const cargarDatos = async () => {
    if (!isLoggedIn) {
      setCargando(false);
      return;
    }
    setCargando(true);
    setError(null);
    let favs = [];
    try {
      const favoritosData = await obtenerFavoritos();
      favs = normalizarFavoritos(favoritosData);
      setFavoritos(favs);
    } catch (e) {
      setError(e.message || 'No se pudieron cargar los favoritos');
    }
    try {
      const alertasData = await obtenerAlertas();
      setAlertas(normalizarAlertas(alertasData));
    } catch (_) {
      // No bloqueante: las comunas seguidas tambien se deducen de favoritos.
      setAlertas([]);
    }
    try {
      const publicas = await obtenerPropiedadesPublicas();
      setCatalogo(Array.isArray(publicas) ? publicas : []);
    } catch (_) {
      // Silencioso: si el catalogo no responde, se muestra el snapshot.
      setCatalogo([]);
    }
    setCargando(false);
  };

  useEffect(() => {
    cargarDatos();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoggedIn]);

  const handleQuitar = async (propiedadId) => {
    try {
      await eliminarFavorito(propiedadId);
      await cargarDatos();
    } catch (e) {
      alert(e.message || 'No se pudo quitar el favorito');
    }
  };

  const generarMensajeWhatsApp = (item) => {
    const numeroTelefono = '56985519073';
    const sufijo = item.tipoPrecio || '/ Valor Total';
    const textoMensaje = `Hola, me interesa la propiedad ("${item.tipoOperacion || 'Venta'}") publicada: "${item.titulo || 'Sin título'}" con un valor de ${formatearPrecio(item.precio)} ${sufijo}. Me gustaría recibir más información.`;
    return `https://wa.me/${numeroTelefono}?text=${encodeURIComponent(textoMensaje)}`;
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
              <p className="text-muted mb-4">Necesitas iniciar sesión para ver tus favoritos y avisos de nuevas propiedades en tus comunas.</p>
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

  const catalogoPorId = catalogo.reduce((acc, p) => {
    if (p && p.id !== null && p.id !== undefined) acc[String(p.id)] = p;
    return acc;
  }, {});

  const enriquecidos = favoritos.map((fav) => {
    const propiedadId = fav?.propiedadId ?? fav?.id;
    const cat = catalogoPorId[String(propiedadId)] || {};
    const imagenesCat = Array.isArray(cat.imagenes) ? cat.imagenes : [];
    const imagen =
      imagenesCat[0] ||
      (Array.isArray(fav?.imagenes) ? fav.imagenes[0] : null) ||
      fav?.imagen ||
      cat?.imagen ||
      null;
    return {
      ...fav,
      propiedadId,
      titulo: fav?.titulo || cat?.titulo || 'Sin título',
      precio: fav?.precio ?? cat?.precio,
      tipoPrecio: fav?.tipoPrecio || cat?.tipoPrecio,
      ubicacion: fav?.ubicacion || cat?.ubicacion || '-',
      tipoOperacion: cat?.tipoOperacion || fav?.tipoOperacion,
      imagen,
      comuna: fav?.comuna || extraerComunaLocal(fav?.ubicacion || cat?.ubicacion) || ''
    };
  });

  // TODAS las guardadas, agrupadas por comuna para ver el resumen completo.
  const grupos = agruparGuardadasPorComuna(enriquecidos);
  const comunasSeguidas = [...new Set([
    ...alertas,
    ...enriquecidos.map((e) => e.comuna).filter(Boolean)
  ])];

  return (
    <div className="App">
      <Navbar />
      <Container className="py-5 mt-5">
        <h2 className="text-center fw-bold mb-2">Mis propiedades guardadas</h2>
        <p className="text-center text-muted mb-4">
          Todas tus propiedades guardadas ({favoritos.length})
          {comunasSeguidas.length > 0 && <> · Comunas con aviso automático: {comunasSeguidas.join(', ')}</>}
        </p>

        <div className="alert alert-info d-flex align-items-center justify-content-center text-center" role="status">
          <i className="bi bi-bell-fill me-2"></i>
          <span>
            Las alertas son automáticas: cuando guardas una propiedad, te avisamos aquí y por correo
            cuando admin o corredor publique una nueva en esa misma comuna. No hay botón de avisos:
            llega solo.
          </span>
        </div>

        {error && (
          <div className="alert alert-danger d-flex align-items-center" role="alert">
            <i className="bi bi-exclamation-triangle-fill me-2"></i>{error}
          </div>
        )}

        {cargando ? (
          <div className="text-center py-5">
            <Spinner animation="border" role="status" className="me-2" />
            <span>Cargando tus propiedades guardadas...</span>
          </div>
        ) : (
          <>
            {enriquecidos.length === 0 ? (
              <Card className="shadow-sm border-0">
                <Card.Body className="text-center text-muted p-5">
                  <i className="bi bi-heart fs-3 d-block mb-2"></i>
                  Aún no tienes propiedades guardadas. Guarda propiedades desde el catálogo y aparecerán aquí, agrupadas por comuna.
                </Card.Body>
              </Card>
            ) : (
              Object.keys(grupos).sort((a, b) => a.localeCompare(b)).map((comuna) => (
                <div key={comuna} className="mb-4">
                  <h5 className="fw-bold mb-2">
                    <i className="bi bi-geo-alt me-1"></i>{comuna} ({grupos[comuna].length})
                  </h5>
                  <Row className="g-4">
                    {grupos[comuna].map((item, idx) => (
                      <Col key={item.propiedadId ?? idx} xs={12} sm={6} md={4} lg={4}>
                        <Card className="property-card h-100 shadow-sm border-0">
                          {item.imagen ? (
                            <img
                              src={item.imagen}
                              alt={item.titulo}
                              loading="lazy"
                              style={{ height: '220px', objectFit: 'cover', width: '100%' }}
                            />
                          ) : (
                            <div
                              className="d-flex align-items-center justify-content-center bg-light text-muted"
                              style={{ height: '220px' }}
                            >
                              <i className="bi bi-image fs-1"></i>
                            </div>
                          )}
                          <Card.Body className="d-flex flex-column">
                            <div className="mb-2 d-flex flex-wrap gap-1">
                              {item.tipoOperacion && (
                                <Badge bg={item.tipoOperacion === 'Arriendo' ? 'warning' : 'success'} text={item.tipoOperacion === 'Arriendo' ? 'dark' : 'white'}>
                                  <i className={`bi ${item.tipoOperacion === 'Arriendo' ? 'bi-key-fill' : 'bi-cash-coin'} me-1`}></i>
                                  {item.tipoOperacion}
                                </Badge>
                              )}
                              {item.comuna && (
                                <Badge bg="info" text="dark">
                                  <i className="bi bi-geo-alt me-1"></i>{item.comuna}
                                </Badge>
                              )}
                            </div>
                            <Card.Title className="fw-bold" style={{ color: '#111' }}>
                              {item.titulo}
                            </Card.Title>
                            <p className="price text-success fw-bold fs-5 mb-2">
                              {item.precio ? formatearPrecio(item.precio) : '-'}
                              {item.tipoPrecio && (
                                <span style={{ fontSize: '0.7em', color: '#666', fontWeight: 'normal' }}>
                                  {' '}{item.tipoPrecio}
                                </span>
                              )}
                            </p>
                            <p className="location text-muted small mb-3 mt-auto">
                              <i className="bi bi-geo-alt-fill me-1 text-danger"></i>{item.ubicacion || '-'}
                            </p>
                            <div className="d-flex gap-2">
                              <a
                                href={generarMensajeWhatsApp(item)}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="btn btn-sm btn-primary btn-contact flex-fill d-flex align-items-center justify-content-center"
                                style={{ textDecoration: 'none' }}
                              >
                                <i className="bi bi-whatsapp me-1"></i>Contactar
                              </a>
                              <Button
                                size="sm"
                                variant="danger"
                                className="flex-fill"
                                onClick={() => handleQuitar(item.propiedadId)}
                              >
                                <i className="bi bi-trash-fill me-1"></i>Quitar
                              </Button>
                            </div>
                          </Card.Body>
                        </Card>
                      </Col>
                    ))}
                  </Row>
                </div>
              ))
            )}
          </>
        )}
      </Container>
      <Footer />
    </div>
  );
}

export default Wishlist;
