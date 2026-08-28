import React, { useState, useEffect } from 'react';
import Navbar from '../components/Navbar';
import HeroSection from '../components/HeroSection';
import PropertyCatalog from '../components/PropertyCatalog';
import Footer from '../components/Footer';
import ImageModal from '../components/ImageModal';
import WhatsAppButton from '../components/WhatsAppButton';
import '../App.css';

const SESSION_KEY = 'inmobiliaria_session';
const PROPIEDADES_KEY = 'inmobiliaria_propiedades';

function Home() {
  const [propiedades, setPropiedades] = useState([]);
  const [todasLasPropiedades, setTodasLasPropiedades] = useState([]);
  const [filtro, setFiltro] = useState('Todos');
  const [modalImage, setModalImage] = useState(null);
  const [user, setUser] = useState(null);

  const getSession = () => {
    const session = localStorage.getItem(SESSION_KEY);
    return session ? JSON.parse(session) : null;
  };

  const updateAuthUI = () => {
    const session = getSession();
    setUser(session);
  };

  const logout = () => {
    localStorage.removeItem(SESSION_KEY);
    setUser(null);
    window.location.href = '/login';
  };

  const cargarPropiedades = () => {
    try {
      const propiedadesGuardadas = localStorage.getItem(PROPIEDADES_KEY);
      let propiedadesData = [];
      
      if (propiedadesGuardadas) {
        propiedadesData = JSON.parse(propiedadesGuardadas);
      } else {
        // Datos de ejemplo iniciales
        propiedadesData = [
          {
            id: 1,
            titulo: 'Hermosa Casa en Las Condes',
            categoria: 'Casa',
            precio: 150000000,
            tipoPrecio: '/ Valor Total',
            ubicacion: 'Las Condes, Santiago',
            piezas: 4,
            banos: 3,
            estacionamiento: 2,
            bodega: 1,
            imagenes: ['imagenes/casa1.jpg'],
            tipoOperacion: 'Venta',
            gastosComunes: 50000,
            videoUrl: ''
          },
          {
            id: 2,
            titulo: 'Departamento Moderno Centro',
            categoria: 'Departamento',
            precio: 85000000,
            tipoPrecio: '/ Valor Total',
            ubicacion: 'Santiago Centro',
            piezas: 2,
            banos: 1,
            estacionamiento: 1,
            bodega: 0,
            imagenes: ['imagenes/casa1.jpg'],
            tipoOperacion: 'Venta',
            gastosComunes: 30000,
            videoUrl: ''
          },
          {
            id: 3,
            titulo: 'Terreno en La Dehesa',
            categoria: 'Terreno',
            precio: 200000000,
            tipoPrecio: '/ Valor Total',
            ubicacion: 'La Dehesa',
            piezas: 0,
            banos: 0,
            estacionamiento: 0,
            bodega: 0,
            imagenes: ['imagenes/casa1.jpg'],
            tipoOperacion: 'Venta',
            gastosComunes: 0,
            videoUrl: ''
          }
        ];
        localStorage.setItem(PROPIEDADES_KEY, JSON.stringify(propiedadesData));
      }

      setTodasLasPropiedades(propiedadesData);
      setPropiedades(propiedadesData);

    } catch (error) {
      console.error("Error al cargar propiedades:", error);
    }
  };

  const filtrarCategoria = (catSeleccionada) => {
    if (catSeleccionada === 'Todos') {
      setPropiedades([...todasLasPropiedades]);
    } else {
      setPropiedades(todasLasPropiedades.filter(p => p.categoria === catSeleccionada));
    }
    setFiltro(catSeleccionada);
  };

  useEffect(() => {
    cargarPropiedades();
    updateAuthUI();
  }, []);

  return (
    <div className="App">
      <Navbar user={user} onLogout={logout} />
      <HeroSection />
      <PropertyCatalog 
        propiedades={propiedades}
        filtro={filtro}
        setFiltro={setFiltro}
        onImageClick={setModalImage}
      />
      <Footer />
      <ImageModal imageUrl={modalImage} onClose={() => setModalImage(null)} />
      <WhatsAppButton />
    </div>
  );
}

export default Home;
