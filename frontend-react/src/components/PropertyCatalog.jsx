import React, { useState, useEffect } from 'react';
import { Container, Row, Col, Button, Card, Badge } from 'react-bootstrap';
import { useMsal } from '@azure/msal-react';
import { haySesion } from '../msalConfig';
import {
  obtenerFavoritos,
  obtenerAlertas,
  agregarFavorito,
  eliminarFavorito,
  seguirComuna,
  extraerComunaLocal
} from '../api/apiClient';

const PropertyCatalog = ({ propiedades, filtro, setFiltro, onImageClick }) => {
  const { accounts } = useMsal();
  const isLoggedIn = accounts.length > 0 || haySesion();
  const [currentImages, setCurrentImages] = useState({});
  const [favoritosIds, setFavoritosIds] = useState(new Set());
  const [alertas, setAlertas] = useState(new Set());
  const [cargandoFavoritos, setCargandoFavoritos] = useState(true);

  // Carga inicial de favoritos y alertas cuando hay sesion. Silencia errores
  // para no romper el catalogo publico si el backend no esta disponible.
  useEffect(() => {
    const cargarWishlist = async () => {
      if (!isLoggedIn) {
        setCargandoFavoritos(false);
        return;
      }
      try {
        const favoritosData = await obtenerFavoritos();
        const lista = Array.isArray(favoritosData)
          ? favoritosData
          : (favoritosData?.favoritos || []);
        const ids = new Set(
          lista
            .map((item) => item?.propiedadId ?? item?.id ?? item)
            .filter((valor) => valor !== null && valor !== undefined && valor !== '')
            .map((valor) => String(valor))
        );
        setFavoritosIds(ids);
      } catch (_) {
        // Error silencioso: el catalogo funciona igual sin wishlist.
      }
      try {
        const alertasData = await obtenerAlertas();
        const lista = Array.isArray(alertasData)
          ? alertasData
          : (alertasData?.alertas || alertasData?.comunas || []);
        const comunas = new Set(
          lista
            .map((item) => (typeof item === 'string' ? item : item?.comuna ?? item?.nombre ?? null))
            .filter((valor) => valor)
        );
        setAlertas(comunas);
      } catch (_) {
        // Error silencioso.
      }
      setCargandoFavoritos(false);
    };
    cargarWishlist();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const formatearPrecio = (precio) => {
    const num = parseInt(String(precio).replace(/\./g, '')) || 0;
    return new Intl.NumberFormat('es-CL', {
      style: 'currency',
      currency: 'CLP',
      minimumFractionDigits: 0
    }).format(num);
  };

  const cambiarImagen = (id, direction) => {
    setCurrentImages(prev => {
      const propiedad = propiedades.find(p => p.id === id);
      if (!propiedad || propiedad.imagenes.length <= 1) return prev;
      
      const currentIndex = prev[id] || 0;
      const newIndex = (currentIndex + direction + propiedad.imagenes.length) % propiedad.imagenes.length;
      return { ...prev, [id]: newIndex };
    });
  };

  const obtenerImagenActual = (propiedad) => {
    const index = currentImages[propiedad.id] || 0;
    return propiedad.imagenes[index];
  };

  const obtenerContadorImagen = (propiedad) => {
    const index = currentImages[propiedad.id] || 0;
    return `${index + 1}/${propiedad.imagenes.length}`;
  };

  const generarMensajeWhatsApp = (propiedad) => {
    const numeroTelefono = "56985519073";
    const sufijo = propiedad.tipoPrecio || "/ Valor Total";
    const textoMensaje = `Hola, me interesa la propiedad ("${propiedad.tipoOperacion}") publicada: "${propiedad.titulo}" con un valor de ${formatearPrecio(propiedad.precio)} ${sufijo}. Me gustaría recibir más información.`;
    return `https://wa.me/${numeroTelefono}?text=${encodeURIComponent(textoMensaje)}`;
  };

  const toggleFavorito = async (p) => {
    if (!isLoggedIn) {
      alert("Inicia sesión para guardar favoritos");
      return;
    }
    const yaEsFavorito = favoritosIds.has(String(p.id));
    try {
      if (yaEsFavorito) {
        await eliminarFavorito(p.id);
        setFavoritosIds((prev) => {
          const siguiente = new Set(prev);
          siguiente.delete(String(p.id));
          return siguiente;
        });
      } else {
        await agregarFavorito({
          propiedadId: p.id,
          titulo: p.titulo,
          precio: p.precio,
          tipoPrecio: p.tipoPrecio,
          ubicacion: p.ubicacion
        });
        setFavoritosIds((prev) => new Set(prev).add(String(p.id)));
        // El backend crea automaticamente la alerta de la comuna.
        const comuna = extraerComunaLocal(p.ubicacion);
        if (comuna) {
          setAlertas((prev) => new Set(prev).add(comuna));
        }
      }
    } catch (error) {
      alert(error.message || 'No se pudo actualizar el favorito');
    }
  };

  const seguirComunaDePropiedad = async (comuna) => {
    if (!isLoggedIn) {
      alert("Inicia sesión para guardar favoritos");
      return;
    }
    if (!comuna) return;
    try {
      await seguirComuna(comuna);
      setAlertas((prev) => new Set(prev).add(comuna));
    } catch (error) {
      alert(error.message || 'No se pudo seguir la comuna');
    }
  };

  return (
    <section id="propiedades" className="container mb-5">
      <Row>
        <Col xs={12}>
          <h2 className="text-center mb-2">Propiedades Disponibles</h2>
          <p className="text-center text-muted mb-4">Filtra nuestro catálogo según lo que estés buscando</p>
        </Col>
      </Row>

      <Row className="mb-4">
        <Col xs={12} className="d-flex justify-content-center gap-2 flex-wrap">
          <Button 
            variant={filtro === 'Todos' ? 'dark' : 'outline-dark'} 
            className="filter-btn"
            onClick={() => setFiltro('Todos')}
          >
            ✨ Todos
          </Button>
          <Button 
            variant={filtro === 'Casa' ? 'dark' : 'outline-dark'} 
            className="filter-btn"
            onClick={() => setFiltro('Casa')}
          >
            🏡 Casas
          </Button>
          <Button 
            variant={filtro === 'Departamento' ? 'dark' : 'outline-dark'} 
            className="filter-btn"
            onClick={() => setFiltro('Departamento')}
          >
            🏢 Departamentos
          </Button>
          <Button 
            variant={filtro === 'Terreno' ? 'dark' : 'outline-dark'} 
            className="filter-btn"
            onClick={() => setFiltro('Terreno')}
          >
            🌱 Terrenos
          </Button>
        </Col>
      </Row>

      <Row id="contenedor-propiedades" className="g-4">
        {propiedades.length === 0 ? (
          <Col xs={12} className="text-center text-muted p-5">
            <i className="bi bi-info-circle fs-3 d-block mb-2"></i>
            No hay propiedades disponibles en esta categoría por el momento.
          </Col>
        ) : (
          propiedades.map((propiedad) => {
            const imagenActual = obtenerImagenActual(propiedad);
            const badgeOperacion = propiedad.tipoOperacion === 'Arriendo' ? (
              <Badge bg="warning" text="dark" className="me-1">
                <i className="bi bi-key-fill me-1"></i>Arriendo
              </Badge>
            ) : (
              <Badge bg="success" text="white" className="me-1">
                <i className="bi bi-cash-coin me-1"></i>Venta
              </Badge>
            );

            const htmlGastosComunes = (propiedad.gastosComunes && propiedad.gastosComunes > 0) ? (
              <div className="text-muted small mb-2">
                <i className="bi bi-receipt me-1"></i>GGCC: <strong>{formatearPrecio(propiedad.gastosComunes)}</strong>
              </div>
            ) : '';

            const htmlBotonVideo = propiedad.videoUrl ? (
              <a href={propiedad.videoUrl} target="_blank" rel="noopener noreferrer" className="btn btn-outline-danger btn-sm w-100 mb-2 mt-auto d-flex align-items-center justify-content-center">
                <i className="bi bi-youtube me-2"></i>Ver Video / Tour Virtual
              </a>
            ) : '';

            const sufijo = propiedad.tipoPrecio || "/ Valor Total";
            const comunaLocal = extraerComunaLocal(propiedad.ubicacion);
            const esFavorito = favoritosIds.has(String(propiedad.id));
            const siguiendoComuna = comunaLocal ? alertas.has(comunaLocal) : false;

            return (
              <Col key={propiedad.id} xs={12} sm={6} md={4} lg={4}>
                <Card className="property-card h-100 shadow-sm border-0">
                  <div className="image-carousel" style={{ position: 'relative', overflow: 'hidden' }}>
                    {propiedad.imagenes.length > 1 && (
                      <>
                        <button 
                          className="carousel-nav carousel-prev"
                          onClick={() => cambiarImagen(propiedad.id, -1)}
                          style={{ display: propiedad.imagenes.length > 1 ? 'block' : 'none' }}
                        >
                          ‹
                        </button>
                        <button 
                          className="carousel-nav carousel-next"
                          onClick={() => cambiarImagen(propiedad.id, 1)}
                          style={{ display: propiedad.imagenes.length > 1 ? 'block' : 'none' }}
                        >
                          ›
                        </button>
                      </>
                    )}
                    <img 
                      src={imagenActual} 
                      alt={propiedad.titulo} 
                      loading="lazy"
                      onClick={() => onImageClick(imagenActual)}
                      title="Haz clic para expandir la imagen"
                      style={{ height: '220px', objectFit: 'cover', width: '100%', cursor: 'pointer', transition: 'transform 0.2s ease' }}
                    />
                    {propiedad.imagenes.length > 1 && (
                      <div className="image-counter" style={{ display: propiedad.imagenes.length > 1 ? 'block' : 'none' }}>
                        {obtenerContadorImagen(propiedad)}
                      </div>
                    )}
                  </div>
                  <Card.Body className="d-flex flex-column">
                    <div className="mb-2">
                      {badgeOperacion}
                      <Badge bg="secondary">{propiedad.categoria}</Badge>
                    </div>
                    <Card.Title className="fw-bold" style={{ color: '#111' }}>
                      {propiedad.titulo}
                    </Card.Title>
                    <p className="price text-success fw-bold fs-5 mb-2">
                      {formatearPrecio(propiedad.precio)}{' '}
                      <span style={{ fontSize: '0.7em', color: '#666', fontWeight: 'normal' }}>
                        {sufijo}
                      </span>
                    </p>
                    
                    <div className="features mb-2 d-flex gap-2 flex-wrap text-muted small">
                      <span className="feature me-2">
                        <i className="bi bi-door-open-fill me-1"></i>{propiedad.piezas} {propiedad.piezas === 1 ? 'dorm.' : 'dorm.'}
                      </span>
                      <span className="feature me-2">
                        <i className="bi bi-droplet-fill me-1"></i>{propiedad.banos} {propiedad.banos === 1 ? 'baño' : 'baños'}
                      </span>
                      <span className="feature me-2">
                        <i className="bi bi-p-circle-fill me-1"></i>{propiedad.estacionamiento} estac.
                      </span>
                      <span className="feature">
                        <i className="bi bi-box-seam-fill me-1"></i>{propiedad.bodega} bod.
                      </span>
                    </div>

                    {htmlGastosComunes}

                    <p className="location text-muted small mb-3 mt-auto">
                      <i className="bi bi-geo-alt-fill me-1 text-danger"></i>{propiedad.ubicacion}
                    </p>

                    {htmlBotonVideo}

                    <a 
                      href={generarMensajeWhatsApp(propiedad)} 
                      target="_blank" 
                      rel="noopener noreferrer"
                      className="btn btn-primary btn-contact w-100 d-flex align-items-center justify-content-center"
                      style={{ textDecoration: 'none' }}
                    >
                      <i className="bi bi-whatsapp me-2"></i>Contactar
                    </a>

                    <div className="d-flex gap-2 mt-2">
                      <Button
                        size="sm"
                        variant={esFavorito ? 'danger' : 'outline-danger'}
                        className={`flex-fill${esFavorito ? ' btn-favorito-activo' : ''}`}
                        onClick={() => toggleFavorito(propiedad)}
                      >
                        <i className={`bi ${esFavorito ? 'bi-heart-fill' : 'bi-heart'} me-1`}></i>
                        {esFavorito ? 'Favorito' : 'Guardar'}
                      </Button>
                      <Button
                        size="sm"
                        variant="outline-primary"
                        className="flex-fill"
                        disabled={!comunaLocal || siguiendoComuna}
                        title={!comunaLocal ? 'No se detectó la comuna en la ubicación' : (siguiendoComuna ? `Siguiendo ${comunaLocal}` : `Avisarme de ${comunaLocal}`)}
                        onClick={() => seguirComunaDePropiedad(comunaLocal)}
                      >
                        <i className={`bi ${siguiendoComuna ? 'bi-bell-fill' : 'bi-bell'} me-1`}></i>
                        {siguiendoComuna ? `Siguiendo ${comunaLocal}` : (comunaLocal ? `Avisarme de ${comunaLocal}` : 'Avisarme')}
                      </Button>
                    </div>
                  </Card.Body>
                </Card>
              </Col>
            );
          })
        )}
      </Row>
    </section>
  );
};

export default PropertyCatalog;
