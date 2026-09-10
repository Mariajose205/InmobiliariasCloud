import React, { useState, useEffect } from 'react';
import { Container, Row, Col, Card, Badge, Button, Spinner } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import { useMsal } from '@azure/msal-react';
import { obtenerRolCuenta } from '../msalConfig';
import { obtenerAuditoria } from '../api/apiClient';

const estadisticas = {
  totalPropiedades: 47,
  propiedadesVenta: 31,
  propiedadesArriendo: 16,
  visitasMes: 12480,
  visitasSemana: 3240,
  contactosRecibidos: 89,
  tasaConversion: 4.7,
  propiedadesPorCategoria: [
    { nombre: 'Casas', cantidad: 18, porcentaje: 38, color: '#a0523d' },
    { nombre: 'Departamentos', cantidad: 15, porcentaje: 32, color: '#6bb4b9' },
    { nombre: 'Terrenos', cantidad: 8, porcentaje: 17, color: '#2d3748' },
    { nombre: 'Oficinas', cantidad: 6, porcentaje: 13, color: '#718096' },
  ],
  ventasPorMes: [
    { mes: 'Ene', ventas: 8 },
    { mes: 'Feb', ventas: 12 },
    { mes: 'Mar', ventas: 10 },
    { mes: 'Abr', ventas: 15 },
    { mes: 'May', ventas: 18 },
    { mes: 'Jun', ventas: 14 },
    { mes: 'Jul', ventas: 20 },
    { mes: 'Ago', ventas: 16 },
    { mes: 'Sep', ventas: 22 },
    { mes: 'Oct', ventas: 19 },
    { mes: 'Nov', ventas: 25 },
    { mes: 'Dic', ventas: 21 },
  ],
  comunasTop: [
    { nombre: 'Las Condes', propiedades: 12 },
    { nombre: 'Vitacura', propiedades: 9 },
    { nombre: 'Providencia', propiedades: 7 },
    { nombre: 'Ñuñoa', propiedades: 6 },
    { nombre: 'Santiago Centro', propiedades: 5 },
  ],
  ingresosEstimados: 4250000,
  comisionPromedio: 3.2,
};

function Dashboard() {
  const { accounts, inProgress } = useMsal();
  const navigate = useNavigate();
  const [auditoria, setAuditoria] = useState([]);
  const [cargandoAuditoria, setCargandoAuditoria] = useState(true);
  const [errorAuditoria, setErrorAuditoria] = useState(null);
  const rol = obtenerRolCuenta(accounts[0]);
  const isAdmin = rol === 'admin';
  const isLoggedIn = accounts.length > 0;

  useEffect(() => {
    if (inProgress === 'none' && !isLoggedIn) {
      navigate('/login');
    }
  }, [inProgress, isLoggedIn, navigate]);

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

  const maxVentas = Math.max(...estadisticas.ventasPorMes.map(v => v.ventas));

  const formatearMoneda = (valor) => {
    return new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 }).format(valor);
  };

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
      if (isNaN(fecha.getTime())) return iso || '';
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
        <div className="d-flex justify-content-between align-items-center mb-4">
          <div>
            <h2 className="mb-1"><i className="bi bi-bar-chart-line-fill me-2"></i>Dashboard Administrativo</h2>
            <p className="text-muted mb-0">Estadísticas generales de la plataforma</p>
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

        {/* Tarjetas resumen */}
        <Row className="g-3 mb-4">
          <Col xs={12} sm={6} xl={3}>
            <Card className="stat-card border-0 shadow-sm">
              <Card.Body>
                <div className="d-flex justify-content-between align-items-start">
                  <div>
                    <p className="stat-label mb-1">Total Propiedades</p>
                    <h3 className="stat-value mb-0">{estadisticas.totalPropiedades}</h3>
                  </div>
                  <div className="stat-icon bg-primary bg-opacity-10 text-primary">
                    <i className="bi bi-building"></i>
                  </div>
                </div>
                <small className="text-success"><i className="bi bi-arrow-up"></i> +3 este mes</small>
              </Card.Body>
            </Card>
          </Col>
          <Col xs={12} sm={6} xl={3}>
            <Card className="stat-card border-0 shadow-sm">
              <Card.Body>
                <div className="d-flex justify-content-between align-items-start">
                  <div>
                    <p className="stat-label mb-1">Visitas del Mes</p>
                    <h3 className="stat-value mb-0">{estadisticas.visitasMes.toLocaleString('es-CL')}</h3>
                  </div>
                  <div className="stat-icon bg-success bg-opacity-10 text-success">
                    <i className="bi bi-eye"></i>
                  </div>
                </div>
                <small className="text-success"><i className="bi bi-arrow-up"></i> +12% vs mes anterior</small>
              </Card.Body>
            </Card>
          </Col>
          <Col xs={12} sm={6} xl={3}>
            <Card className="stat-card border-0 shadow-sm">
              <Card.Body>
                <div className="d-flex justify-content-between align-items-start">
                  <div>
                    <p className="stat-label mb-1">Contactos Recibidos</p>
                    <h3 className="stat-value mb-0">{estadisticas.contactosRecibidos}</h3>
                  </div>
                  <div className="stat-icon bg-warning bg-opacity-10 text-warning">
                    <i className="bi bi-envelope-check"></i>
                  </div>
                </div>
                <small className="text-success"><i className="bi bi-arrow-up"></i> +8 esta semana</small>
              </Card.Body>
            </Card>
          </Col>
          <Col xs={12} sm={6} xl={3}>
            <Card className="stat-card border-0 shadow-sm">
              <Card.Body>
                <div className="d-flex justify-content-between align-items-start">
                  <div>
                    <p className="stat-label mb-1">Tasa de Conversión</p>
                    <h3 className="stat-value mb-0">{estadisticas.tasaConversion}%</h3>
                  </div>
                  <div className="stat-icon bg-danger bg-opacity-10 text-danger">
                    <i className="bi bi-graph-up-arrow"></i>
                  </div>
                </div>
                <small className="text-muted">Contactos / Visitas</small>
              </Card.Body>
            </Card>
          </Col>
        </Row>

        <Row className="g-4 mb-4">
          {/* Gráfico de barras - Ventas por mes */}
          <Col xs={12} lg={8}>
            <Card className="border-0 shadow-sm h-100">
              <Card.Body>
                <h5 className="mb-3"><i className="bi bi-bar-chart me-2"></i>Ventas por Mes (2026)</h5>
                <div className="bar-chart">
                  {estadisticas.ventasPorMes.map((item, idx) => (
                    <div key={idx} className="bar-column">
                      <div className="bar-wrapper">
                        <div
                          className="bar"
                          style={{
                            height: `${(item.ventas / maxVentas) * 100}%`,
                            backgroundColor: idx === new Date().getMonth() ? '#a0523d' : '#6bb4b9',
                          }}
                        >
                          <span className="bar-value">{item.ventas}</span>
                        </div>
                      </div>
                      <span className="bar-label">{item.mes}</span>
                    </div>
                  ))}
                </div>
              </Card.Body>
            </Card>
          </Col>

          {/* Distribución por categoría */}
          <Col xs={12} lg={4}>
            <Card className="border-0 shadow-sm h-100">
              <Card.Body>
                <h5 className="mb-3"><i className="bi bi-pie-chart me-2"></i>Por Categoría</h5>
                {estadisticas.propiedadesPorCategoria.map((cat, idx) => (
                  <div key={idx} className="mb-3">
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
                          borderRadius: '5px',
                        }}
                      ></div>
                    </div>
                  </div>
                ))}
                <hr />
                <div className="d-flex justify-content-between">
                  <span className="fw-bold">Venta</span>
                  <span><Badge bg="success">{estadisticas.propiedadesVenta}</Badge></span>
                </div>
                <div className="d-flex justify-content-between mt-1">
                  <span className="fw-bold">Arriendo</span>
                  <span><Badge bg="info">{estadisticas.propiedadesArriendo}</Badge></span>
                </div>
              </Card.Body>
            </Card>
          </Col>
        </Row>

        <Row className="g-4 mb-4">
          {/* Top comunas */}
          <Col xs={12} md={6}>
            <Card className="border-0 shadow-sm h-100">
              <Card.Body>
                <h5 className="mb-3"><i className="bi bi-geo-alt me-2"></i>Top Comunas</h5>
                {estadisticas.comunasTop.map((comuna, idx) => (
                  <div key={idx} className="d-flex align-items-center mb-3">
                    <span className="comuna-rank me-3">{idx + 1}</span>
                    <div className="flex-grow-1">
                      <div className="d-flex justify-content-between">
                        <span className="fw-medium">{comuna.nombre}</span>
                        <span className="text-muted">{comuna.propiedades} propiedades</span>
                      </div>
                      <div className="progress mt-1" style={{ height: '6px', borderRadius: '3px' }}>
                        <div
                          className="progress-bar"
                          style={{
                            width: `${(comuna.propiedades / estadisticas.comunasTop[0].propiedades) * 100}%`,
                            backgroundColor: '#a0523d',
                            borderRadius: '3px',
                          }}
                        ></div>
                      </div>
                    </div>
                  </div>
                ))}
              </Card.Body>
            </Card>
          </Col>

          {/* Ingresos */}
          <Col xs={12} md={6}>
            <Card className="border-0 shadow-sm h-100">
              <Card.Body>
                <h5 className="mb-3"><i className="bi bi-cash-stack me-2"></i>Resumen Financiero</h5>
                <div className="text-center py-3">
                  <p className="text-muted mb-1">Ingresos Estimados (Comisiones)</p>
                  <h2 className="fw-bold" style={{ color: '#28a745' }}>{formatearMoneda(estadisticas.ingresosEstimados)}</h2>
                  <small className="text-muted">Mes actual proyectado</small>
                </div>
                <hr />
                <div className="d-flex justify-content-between mb-2">
                  <span>Comisión promedio</span>
                  <Badge bg="secondary">{estadisticas.comisionPromedio}%</Badge>
                </div>
                <div className="d-flex justify-content-between mb-2">
                  <span>Visitas esta semana</span>
                  <Badge bg="info">{estadisticas.visitasSemana.toLocaleString('es-CL')}</Badge>
                </div>
                <div className="d-flex justify-content-between mb-2">
                  <span>Propiedades en venta</span>
                  <Badge bg="success">{estadisticas.propiedadesVenta}</Badge>
                </div>
                <div className="d-flex justify-content-between">
                  <span>Propiedades en arriendo</span>
                  <Badge bg="warning">{estadisticas.propiedadesArriendo}</Badge>
                </div>
              </Card.Body>
            </Card>
          </Col>
        </Row>

        {/* Actividad reciente (auditoría real: quien y cuando) */}
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
