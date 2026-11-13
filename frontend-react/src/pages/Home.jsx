import React, { useState, useEffect, useRef } from 'react';
import { Container, Row, Col, Form, Alert, Button } from 'react-bootstrap';
import { Link, useLocation } from 'react-router-dom';
import { useMsal } from '@azure/msal-react';
import Navbar from '../components/Navbar';
import HeroSection from '../components/HeroSection';
import PropertyCatalog from '../components/PropertyCatalog';
import Footer from '../components/Footer';
import ImageModal from '../components/ImageModal';
import WhatsAppButton from '../components/WhatsAppButton';
import { haySesion } from '../msalConfig';
import {
  obtenerPropiedadesPublicas,
  obtenerFavoritos,
  obtenerNovedades,
  descartarNovedad,
  extraerComunaLocal,
  COMUNAS
} from '../api/apiClient';
import '../App.css';

function Home() {
  const { accounts } = useMsal();
  const isLoggedIn = accounts.length > 0 || haySesion();
  const location = useLocation();

  const [propiedades, setPropiedades] = useState([]);
  const [todasLasPropiedades, setTodasLasPropiedades] = useState([]);
  const [filtro, setFiltro] = useState('Todos');
  const [filtroComuna, setFiltroComuna] = useState('Todas');
  const [modalImage, setModalImage] = useState(null);

  // Los links "Propiedades" del navbar navegan a /#propiedades desde
  // cualquier pagina. React Router no hace scroll automatico en una SPA,
  // asi que al montar (o al cambiar el hash) se baja hasta la seccion.
  // El timeout corto espera a que el catalogo termine de renderizarse.
  useEffect(() => {
    const hash = location.hash; // "#propiedades" o "#contacto"
    if (!hash) return undefined;
    const timer = setTimeout(() => {
      const el = document.querySelector(hash);
      if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 300);
    return () => clearTimeout(timer);
  }, [location.hash]);

  // Contador de favoritos.
  const [totalGuardadas, setTotalGuardadas] = useState(0);

  // Resumenes (digests) que vienen del backend (GET /api/wishlist/novedades).
  // El backend agrupa las propiedades nuevas: un solo resumen cada 6
  // propiedades (o el barrido diario). Aqui se muestra UNA alerta por
  // resumen, con el detalle de sus propiedades dentro, en lugar de una
  // alerta por propiedad.
  const [resumenes, setResumenes] = useState([]);
  // Resumenes descartados en esta sesion: evita que reaparezcan mientras
  // el DELETE viaja al backend y el polling vuelve a consultar.
  const descartadosRef = useRef(new Set());

  // Refs para el polling (evitan recrear el intervalo con cierres obsoletos)
  const filtroRef = useRef(filtro);
  const filtroComunaRef = useRef(filtroComuna);
  useEffect(() => { filtroRef.current = filtro; }, [filtro]);
  useEffect(() => { filtroComunaRef.current = filtroComuna; }, [filtroComuna]);

  const normalizar = (valor) => (valor || '').toString().trim().toLowerCase();
  const normalizarComuna = (valor) => (valor || '').toString().trim().toLowerCase();

  // Filtra combinando categoria + comuna (extraerComunaLocal)
  const aplicarFiltros = (lista, catSeleccionada, comunaSeleccionada) => {
    const objetivo = normalizar(catSeleccionada);
    const comunaObjetivo = normalizarComuna(comunaSeleccionada);
    return (lista || []).filter((p) => {
      const okCategoria = objetivo === '' || objetivo === 'todos' || normalizar(p.categoria) === objetivo;
      const okComuna =
        !comunaObjetivo ||
        comunaObjetivo === 'todas' ||
        normalizarComuna(extraerComunaLocal(p.ubicacion)) === comunaObjetivo;
      return okCategoria && okComuna;
    });
  };

  const cargarPropiedades = async () => {
    try {
      const propiedadesData = await obtenerPropiedadesPublicas();
      const lista = propiedadesData || [];
      setTodasLasPropiedades(lista);
      setPropiedades(aplicarFiltros(lista, filtroRef.current, filtroComunaRef.current));
    } catch (error) {
      console.error("Error al cargar propiedades:", error);
      setTodasLasPropiedades([]);
      setPropiedades([]);
    }
  };

  const filtrarCategoria = (catSeleccionada) => {
    setFiltro(catSeleccionada);
    setPropiedades(aplicarFiltros(todasLasPropiedades, catSeleccionada, filtroComuna));
  };

  const handleFiltroComuna = (comunaSeleccionada) => {
    setFiltroComuna(comunaSeleccionada);
    setPropiedades(aplicarFiltros(todasLasPropiedades, filtro, comunaSeleccionada));
  };

  const scrollAlCatalogo = () => {
    const el = document.getElementById('propiedades');
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  const cargarIntereses = async () => {
    if (!isLoggedIn) {
      setTotalGuardadas(0);
      return;
    }
    try {
      const favoritosData = await obtenerFavoritos();
      const favoritos = Array.isArray(favoritosData)
        ? favoritosData
        : (favoritosData?.favoritos || []);
      setTotalGuardadas(favoritos.length);
    } catch (_) {
      // Silencioso: sin favoritos no se muestra el contador.
      setTotalGuardadas(0);
    }
  };

  // Convierte una propiedad del resumen al formato interno de la alerta.
  const normalizarItem = (item) => {
    if (!item) return null;
    const propiedadId = item.propiedadId ?? item.id ?? null;
    const titulo = item.titulo ?? item.mensaje ?? null;
    if (propiedadId == null && !titulo) return null;
    return {
      propiedadId,
      titulo,
      comuna: item.comuna || extraerComunaLocal(item.ubicacion) || '',
      precio: item.precio ?? null,
      tipoPrecio: item.tipoPrecio || '',
      ubicacion: item.ubicacion || ''
    };
  };

  // Normaliza la respuesta del backend. Formato nuevo: cada elemento es un
  // resumen con `items`. Formato antiguo (backend sin desplegar): cada
  // elemento es una novedad individual; se agrupan todas en UN solo resumen
  // sintetico para no volver a mostrar una alerta por propiedad.
  const normalizarResumenes = (datos) => {
    const lista = Array.isArray(datos) ? datos : (datos?.resumenes || datos?.novedades || []);
    const normalizados = [];
    const legado = [];

    lista.forEach((n) => {
      if (!n) return;
      if (Array.isArray(n.items) && n.items.length > 0) {
        const items = n.items.map(normalizarItem).filter(Boolean);
        if (items.length === 0) return;
        normalizados.push({
          clave: `resumen-${n.id}`,
          id: n.id ?? null,
          cantidad: n.cantidad ?? items.length,
          comunas: Array.isArray(n.comunas)
            ? n.comunas.filter(Boolean)
            : [...new Set(items.map((i) => i.comuna).filter(Boolean))],
          titulo: n.titulo || null,
          fechaCreacion: n.fechaCreacion || null,
          items,
          legadoIds: []
        });
      } else if (normalizarItem(n)) {
        legado.push({ id: n.id ?? null, item: normalizarItem(n) });
      }
    });

    if (legado.length > 0) {
      const items = legado.map((l) => l.item);
      normalizados.push({
        clave: 'legado',
        id: null,
        cantidad: items.length,
        comunas: [...new Set(items.map((i) => i.comuna).filter(Boolean))],
        titulo: null,
        fechaCreacion: null,
        items,
        legadoIds: legado.map((l) => l.id).filter((id) => id != null)
      });
    }

    return normalizados;
  };

  const cargarNovedadesBackend = async () => {
    if (!isLoggedIn) return;
    try {
      const datos = await obtenerNovedades();
      const lista = normalizarResumenes(datos)
        .filter((r) => !descartadosRef.current.has(r.clave));
      setResumenes(lista);
    } catch (_) {
      // Silencioso: el polling de novedades no debe romper la pagina.
    }
  };

  // Carga inicial de propiedades
  useEffect(() => {
    cargarPropiedades();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Carga de favoritos + resumenes (solo con sesion)
  useEffect(() => {
    cargarIntereses();
    cargarNovedadesBackend();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoggedIn]);

  // Cuando cambia el login, limpia resumenes viejos
  useEffect(() => {
    if (!isLoggedIn) {
      setResumenes([]);
      descartadosRef.current = new Set();
    }
  }, [isLoggedIn]);

  // Polling: resumenes del backend + refresco del catalogo cada 15s.
  // Ya NO se crean alertas locales por cada propiedad nueva del catalogo:
  // las novedades solo llegan agrupadas desde el backend para no saturar.
  useEffect(() => {
    if (!isLoggedIn) return undefined;
    const intervalo = setInterval(async () => {
      await cargarNovedadesBackend();
      try {
        const datos = await obtenerPropiedadesPublicas();
        const lista = datos || [];
        // Refresca el catalogo en segundo plano sin romper filtros aplicados
        setTodasLasPropiedades(lista);
        setPropiedades(aplicarFiltros(lista, filtroRef.current, filtroComunaRef.current));
      } catch (_) {
        // Silencioso: el polling no debe romper la pagina.
      }
      await cargarIntereses();
    }, 15000);
    return () => clearInterval(intervalo);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoggedIn]);

  const descartarResumen = async (resumen) => {
    descartadosRef.current.add(resumen.clave);
    setResumenes((prev) => prev.filter((r) => r.clave !== resumen.clave));
    if (!isLoggedIn) return;
    try {
      if (resumen.id != null) {
        await descartarNovedad(resumen.id);
      } else {
        // Formato antiguo: se borra cada novedad que compone el grupo.
        for (const id of resumen.legadoIds) {
          await descartarNovedad(id);
        }
      }
    } catch (_) {
      // Silencioso: si falla, el resumen reaparece en el proximo polling.
    }
  };

  const tituloResumen = (resumen) => {
    if (resumen.titulo) return resumen.titulo;
    const donde = resumen.comunas?.length ? resumen.comunas.join(', ') : 'tus comunas';
    return resumen.cantidad === 1
      ? `1 nueva propiedad en ${donde}`
      : `${resumen.cantidad} nuevas propiedades en ${donde}`;
  };

  const precioTexto = (item) => {
    if (item?.precio === null || item?.precio === undefined || item?.precio === '') return '';
    const numero = Number(item.precio);
    const base = Number.isNaN(numero) ? String(item.precio) : numero.toLocaleString('es-CL');
    return item.tipoPrecio ? `${base} ${item.tipoPrecio}` : base;
  };

  return (
    <div className="App">
      <Navbar />
      <HeroSection />

      <Container className="mb-3">
        {resumenes.length > 0 && (
          <Alert variant="success" className="alert-novedad mb-2">
            <i className="bi bi-bell-fill me-2"></i>
            ¡Tienes {resumenes.length} {resumenes.length === 1 ? 'resumen' : 'resumenes'} de novedades en tus comunas!
          </Alert>
        )}
        {resumenes.map((resumen) => (
          <Alert
            key={resumen.clave}
            variant="success"
            dismissible
            className="alert-novedad"
            onClose={() => descartarResumen(resumen)}
          >
            <div className="d-flex align-items-center justify-content-between">
              <span className="fw-semibold">
                <i className="bi bi-bell-fill me-2"></i>
                {tituloResumen(resumen)}
              </span>
              <Button
                size="sm"
                variant="success"
                className="ms-3 flex-shrink-0"
                onClick={scrollAlCatalogo}
              >
                Ver
              </Button>
            </div>
            <ul className="mb-0 mt-2 ps-4">
              {resumen.items.map((item, indice) => (
                <li key={`${resumen.clave}-${item.propiedadId ?? indice}`}>
                  {item.titulo || 'Propiedad'}
                  {item.comuna ? ` (${item.comuna})` : ''}
                  {precioTexto(item) ? ` - $${precioTexto(item)}` : ''}
                  {item.ubicacion ? ` - ${item.ubicacion}` : ''}
                </li>
              ))}
            </ul>
          </Alert>
        ))}

        {isLoggedIn && totalGuardadas > 0 && (
          <Alert variant="info" className="alert-novedad d-flex align-items-center justify-content-between">
            <span>
              <i className="bi bi-heart-fill me-2"></i>
              Tienes {totalGuardadas} guardadas
            </span>
            <Button as={Link} to="/wishlist" size="sm" variant="info" className="ms-3 flex-shrink-0">
              Ver wishlist
            </Button>
          </Alert>
        )}
      </Container>

      <Container className="mb-3">
        <Row>
          <Col md={{ span: 4, offset: 4 }} xs={12}>
            <Form.Group controlId="filtroComuna">
              <Form.Select
                aria-label="Filtrar por comuna"
                value={filtroComuna}
                onChange={(e) => handleFiltroComuna(e.target.value)}
              >
                <option value="Todas">Todas las comunas</option>
                {COMUNAS.map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </Form.Select>
            </Form.Group>
          </Col>
        </Row>
      </Container>

      <PropertyCatalog
        propiedades={propiedades}
        filtro={filtro}
        setFiltro={filtrarCategoria}
        onImageClick={setModalImage}
      />
      <Footer />
      <ImageModal imageUrl={modalImage} onClose={() => setModalImage(null)} />
      <WhatsAppButton />
    </div>
  );
}

export default Home;
