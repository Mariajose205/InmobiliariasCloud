import React, { useState, useEffect } from 'react';
import { Container, Row, Col, Card, Form, Button, Badge, Alert } from 'react-bootstrap';

const PROPIEDADES_KEY = 'inmobiliaria_propiedades';

function Admin() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [propiedades, setPropiedades] = useState([]);
  const [editingIndex, setEditingIndex] = useState('');
  const [formData, setFormData] = useState({
    titulo: '',
    categoria: '',
    tipoOperacion: 'Venta',
    precio: '',
    tipoPrecio: '/ Valor Total',
    gastosComunes: '0',
    ubicacion: '',
    piezas: '0',
    banos: '0',
    estacionamiento: '0',
    bodega: '0',
    videoUrl: ''
  });
  const [archivosFotos, setArchivosFotos] = useState([]);
  const [previewFotos, setPreviewFotos] = useState([]);
  const [loading, setLoading] = useState(false);

  const CLAVE_CORRECTA = "Duoc2026";

  useEffect(() => {
    const session = sessionStorage.getItem('admin_session');
    if (session === 'active') {
      setIsAuthenticated(true);
      cargarPropiedades();
    }
  }, []);

  const verificarAcceso = () => {
    if (password === CLAVE_CORRECTA) {
      setIsAuthenticated(true);
      sessionStorage.setItem('admin_session', 'active');
      setError('');
      cargarPropiedades();
    } else {
      setError('Clave incorrecta. Inténtalo de nuevo.');
      setPassword('');
    }
  };

  const cerrarSesion = () => {
    setIsAuthenticated(false);
    sessionStorage.removeItem('admin_session');
    setPassword('');
  };

  const cargarPropiedades = () => {
    try {
      const propiedadesGuardadas = localStorage.getItem(PROPIEDADES_KEY);
      let propiedadesData = [];

      if (propiedadesGuardadas) {
        propiedadesData = JSON.parse(propiedadesGuardadas);
      }

      setPropiedades(propiedadesData);
    } catch (err) {
      console.error("Error al cargar propiedades:", err);
    }
  };

  const handleInputChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleFileChange = (e) => {
    const files = Array.from(e.target.files);
    if (files.length === 0) return;

    const nuevosArchivos = [...archivosFotos, ...files];
    setArchivosFotos(nuevosArchivos);

    // Crear previews
    files.forEach(file => {
      const reader = new FileReader();
      reader.onloadend = () => {
        setPreviewFotos(prev => [...prev, reader.result]);
      };
      reader.readAsDataURL(file);
    });

    e.target.value = "";
  };

  const eliminarPreview = (index) => {
    const nuevosArchivos = archivosFotos.filter((_, i) => i !== index);
    const nuevosPreviews = previewFotos.filter((_, i) => i !== index);
    setArchivosFotos(nuevosArchivos);
    setPreviewFotos(nuevosPreviews);
  };

  const convertirArchivosABase64 = async (archivos) => {
    const promesas = archivos.map(archivo => {
      return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onloadend = () => resolve(reader.result);
        reader.onerror = reject;
        reader.readAsDataURL(archivo);
      });
    });
    return Promise.all(promesas);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);

    try {
      // Validar que haya al menos una imagen para nuevas propiedades
      if (editingIndex === "" && archivosFotos.length === 0) {
        alert("Por favor, selecciona al menos una imagen para la propiedad.");
        setLoading(false);
        return;
      }

      let imagenesFinales = [];

      if (editingIndex !== "") {
        // Mantener imágenes existentes al editar
        imagenesFinales = [...propiedades[editingIndex].imagenes];
      }

      // Convertir nuevos archivos a base64
      if (archivosFotos.length > 0) {
        const nuevasImagenesBase64 = await convertirArchivosABase64(archivosFotos);
        imagenesFinales = [...imagenesFinales, ...nuevasImagenesBase64];
      }

      const nuevaPropiedad = {
        id: editingIndex !== '' ? propiedades[editingIndex].id : Date.now(),
        titulo: formData.titulo,
        categoria: formData.categoria,
        precio: parseInt(formData.precio) || 0,
        tipoPrecio: formData.tipoPrecio,
        ubicacion: formData.ubicacion,
        piezas: parseInt(formData.piezas) || 0,
        banos: parseInt(formData.banos) || 0,
        imagenes: imagenesFinales,
        tipoOperacion: formData.tipoOperacion,
        gastosComunes: parseInt(formData.gastosComunes) || 0,
        estacionamiento: parseInt(formData.estacionamiento) || 0,
        bodega: parseInt(formData.bodega) || 0,
        videoUrl: formData.videoUrl
      };

      let nuevasPropiedades;
      if (editingIndex === "") {
        nuevasPropiedades = [nuevaPropiedad, ...propiedades];
        alert("¡Éxito! Propiedad publicada correctamente.");
      } else {
        nuevasPropiedades = [...propiedades];
        nuevasPropiedades[editingIndex] = nuevaPropiedad;
        alert("¡Éxito! Propiedad actualizada correctamente.");
      }

      localStorage.setItem(PROPIEDADES_KEY, JSON.stringify(nuevasPropiedades));
      setPropiedades(nuevasPropiedades);
      resetFormulario();

    } catch (error) {
      console.error("Error al guardar:", error);
      alert("Ocurrió un error al guardar: " + (error.message || error));
    } finally {
      setLoading(false);
    }
  };

  const eliminarPropiedad = (idx) => {
    const target = propiedades[idx];
    if (confirm(`¿Estás seguro de eliminar permanentemente "${target.titulo}"?`)) {
      try {
        const nuevasPropiedades = propiedades.filter((_, i) => i !== idx);
        localStorage.setItem(PROPIEDADES_KEY, JSON.stringify(nuevasPropiedades));
        setPropiedades(nuevasPropiedades);
        alert("Eliminado con éxito.");
      } catch (err) {
        alert("No se pudo eliminar: " + err.message);
      }
    }
  };

  const prepararEditar = (idx) => {
    const p = propiedades[idx];
    setEditingIndex(idx);
    setFormData({
      titulo: p.titulo,
      categoria: p.categoria || "Casa",
      tipoOperacion: p.tipoOperacion || "Venta",
      precio: p.precio,
      tipoPrecio: p.tipoPrecio || "/ Valor Total",
      gastosComunes: p.gastosComunes || 0,
      ubicacion: p.ubicacion,
      piezas: p.piezas,
      banos: p.banos,
      estacionamiento: p.estacionamiento,
      bodega: p.bodega,
      videoUrl: p.videoUrl || ""
    });

    // Configurar previews de imágenes existentes
    setArchivosFotos([]);
    setPreviewFotos(p.imagenes || []);

    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const resetFormulario = () => {
    setFormData({
      titulo: '',
      categoria: '',
      tipoOperacion: 'Venta',
      precio: '',
      tipoPrecio: '/ Valor Total',
      gastosComunes: '0',
      ubicacion: '',
      piezas: '0',
      banos: '0',
      estacionamiento: '0',
      bodega: '0',
      videoUrl: ''
    });
    setArchivosFotos([]);
    setPreviewFotos([]);
    setEditingIndex('');
  };

  const formatearPrecio = (precio) => {
    return new Intl.NumberFormat('es-CL').format(precio);
  };

  if (!isAuthenticated) {
    return (
      <Container style={{ maxWidth: '400px', marginTop: '100px' }}>
        <Card className="shadow-sm p-4">
          <h3 className="text-center mb-4">🔑 Panel Administrativo</h3>
          <Form.Group className="mb-3">
            <Form.Label>Contraseña de Acceso</Form.Label>
            <Form.Control
              type="password"
              placeholder="Introduce la clave..."
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              onKeyPress={(e) => e.key === 'Enter' && verificarAcceso()}
            />
          </Form.Group>
          {error && <Alert variant="danger" className="small mt-2">{error}</Alert>}
          <Button className="btn-primary w-100" onClick={verificarAcceso}>Ingresar</Button>
        </Card>
      </Container>
    );
  }

  return (
    <Container className="my-5">
      <div className="d-flex justify-content-between align-items-center mb-4">
        <h2>🏢 Gestión de Catálogo de Propiedades</h2>
        <Button variant="danger" size="sm" onClick={cerrarSesion}>🔒 Cerrar Sesión</Button>
      </div>

      <Row className="g-4">
        <Col xs={12} lg={5}>
          <Card className="p-4 shadow-sm">
            <h4 className="mb-3">{editingIndex !== '' ? '✏️ Editando Propiedad' : '➕ Añadir Nueva Propiedad'}</h4>
            <Form onSubmit={handleSubmit}>
              <Form.Group className="mb-3">
                <Form.Label>Título del Proyecto/Propiedad</Form.Label>
                <Form.Control
                  type="text"
                  name="titulo"
                  value={formData.titulo}
                  onChange={handleInputChange}
                  placeholder="Ej: Hermosa Casa en Vitacura"
                  required
                />
              </Form.Group>

              <Row>
                <Col xs={6} className="mb-3">
                  <Form.Label>Categoría</Form.Label>
                  <Form.Select
                    name="categoria"
                    value={formData.categoria}
                    onChange={handleInputChange}
                    required
                  >
                    <option value="">Selecciona...</option>
                    <option value="Casa">🏡 Casa</option>
                    <option value="Departamento">🏢 Departamento</option>
                    <option value="Terreno">🌱 Terreno</option>
                  </Form.Select>
                </Col>
                <Col xs={6} className="mb-3">
                  <Form.Label>Operación</Form.Label>
                  <Form.Select
                    name="tipoOperacion"
                    value={formData.tipoOperacion}
                    onChange={handleInputChange}
                    required
                  >
                    <option value="Venta">💰 Venta</option>
                    <option value="Arriendo">🔑 Arriendo</option>
                  </Form.Select>
                </Col>
              </Row>

              <Row>
                <Col xs={7} className="mb-3">
                  <Form.Label>Precio (Número neto)</Form.Label>
                  <Form.Control
                    type="number"
                    name="precio"
                    value={formData.precio}
                    onChange={handleInputChange}
                    placeholder="Ej: 145000000"
                    required
                  />
                </Col>
                <Col xs={5} className="mb-3">
                  <Form.Label>Sufijo Precio</Form.Label>
                  <Form.Select
                    name="tipoPrecio"
                    value={formData.tipoPrecio}
                    onChange={handleInputChange}
                  >
                    <option value="/ Valor Total">/ Valor Total</option>
                    <option value="UF">UF</option>
                    <option value="/ Mensual">/ Mensual</option>
                  </Form.Select>
                </Col>
              </Row>

              <Form.Group className="mb-3">
                <Form.Label>Gastos Comunes (Opcional)</Form.Label>
                <Form.Control
                  type="number"
                  name="gastosComunes"
                  value={formData.gastosComunes}
                  onChange={handleInputChange}
                  placeholder="Ej: 120000 (0 si no aplica)"
                />
              </Form.Group>

              <Form.Group className="mb-3">
                <Form.Label>📍 Ubicación / Comuna</Form.Label>
                <Form.Control
                  type="text"
                  name="ubicacion"
                  value={formData.ubicacion}
                  onChange={handleInputChange}
                  placeholder="Ej: Las Condes, Santiago"
                  required
                />
              </Form.Group>

              <Row>
                <Col xs={3} className="mb-3">
                  <Form.Label>🛏️ Dorm.</Form.Label>
                  <Form.Control
                    type="number"
                    name="piezas"
                    value={formData.piezas}
                    onChange={handleInputChange}
                    min="0"
                  />
                </Col>
                <Col xs={3} className="mb-3">
                  <Form.Label>🚿 Baños</Form.Label>
                  <Form.Control
                    type="number"
                    name="banos"
                    value={formData.banos}
                    onChange={handleInputChange}
                    min="0"
                  />
                </Col>
                <Col xs={3} className="mb-3">
                  <Form.Label>🚗 Estac.</Form.Label>
                  <Form.Control
                    type="number"
                    name="estacionamiento"
                    value={formData.estacionamiento}
                    onChange={handleInputChange}
                    min="0"
                  />
                </Col>
                <Col xs={3} className="mb-3">
                  <Form.Label>📦 Bodega</Form.Label>
                  <Form.Control
                    type="number"
                    name="bodega"
                    value={formData.bodega}
                    onChange={handleInputChange}
                    min="0"
                  />
                </Col>
              </Row>

              <Form.Group className="mb-3">
                <Form.Label>🔗 Video / Tour Virtual (Link Opcional)</Form.Label>
                <Form.Control
                  type="url"
                  name="videoUrl"
                  value={formData.videoUrl}
                  onChange={handleInputChange}
                  placeholder="https://www.youtube.com/watch?v=..."
                />
              </Form.Group>

              <Form.Group className="mb-3">
                <Form.Label>📸 Imágenes de la Propiedad</Form.Label>
                <Form.Control
                  type="file"
                  multiple
                  accept="image/*"
                  onChange={handleFileChange}
                />
                <Form.Text>Selecciona una o más imágenes desde tu computadora</Form.Text>
              </Form.Group>

              {previewFotos.length > 0 && (
                <div className="mb-3">
                  <Form.Label>Previsualización de Imágenes</Form.Label>
                  <div style={{
                    display: 'flex',
                    flexWrap: 'wrap',
                    gap: '10px',
                    marginTop: '10px'
                  }}>
                    {previewFotos.map((preview, index) => (
                      <div key={index} style={{
                        position: 'relative',
                        width: '100px',
                        height: '100px',
                        borderRadius: '8px',
                        overflow: 'hidden',
                        boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
                      }}>
                        <img
                          src={preview}
                          alt={`Preview ${index}`}
                          style={{
                            width: '100%',
                            height: '100%',
                            objectFit: 'cover'
                          }}
                        />
                        <button
                          type="button"
                          onClick={() => eliminarPreview(index)}
                          style={{
                            position: 'absolute',
                            top: '5px',
                            right: '5px',
                            background: 'rgba(255, 0, 0, 0.8)',
                            color: 'white',
                            border: 'none',
                            borderRadius: '50%',
                            width: '24px',
                            height: '24px',
                            cursor: 'pointer',
                            fontSize: '14px',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center'
                          }}
                        >
                          ×
                        </button>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              <div className="d-flex gap-2">
                <Button type="submit" variant="success" className="flex-grow-1" disabled={loading}>
                  {loading ? '⏳ Procesando...' : `💾 ${editingIndex !== '' ? 'Actualizar' : 'Publicar'} Proyecto`}
                </Button>
                {editingIndex !== '' && (
                  <Button variant="secondary" onClick={resetFormulario}>Cancelar</Button>
                )}
              </div>
            </Form>
          </Card>
        </Col>

        <Col xs={12} lg={7}>
          <Card className="p-4 shadow-sm">
            <h4 className="mb-3">📋 Propiedades en Línea</h4>
            <div>
              {propiedades.length === 0 ? (
                <p className="text-muted">No hay propiedades guardadas.</p>
              ) : (
                propiedades.map((p, idx) => {
                  const sufijo = p.tipoPrecio || '/ Total';
                  const badgeOp = p.tipoOperacion === "Arriendo" ? '🔑 Arriendo' : '💰 Venta';
                  
                  return (
                    <div key={p.id} style={{
                      background: 'white',
                      padding: '15px',
                      borderRadius: '8px',
                      marginBottom: '12px',
                      boxShadow: '0 2px 4px rgba(0,0,0,0.05)',
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center'
                    }}>
                      <div>
                        <strong style={{ fontSize: '1.1em', color: '#111' }}>{p.titulo}</strong>{' '}
                        <Badge bg="secondary" className="ms-1">{p.categoria}</Badge>
                        <Badge bg="success" className="ms-1">{badgeOp}</Badge>
                        <br />
                        <span style={{ color: '#28a745', fontWeight: 'bold' }}>
                          ${formatearPrecio(p.precio)} {sufijo}
                        </span>
                        <br />
                        <small style={{ color: '#666' }}>
                          📍 {p.ubicacion} | 🛏️ {p.piezas} dorm | 🚿 {p.banos} baños | 🚗 {p.estacionamiento} estac. | 📦 {p.bodega} bodega
                          {p.gastosComunes > 0 && ` | 💸 GGCC: $${formatearPrecio(p.gastosComunes)}`}
                          {p.videoUrl && ` | 🎥 Tiene Video`}
                        </small>
                      </div>
                      <div>
                        <Button 
                          variant="warning" 
                          size="sm" 
                          onClick={() => prepararEditar(idx)}
                          className="me-1"
                        >
                          ✏️ Editar
                        </Button>
                        <Button 
                          variant="danger" 
                          size="sm" 
                          onClick={() => eliminarPropiedad(idx)}
                        >
                          ❌ Borrar
                        </Button>
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </Card>
        </Col>
      </Row>
    </Container>
  );
}

export default Admin;
