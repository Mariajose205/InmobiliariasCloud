import React, { useState, useEffect } from 'react';
import Navbar from '../components/Navbar';
import HeroSection from '../components/HeroSection';
import PropertyCatalog from '../components/PropertyCatalog';
import Footer from '../components/Footer';
import ImageModal from '../components/ImageModal';
import WhatsAppButton from '../components/WhatsAppButton';
import '../App.css';

const PROPIEDADES_KEY = 'inmobiliaria_propiedades';

function Home() {
  const [propiedades, setPropiedades] = useState([]);
  const [todasLasPropiedades, setTodasLasPropiedades] = useState([]);
  const [filtro, setFiltro] = useState('Todos');
  const [modalImage, setModalImage] = useState(null);

  const cargarPropiedades = () => {
    try {
      const propiedadesGuardadas = localStorage.getItem(PROPIEDADES_KEY);
      let propiedadesData = [];

      if (propiedadesGuardadas) {
        propiedadesData = JSON.parse(propiedadesGuardadas);
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
  }, []);

  return (
    <div className="App">
      <Navbar />
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
