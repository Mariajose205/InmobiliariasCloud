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
  obtenerAlertas,
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

  // Banner de novedades + contadores
  const [totalGuardadas, setTotalGuardadas] = useState(0);
  // Novedades que vienen del backend (GET /api/wishlist/novedades).
  // Se crean solas cuando admin/corredor publica en una comuna seguida.
  // Tambien se mantiene el polling del catalogo como respaldo local.
  const [novedades, setNovedades] = useState([]);
  const novedadesIdsRef = useRef(new Set());

  // Refs para el polling (evitan recrear el intervalo con cierres obsoletos)
  const seenIdsRef = useRef(new Set());
  const favoritosIdsRef = useRef(new Set());
  const comunasActivasRef = useRef(new Set());
  const primerSnapshotRef = useRef(false);
  const filtroRef = useRef(filtro);
  const filtroComunaRef = useRef(filtroComuna);
  useEffect(() => { filtroRef.current = filtro; }, [filtro]);
  useEffect(() => { filtroComunaRef.current = filtroComuna; }, [filtroComuna]);

  const normalizar = (valor) => (valor || '').toString().trim().toLowerCase();
  const normalizarComuna = (valor) => (valor || '').toString().trim().toLowerCase();
  const normalizarComunaComparacion = (valor) =>
    String(valor || '')
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .trim();

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
      // Primer snapshot: todo lo visible al cargar ya se considera visto
      // para que el polling solo avise de ids nuevos posteriores.
      if (!primerSnapshotRef.current) {
        const ids = new Set(lista.map((p) => String(p.id)).filter((v) => v && v !== 'undefined'));
        seenIdsRef.current = new Set([...seenIdsRef.current, ...ids]);
        primerSnapshotRef.current = true;
      }
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

  const esReciente24h = (p) => {
    // fechaCreacion puede venir como ISO / Instant de Java. Si no existe,
    // se omite el chequeo: solo manda el "id nuevo vs primer snapshot".
    const valor = p?.fechaCreacion;
    if (!valor) return true;
    const fecha = new Date(valor);
    if (Number.isNaN(fecha.getTime())) return true;
    return Date.now() - fecha.getTime() <= 24 * 60 * 60 * 1000;
  };

  const scrollAlCatalogo = () => {
    const el = document.getElementById('propiedades');
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  const cargarIntereses = async () => {
    if (!isLoggedIn) {
      setTotalGuardadas(0);
      favoritosIdsRef.current = new Set();
      comunasActivasRef.current = new Set();
      return;
    }

    let favoritos = [];
    try {
      const favoritosData = await obtenerFavoritos();
      favoritos = Array.isArray(favoritosData)
        ? favoritosData
        : (favoritosData?.favoritos || []);
      const ids = new Set(
        favoritos
          .map((item) => item?.propiedadId ?? item?.id ?? item)
          .filter((valor) => valor !== null && valor !== undefined && valor !== '')
          .map((valor) => String(valor))
      );
      favoritosIdsRef.current = ids;
      setTotalGuardadas(favoritos.length);
    } catch (_) {
      // Silencioso: sin favoritos no hay comunas activas por esta via.
      setTotalGuardadas(0);
      favoritosIdsRef.current = new Set();
    }

    let alertasComunas = [];
    try {
      const alertasData = await obtenerAlertas();
      const lista = Array.isArray(alertasData)
        ? alertasData
        : (alertasData?.alertas || alertasData?.comunas || []);
      alertasComunas = lista
        .map((item) => (typeof item === 'string' ? item : item?.comuna ?? item?.nombre ?? null))
        .filter(Boolean);
    } catch (_) {
      // Silencioso si falla con 404 u otro error (no debe romper Home).
      alertasComunas = [];
    }

    const comunas = new Set();
    favoritos.forEach((fav) => {
      const c = fav?.comuna || extraerComunaLocal(fav?.ubicacion);
      if (c) comunas.add(normalizarComunaComparacion(c));
    });
    alertasComunas.forEach((c) => {
      if (c) comunas.add(normalizarComunaComparacion(c));
    });
    comunasActivasRef.current = comunas;
  };

  const cargarNovedadesBackend = async () => {
    if (!isLoggedIn) return;
    try {
      const datos = await obtenerNovedades();
      const lista = Array.isArray(datos) ? datos : (datos?.novedades || []);
      // Solo muestra las que aun no se vieron en esta sesion (para no
      // repetir banners si el usuario recarga, se guarda el id visto).
      const nuevas = [];
      lista.forEach((n) => {
        const key = String(n.id ?? n.propiedadId);
        if (!key || key === 'undefined') return;
        if (novedadesIdsRef.current.has(key)) return;
        novedadesIdsRef.current.add(key);
        nuevas.push({
          id: n.id,
          propiedadId: n.propiedadId,
          propiedad: {
            id: n.propiedadId,
            titulo: n.titulo,
            precio: n.precio,
            tipoPrecio: n.tipoPrecio,
            ubicacion: n.ubicacion
          },
          comuna: n.comuna,
          mensaje: n.mensaje,
          origen: 'backend'
        });
      });
      if (nuevas.length > 0) {
        setNovedades((prev) => [...nuevas, ...prev]);
      }
    } catch (_) {
      // Silencioso: el polling de novedades no debe romper la pagina.
      // Si el endpoint aun no existe (despliegue viejo), el catalogo
      // polling de respaldo seguira avisando.
    }
  };

  // Carga inicial de propiedades
  useEffect(() => {
    cargarPropiedades();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Carga de favoritos + alertas (solo con sesion) para calcular comunas activas
  useEffect(() => {
    cargarIntereses();
    cargarNovedadesBackend();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoggedIn]);

  // Cuando cambia el login, limpia banners viejos
  useEffect(() => {
    if (!isLoggedIn) {
      setNovedades([]);
      novedadesIdsRef.current = new Set();
    }
  }, [isLoggedIn]);

  // Polling simultaneo: novedades del backend + catalogo de respaldo cada 15s.
  // La alerta llega sola, sin boton "Avisarme": basta con guardar un favorito
  // en la comuna para empezar a recibir avisos de nuevas publicaciones.
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

        const comunasActivas = comunasActivasRef.current;
        const favoritosIds = favoritosIdsRef.current;
        const nuevas = [];
        lista.forEach((p) => {
          const id = String(p.id);
          if (!id || id === 'undefined') return;
          if (seenIdsRef.current.has(id)) return;
          // Marcar visto apenas se evalua (no repetir en el proximo ciclo)
          seenIdsRef.current.add(id);
          if (favoritosIds.has(id)) return;
          if (comunasActivas.size === 0) return;
          const comuna = extraerComunaLocal(p.ubicacion);
          if (!comuna || !comunasActivas.has(normalizarComunaComparacion(comuna))) return;
          if (!esReciente24h(p)) return;
          // Evita duplicar si ya llego por novedades del backend
          if (novedadesIdsRef.current.has(String(p.id))) return;
          novedadesIdsRef.current.add(String(p.id));
          nuevas.push({ propiedad: p, comuna, origen: 'catalogo' });
        });
        if (nuevas.length > 0) {
          setNovedades((prev) => [...nuevas, ...prev]);
        }
      } catch (_) {
        // Silencioso: el polling no debe romper la pagina.
      }
      // Refresca intereses cada ~4 ciclos para que nuevas comunas seguidas
      // por favoritos tambien generen avisos sin recargar.
      // (cargarIntereses es barato: solo 2 GET autenticados)
      await cargarIntereses();
    }, 15000);
    return () => clearInterval(intervalo);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoggedIn]);

  const cerrarNovedad = async (novedad) => {
    const id = novedad?.id;
    const pid = String(novedad?.propiedad?.id ?? novedad?.propiedadId ?? '');
    setNovedades((prev) =>
      prev.filter((n) => {
        if (id != null && String(n.id) === String(id)) return false;
        if (pid && String(n.propiedad?.id ?? n.propiedadId) === pid && n.origen === 'backend') return false;
        if (pid && String(n.propiedad?.id) === pid) return false;
        return true;
      })
    );
    // Si vino del backend, borra la fila para que no reaparezca.
    if (id != null && novedad?.origen === 'backend' && isLoggedIn) {
      try {
        await descartarNovedad(id);
      } catch (_) {
        // Silencioso.
      }
    }
  };

  return (
    <div className="App">
      <Navbar />
      <HeroSection />

      <Container className="mb-3">
        {novedades.length > 0 && (
          <Alert variant="success" className="alert-novedad mb-2">
            <i className="bi bi-bell-fill me-2"></i>
            ¡Tienes {novedades.length} {novedades.length === 1 ? 'novedad' : 'novedades'} en tus comunas!
          </Alert>
        )}
        {novedades.map((novedad) => {
          const propiedad = novedad.propiedad || {};
          const comuna = novedad.comuna || extraerComunaLocal(propiedad.ubicacion) || '';
          return (
            <Alert
              key={String(novedad.id ?? propiedad.id)}
              variant="success"
              dismissible
              className="alert-novedad d-flex align-items-center justify-content-between"
              onClose={() => cerrarNovedad(novedad)}
            >
              <span>
                <i className="bi bi-bell-fill me-2"></i>
                ¡Nueva propiedad en {comuna}: {propiedad.titulo || novedad.mensaje}!
              </span>
              <Button
                size="sm"
                variant="success"
                className="ms-3 flex-shrink-0"
                onClick={scrollAlCatalogo}
              >
                Ver
              </Button>
            </Alert>
          );
        })}

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
