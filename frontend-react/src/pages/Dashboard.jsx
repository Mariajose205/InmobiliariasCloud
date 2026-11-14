import React, { useState, useEffect } from 'react';
import { Container, Row, Col, Card, Badge, Button, Spinner, Alert } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import { useMsal } from '@azure/msal-react';
import { obtenerRolCuenta } from '../msalConfig';
import {
  obtenerAuditoria,
  obtenerEstadisticasAdmin,
  obtenerPropiedadesPublicas,
  extraerComunaLocal
} from '../api/apiClient';

const MESES = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];
const COLORES_CATEGORIA = ['#a0523d', '#6bb4b9', '#2d3748', '#718096', '#c98b5f', '#4a8f8f'];
const COMISION_ESTIMADA = 0.03;

const METRICAS_VACIAS = {
  totalFavoritos: 0,
  clientesConFavoritos: 0,
  totalAlertas: 0,
  clientesConAlertas: 0,
  totalResumenes: 0,
  resumenesEnviados: 0,
  topPropiedadesGuardadas: [],
  topComunasFavoritos: [],
  topComunasAlertas: []
};

/** La API publica devuelve la lista; se acepta tambien un envoltorio. */
function normalizarPropiedades(datos) {
  if (Array.isArray(datos)) return datos;
  if (datos && Array.isArray(datos.propiedades)) return datos.propiedades;
  return [];
}

/** Cifras reales del wishlist-service, con valores seguros si falta un campo. */
function normalizarMetricas(datos) {
  if (!datos || typeof datos !== 'object') return METRICAS_VACIAS;
  return {
    ...METRICAS_VACIAS,
    ...datos,
    topPropiedadesGuardadas: Array.isArray(datos.topPropiedadesGuardadas)
      ? datos.topPropiedadesGuardadas
      : [],
    topComunasFavoritos: Array.isArray(datos.topComunasFavoritos) ? datos.topComunasFavoritos : [],
    topComunasAlertas: Array.isArray(datos.topComunasAlertas) ? datos.topComunasAlertas : []
  };
}

const esVenta = (p) => String(p.tipoOperacion || '').trim().toLowerCase() === 'venta';
const esArriendo = (p) => String(p.tipoOperacion || '').trim().toLowerCase() === 'arriendo';

/** Ultimos 12 meses calendario con la cantidad de propiedades publicadas. */
function calcularPublicacionesPorMes(propiedades) {
  const hoy = new Date();
  const buckets = [];
  for (let i = 11; i >= 0; i -= 1) {
    const fecha = new Date(hoy.getFullYear(), hoy.getMonth() - i, 1);
    buckets.push({
      clave: `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}`,
      etiqueta: MESES[fecha.getMonth()],
      anio: fecha.getFullYear(),
      cantidad: 0
    });
  }
  const indice = new Map(buckets.map((bucket, posicion) => [bucket.clave, posicion]));
  propiedades.forEach((p) => {
    if (!p.fechaCreacion) return;
    const fecha = new Date(p.fechaCreacion);
    if (Number.isNaN(fecha.getTime())) return;
    const clave = `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}`;
    const posicion = indice.get(clave);
    if (posicion !== undefined) buckets[posicion].cantidad += 1;
  });
  return buckets;
}

/** Distribucion por categoria con porcentaje sobre el total del catalogo. */
function calcularCategorias(propiedades) {
  const conteo = new Map();
  propiedades.forEach((p) => {
    const nombre = (p.categoria || 'Sin categoría').trim() || 'Sin categoría';
    conteo.set(nombre, (conteo.get(nombre) || 0) + 1);
  });
  const total = propiedades.length;
  return [...conteo.entries()]
    .map(([nombre, cantidad], posicion) => ({
      nombre,
      cantidad,
      porcentaje: total > 0 ? Math.round((cantidad * 100) / total) : 0,
      color: COLORES_CATEGORIA[posicion % COLORES_CATEGORIA.length]
    }))
    .sort((a, b) => b.cantidad - a.cantidad);
}

/**
 * Comunas del catalogo. Usa la misma normalizacion que el catalogo publico
 * (extraerComunaLocal); las ubicaciones que no calzan con una comuna conocida
 * se informan aparte en vez de inventar una.
 */
function calcularComunas(propiedades) {
  const conteo = new Map();
  let sinComuna = 0;
  propiedades.forEach((p) => {
    const comuna = extraerComunaLocal(p.ubicacion);
    if (!comuna) {
      sinComuna += 1;
      return;
    }
    conteo.set(comuna, (conteo.get(comuna) || 0) + 1);
  });
  const lista = [...conteo.entries()]
    .map(([nombre, cantidad]) => ({ nombre, propiedades: cantidad }))
    .sort((a, b) => b.propiedades - a.propiedades)
    .slice(0, 5);
  return { lista, sinComuna };
}

function formatearMoneda(valor) {
  if (!Number.isFinite(valor) || valor <= 0) return '—';
  return new Intl.NumberFormat('es-CL', {
    style: 'currency',
    currency: 'CLP',
    maximumFractionDigits: 0
  }).format(valor);
}

function formatearNumero(valor) {
  return Number(valor || 0).toLocaleString('es-CL');
}

function Dashboard() {
  const { accounts, inProgress } = useMsal();
  const navigate = useNavigate();

  const [auditoria, setAuditoria] = useState([]);
  const [cargandoAuditoria, setCargandoAuditoria] = useState(true);
  const [errorAuditoria, setErrorAuditoria] = useState(null);

  const [propiedades, setPropiedades] = useState([]);
  const [metricas, setMetricas] = useState(METRICAS_VACIAS);
  const [cargandoDatos, setCargandoDatos] = useState(true);
  const [errorDatos, setErrorDatos] = useState(null);

  const rol = obtenerRolCuenta(accounts[0]);
  const isAdmin = rol === 'admin';
  const isLoggedIn = accounts.length > 0;

  useEffect(() => {
    if (inProgress === 'none') {
      if (!isLoggedIn) {
        navigate('/login');
      } else if (rol === null) {
        // Invitado (ej: profesor): fuera del panel, solo pagina principal.
        navigate('/');
      }
    }
  }, [inProgress, isLoggedIn, rol, navigate]);

  // Catalogo publico + metricas del wishlist-service. Se piden en paralelo y
  // con allSettled: si una fuente falla, el dashboard igual muestra la otra.
  useEffect(() => {
    if (!isAdmin) return;
    let activo = true;
    const cargarDatos = async () => {
      const [catalogo, estadisticas] = await Promise.allSettled([
        obtenerPropiedadesPublicas(),
        obtenerEstadisticasAdmin()
      ]);
      if (!activo) return;

      const errores = [];
      if (catalogo.status === 'fulfilled') {
        setPropiedades(normalizarPropiedades(catalogo.value));
      } else {
        setPropiedades([]);
        errores.push(`catálogo: ${catalogo.reason?.message || 'no disponible'}`);
      }

      if (estadisticas.status === 'fulfilled') {
        setMetricas(normalizarMetricas(estadisticas.value));
      } else {
        setMetricas(METRICAS_VACIAS);
        errores.push(`favoritos y alertas: ${estadisticas.reason?.message || 'no disponible'}`);
      }

      setErrorDatos(errores.length > 0 ? `No se pudieron cargar ${errores.join('; ')}.` : null);
      setCargandoDatos(false);
    };
    cargarDatos();
    return () => {
      activo = false;
    };
  }, [isAdmin]);

  useEffect(() => {
    if (!isAdmin) return;
    const cargarAuditoria = async () => {
      try {
        const datos = await obtenerAuditoria();
        setAuditoria(Array.isArray(datos) ? datos : []);
        setErrorAuditoria(null);
      } catch (err) {
        console.error('Error al cargar auditoría:', err);
        setErrorAuditoria(err.message || 'No se pudo cargar la actividad.');
      } finally {
        setCargandoAuditoria(false);
      }
    };
    cargarAuditoria();
  }, [isAdmin]);

  if (!isAdmin) {
    return (
      <Container style={{ maxWidth: '500px', marginTop: '100px' }}>
        <Card className="shadow-sm p-4 text-center">
          <h3 className="mb-3">🔒 Acceso Restringido</h3>
          <p className="text-muted">Solo el administrador puede ver el dashboard de estadísticas.</p>
          <Button variant="primary" onClick={() => navigate('/')}>Ir al Inicio</Button>
        </Card>
      </Container>
    );
  }

  // ---- Metricas derivadas del catalogo real ----
  const totalPropiedades = propiedades.length;
  const propiedadesVenta = propiedades.filter(esVenta).length;
  const propiedadesArriendo = propiedades.filter(esArriendo).length;

  const publicacionesPorMes = calcularPublicacionesPorMes(propiedades);
  const maxPublicaciones = Math.max(1, ...publicacionesPorMes.map((m) => m.cantidad));
  const totalUltimos12Meses = publicacionesPorMes.reduce((suma, m) => suma + m.cantidad, 0);
  const publicadasEsteMes = publicacionesPorMes[publicacionesPorMes.length - 1]?.cantidad || 0;
  const mesActual = publicacionesPorMes[publicacionesPorMes.length - 1];

  const categorias = calcularCategorias(propiedades);
  const { lista: comunasTop, sinComuna } = calcularComunas(propiedades);
  const maxComuna = Math.max(1, ...comunasTop.map((c) => c.propiedades));

  const preciosVenta = propiedades.filter((p) => esVenta(p) && Number(p.precio) > 0).map((p) => Number(p.precio));
  const preciosArriendo = propiedades.filter((p) => esArriendo(p) && Number(p.precio) > 0).map((p) => Number(p.precio));
  const promedio = (lista) => (lista.length > 0 ? Math.round(lista.reduce((a, b) => a + b, 0) / lista.length) : 0);
  const precioPromedioVenta = promedio(preciosVenta);
  const precioPromedioArriendo = promedio(preciosArriendo);
  const valorPortafolioVenta = preciosVenta.reduce((suma, precio) => suma + precio, 0);
  const comisionEstimada = Math.round(valorPortafolioVenta * COMISION_ESTIMADA);

  const topPropiedades = metricas.topPropiedadesGuardadas.slice(0, 5);
  const maxGuardados = Math.max(1, ...topPropiedades.map((p) => Number(p.guardados) || 0));
  const maxComunaFavoritos = Math.max(
    1,
    ...metricas.topComunasFavoritos.map((c) => Number(c.guardados) || 0)
  );
  const maxComunaAlertas = Math.max(
    1,
    ...metricas.topComunasAlertas.map((c) => Number(c.seguidores) || 0)
  );

  const infoAccion = (accion) => {
    switch (accion) {
      case 'CREAR':
        return { icono: 'bi-file-earmark-plus-fill', color: 'bg-success', texto: 'Creó propiedad' };
      case 'EDITAR':
        return { icono: 'bi-pencil-square', color: 'bg-primary', texto: 'Editó propiedad' };
      case 'ELIMINAR':
        return { icono: 'bi-trash-fill', color: 'bg-secondary', texto: 'Eliminó propiedad' };
      default:
        return { icono: 'bi-dot', color: 'bg-secondary', texto: accion || 'Acción' };
    }
  };

  const formatearFecha = (iso) => {
    try {
      const fecha = new Date(iso);
      if (Number.isNaN(fecha.getTime())) return iso || '';
      const dia = String(fecha.getDate()).padStart(2, '0');
      const mes = String(fecha.getMonth() + 1).padStart(2, '0');
      const anio = fecha.getFullYear();
      const hora = String(fecha.getHours()).padStart(2, '0');
      const minutos = String(fecha.getMinutes()).padStart(2, '0');
      return `${dia}/${mes}/${anio} ${hora}:${minutos}`;
    } catch (_) {
      return iso || '';
    }
  };

  return (
    <div className="dashboard-page">
      <Container fluid className="py-4 px-4">
        <div className="d-flex justify-content-between align-items-center mb-4 flex-wrap gap-2">
          <div>
            <h2 className="mb-1"><i className="bi bi-bar-chart-line-fill me-2"></i>Dashboard Administrativo</h2>
            <p className="text-muted mb-0">Datos reales del catálogo, los favoritos y las alertas</p>
          </div>
          <div className="d-flex align-items-center gap-2">
            <Badge bg="primary">Administrador</Badge>
            <Button variant="outline-primary" size="sm" onClick={() => navigate('/admin')}>
              <i className="bi bi-gear-fill me-1"></i>Gestión de Propiedades
            </Button>
            <Button variant="outline-secondary" size="sm" onClick={() => navigate('/')}>
              <i className="bi bi-house-door me-1"></i>Ver Sitio
            </Button>
          </div>
        </div>

        {errorDatos && (
          <Alert variant="warning" className="py-2">
            <i className="bi bi-exclamation-triangle me-2"></i>{errorDatos}
          </Alert>
        )}

        {cargandoDatos ? (
          <Card className="border-0 shadow-sm mb-4">
            <Card.Body className="text-center py-5 text-muted">
              <Spinner animation="border" size="sm" className="me-2" />
              Cargando datos del sitio...
            </Card.Body>
          </Card>
        ) : (
          <>
            {/* Tarjetas resumen */}
            <Row className="g-3 mb-4">
              <Col xs={12} sm={6} xl={3}>
                <Card className="stat-card border-0 shadow-sm h-100">
                  <Card.Body>
                    <div className="d-flex justify-content-between align-items-start">
                      <div>
                        <p className="stat-label mb-1">Propiedades en el catálogo</p>
                        <h3 className="stat-value mb-0">{formatearNumero(totalPropiedades)}</h3>
                      </div>
                      <div className="stat-icon bg-primary bg-opacity-10 text-primary">
                        <i className="bi bi-building"></i>
                      </div>
                    </div>
                    <small className="text-muted">
                      {publicadasEsteMes} publicadas en {mesActual?.etiqueta}
                    </small>
                  </Card.Body>
                </Card>
              </Col>
              <Col xs={12} sm={6} xl={3}>
                <Card className="stat-card border-0 shadow-sm h-100">
                  <Card.Body>
                    <div className="d-flex justify-content-between align-items-start">
                      <div>
                        <p className="stat-label mb-1">Favoritos guardados</p>
                        <h3 className="stat-value mb-0">{formatearNumero(metricas.totalFavoritos)}</h3>
                      </div>
                      <div className="stat-icon bg-danger bg-opacity-10 text-danger">
                        <i className="bi bi-heart-fill"></i>
                      </div>
                    </div>
                    <small className="text-muted">
                      {metricas.clientesConFavoritos} cliente{metricas.clientesConFavoritos === 1 ? '' : 's'} con favoritos
                    </small>
                  </Card.Body>
                </Card>
              </Col>
              <Col xs={12} sm={6} xl={3}>
                <Card className="stat-card border-0 shadow-sm h-100">
                  <Card.Body>
                    <div className="d-flex justify-content-between align-items-start">
                      <div>
                        <p className="stat-label mb-1">Alertas por comuna</p>
                        <h3 className="stat-value mb-0">{formatearNumero(metricas.totalAlertas)}</h3>
                      </div>
                      <div className="stat-icon bg-warning bg-opacity-10 text-warning">
                        <i className="bi bi-bell-fill"></i>
                      </div>
                    </div>
                    <small className="text-muted">
                      {metricas.clientesConAlertas} cliente{metricas.clientesConAlertas === 1 ? '' : 's'} siguiendo comunas
                    </small>
                  </Card.Body>
                </Card>
              </Col>
              <Col xs={12} sm={6} xl={3}>
                <Card className="stat-card border-0 shadow-sm h-100">
                  <Card.Body>
                    <div className="d-flex justify-content-between align-items-start">
                      <div>
                        <p className="stat-label mb-1">Resúmenes de novedades</p>
                        <h3 className="stat-value mb-0">{formatearNumero(metricas.totalResumenes)}</h3>
                      </div>
                      <div className="stat-icon bg-success bg-opacity-10 text-success">
                        <i className="bi bi-envelope-check-fill"></i>
                      </div>
                    </div>
                    <small className="text-muted">
                      {metricas.resumenesEnviados} correo{metricas.resumenesEnviados === 1 ? '' : 's'} enviado{metricas.resumenesEnviados === 1 ? '' : 's'}
                    </small>
                  </Card.Body>
                </Card>
              </Col>
            </Row>

            <Row className="g-4 mb-4">
              {/* Publicaciones por mes (ultimos 12 meses) */}
              <Col xs={12} lg={8}>
                <Card className="border-0 shadow-sm h-100">
                  <Card.Body>
                    <div className="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
                      <h5 className="mb-0"><i className="bi bi-bar-chart me-2"></i>Propiedades publicadas por mes</h5>
                      <Badge bg="light" text="secondary">
                        {totalUltimos12Meses} en los últimos 12 meses
                      </Badge>
                    </div>
                    {totalUltimos12Meses === 0 ? (
                      <p className="text-muted mb-0">
                        Ninguna propiedad del catálogo tiene fecha de publicación dentro de los últimos 12 meses.
                      </p>
                    ) : (
                      <div className="bar-chart">
                        {publicacionesPorMes.map((item, idx) => (
                          <div key={item.clave} className="bar-column">
                            <div className="bar-wrapper">
                              <div
                                className="bar"
                                style={{
                                  height: `${(item.cantidad / maxPublicaciones) * 100}%`,
                                  backgroundColor: idx === publicacionesPorMes.length - 1 ? '#a0523d' : '#6bb4b9'
                                }}
                              >
                                <span className="bar-value">{item.cantidad}</span>
                              </div>
                            </div>
                            <span className="bar-label">{item.etiqueta}</span>
                          </div>
                        ))}
                      </div>
                    )}
                  </Card.Body>
                </Card>
              </Col>

              {/* Distribucion por categoria */}
              <Col xs={12} lg={4}>
                <Card className="border-0 shadow-sm h-100">
                  <Card.Body>
                    <h5 className="mb-3"><i className="bi bi-pie-chart me-2"></i>Por categoría</h5>
                    {categorias.length === 0 ? (
                      <p className="text-muted mb-0">Todavía no hay propiedades en el catálogo.</p>
                    ) : (
                      categorias.map((cat) => (
                        <div key={cat.nombre} className="mb-3">
                          <div className="d-flex justify-content-between mb-1">
                            <span className="fw-medium">{cat.nombre}</span>
                            <span className="text-muted">{cat.cantidad} ({cat.porcentaje}%)</span>
                          </div>
                          <div className="progress" style={{ height: '10px', borderRadius: '5px' }}>
                            <div
                              className="progress-bar"
                              style={{
                                width: `${cat.porcentaje}%`,
                                backgroundColor: cat.color,
                                borderRadius: '5px'
                              }}
                            ></div>
                          </div>
                        </div>
                      ))
                    )}
                    <hr />
                    <div className="d-flex justify-content-between">
                      <span className="fw-bold">Venta</span>
                      <span><Badge bg="success">{propiedadesVenta}</Badge></span>
                    </div>
                    <div className="d-flex justify-content-between mt-1">
                      <span className="fw-bold">Arriendo</span>
                      <span><Badge bg="info">{propiedadesArriendo}</Badge></span>
                    </div>
                  </Card.Body>
                </Card>
              </Col>
            </Row>

            <Row className="g-4 mb-4">
              {/* Top comunas del catalogo */}
              <Col xs={12} md={6}>
                <Card className="border-0 shadow-sm h-100">
                  <Card.Body>
                    <h5 className="mb-3"><i className="bi bi-geo-alt me-2"></i>Comunas con más propiedades</h5>
                    {comunasTop.length === 0 ? (
                      <p className="text-muted mb-0">
                        Ninguna ubicación del catálogo coincide con las comunas reconocidas.
                      </p>
                    ) : (
                      comunasTop.map((comuna, idx) => (
                        <div key={comuna.nombre} className="d-flex align-items-center mb-3">
                          <span className="comuna-rank me-3">{idx + 1}</span>
                          <div className="flex-grow-1">
                            <div className="d-flex justify-content-between">
                              <span className="fw-medium">{comuna.nombre}</span>
                              <span className="text-muted">
                                {comuna.propiedades} propiedad{comuna.propiedades === 1 ? '' : 'es'}
                              </span>
                            </div>
                            <div className="progress mt-1" style={{ height: '6px', borderRadius: '3px' }}>
                              <div
                                className="progress-bar"
                                style={{
                                  width: `${(comuna.propiedades / maxComuna) * 100}%`,
                                  backgroundColor: '#a0523d',
                                  borderRadius: '3px'
                                }}
                              ></div>
                            </div>
                          </div>
                        </div>
                      ))
                    )}
                    {sinComuna > 0 && (
                      <small className="text-muted d-block mt-2">
                        {sinComuna} propiedad{sinComuna === 1 ? '' : 'es'} con una ubicación fuera de las comunas reconocidas.
                      </small>
                    )}
                  </Card.Body>
                </Card>
              </Col>

              {/* Precios reales del catalogo */}
              <Col xs={12} md={6}>
                <Card className="border-0 shadow-sm h-100">
                  <Card.Body>
                    <h5 className="mb-3"><i className="bi bi-cash-stack me-2"></i>Precios del catálogo</h5>
                    <div className="d-flex justify-content-between mb-2">
                      <span>Precio promedio en venta</span>
                      <span className="fw-semibold">{formatearMoneda(precioPromedioVenta)}</span>
                    </div>
                    <div className="d-flex justify-content-between mb-2">
                      <span>Precio promedio en arriendo</span>
                      <span className="fw-semibold">{formatearMoneda(precioPromedioArriendo)}</span>
                    </div>
                    <div className="d-flex justify-content-between mb-2">
                      <span>Valor del portafolio en venta</span>
                      <Badge bg="success">{formatearMoneda(valorPortafolioVenta)}</Badge>
                    </div>
                    <div className="d-flex justify-content-between mb-2">
                      <span>Propiedades en venta</span>
                      <Badge bg="success">{propiedadesVenta}</Badge>
                    </div>
                    <div className="d-flex justify-content-between">
                      <span>Propiedades en arriendo</span>
                      <Badge bg="warning" text="dark">{propiedadesArriendo}</Badge>
                    </div>
                    <hr />
                    <div className="text-center">
                      <p className="text-muted mb-1">Comisión estimada ({COMISION_ESTIMADA * 100}% del portafolio en venta)</p>
                      <h4 className="fw-bold mb-0" style={{ color: '#28a745' }}>
                        {formatearMoneda(comisionEstimada)}
                      </h4>
                      <small className="text-muted">Solo propiedades con precio informado</small>
                    </div>
                  </Card.Body>
                </Card>
              </Col>
            </Row>

            <Row className="g-4 mb-4">
              {/* Propiedades mas guardadas */}
              <Col xs={12} lg={7}>
                <Card className="border-0 shadow-sm h-100">
                  <Card.Body>
                    <h5 className="mb-3"><i className="bi bi-heart me-2"></i>Propiedades más guardadas</h5>
                    {topPropiedades.length === 0 ? (
                      <p className="text-muted mb-0">
                        Todavía no hay favoritos guardados. Cuando los clientes guarden propiedades van a aparecer acá.
                      </p>
                    ) : (
                      topPropiedades.map((propiedad) => (
                        <div key={propiedad.propiedadId} className="mb-3">
                          <div className="d-flex justify-content-between gap-2">
                            <span className="fw-medium text-truncate" title={propiedad.titulo}>
                              {propiedad.titulo || `Propiedad ${propiedad.propiedadId}`}
                            </span>
                            <span className="text-muted text-nowrap">
                              {propiedad.guardados} guardado{propiedad.guardados === 1 ? '' : 's'}
                            </span>
                          </div>
                          <div className="progress mt-1" style={{ height: '6px', borderRadius: '3px' }}>
                            <div
                              className="progress-bar"
                              style={{
                                width: `${((Number(propiedad.guardados) || 0) / maxGuardados) * 100}%`,
                                backgroundColor: '#c98b5f',
                                borderRadius: '3px'
                              }}
                            ></div>
                          </div>
                        </div>
                      ))
                    )}
                  </Card.Body>
                </Card>
              </Col>

              {/* Interes de los clientes por comuna */}
              <Col xs={12} lg={5}>
                <Card className="border-0 shadow-sm h-100">
                  <Card.Body>
                    <h5 className="mb-3"><i className="bi bi-people me-2"></i>Interés de los clientes</h5>
                    <p className="stat-label mb-2">Comunas con más favoritos</p>
                    {metricas.topComunasFavoritos.length === 0 ? (
                      <small className="text-muted d-block mb-3">Sin favoritos registrados.</small>
                    ) : (
                      metricas.topComunasFavoritos.map((comuna) => (
                        <div key={`fav-${comuna.comuna}`} className="mb-2">
                          <div className="d-flex justify-content-between">
                            <span>{comuna.comuna}</span>
                            <span className="text-muted">{comuna.guardados}</span>
                          </div>
                          <div className="progress" style={{ height: '5px', borderRadius: '3px' }}>
                            <div
                              className="progress-bar"
                              style={{
                                width: `${((Number(comuna.guardados) || 0) / maxComunaFavoritos) * 100}%`,
                                backgroundColor: '#a0523d',
                                borderRadius: '3px'
                              }}
                            ></div>
                          </div>
                        </div>
                      ))
                    )}
                    <hr />
                    <p className="stat-label mb-2">Comunas más seguidas en alertas</p>
                    {metricas.topComunasAlertas.length === 0 ? (
                      <small className="text-muted d-block">Sin alertas registradas.</small>
                    ) : (
                      metricas.topComunasAlertas.map((comuna) => (
                        <div key={`alerta-${comuna.comuna}`} className="mb-2">
                          <div className="d-flex justify-content-between">
                            <span>{comuna.comuna}</span>
                            <span className="text-muted">{comuna.seguidores}</span>
                          </div>
                          <div className="progress" style={{ height: '5px', borderRadius: '3px' }}>
                            <div
                              className="progress-bar"
                              style={{
                                width: `${((Number(comuna.seguidores) || 0) / maxComunaAlertas) * 100}%`,
                                backgroundColor: '#6bb4b9',
                                borderRadius: '3px'
                              }}
                            ></div>
                          </div>
                        </div>
                      ))
                    )}
                  </Card.Body>
                </Card>
              </Col>
            </Row>
          </>
        )}

        {/* Actividad reciente (auditoría real: quién y cuándo) */}
        <Card className="border-0 shadow-sm">
          <Card.Body>
            <h5 className="mb-3"><i className="bi bi-clock-history me-2"></i>Actividad Reciente</h5>
            {cargandoAuditoria ? (
              <div className="text-center py-4 text-muted">
                <Spinner animation="border" size="sm" className="me-2" />Cargando actividad...
              </div>
            ) : errorAuditoria ? (
              <p className="text-danger mb-0">{errorAuditoria}</p>
            ) : auditoria.length === 0 ? (
              <p className="text-muted mb-0">Aún no hay actividad registrada.</p>
            ) : (
              <div className="activity-list">
                {auditoria.map((item) => {
                  const accion = infoAccion(item.accion);
                  return (
                    <div key={item.id} className="activity-item d-flex align-items-start">
                      <div className={`activity-dot ${accion.color} me-3 mt-1`}></div>
                      <div className="flex-grow-1">
                        <div className="d-flex justify-content-between flex-wrap gap-2">
                          <span className="fw-medium">
                            <i className={`bi ${accion.icono} me-1`}></i>{accion.texto}
                          </span>
                          <small className="text-muted">
                            <i className="bi bi-calendar-event me-1"></i>{formatearFecha(item.fecha)}
                          </small>
                        </div>
                        <small className="text-muted d-block">
                          <i className="bi bi-building me-1"></i>{item.propiedad}
                        </small>
                        <small className="d-block mt-1">
                          <i className="bi bi-person-circle me-1"></i>
                          <span className="fw-medium">{item.usuario}</span>
                          {item.rol && (
                            <Badge bg={item.rol === 'ADMIN' ? 'primary' : 'info'} className="ms-2">
                              {item.rol === 'ADMIN' ? 'Admin' : 'Corredor'}
                            </Badge>
                          )}
                        </small>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </Card.Body>
        </Card>
      </Container>
    </div>
  );
}

export default Dashboard;
