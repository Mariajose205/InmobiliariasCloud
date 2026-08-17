
let propiedades = [];            
let todasLasPropiedades = [];     

function formatearPrecio(precio) {
    const num = parseInt(String(precio).replace(/\./g, '')) || 0;
    return new Intl.NumberFormat('es-CL', {
        style: 'currency',
        currency: 'CLP',
        minimumFractionDigits: 0
    }).format(num);
}

// Renderiza las tarjetas usando la lista de propiedades actual
function renderizarPropiedades() {
    const contenedor = document.getElementById('contenedor-propiedades');
    if (!contenedor) return;
    contenedor.innerHTML = '';
    
    if (propiedades.length === 0) {
        contenedor.innerHTML = `
            <div class="col-12 text-center text-muted p-5">
                <i class="bi bi-info-circle fs-3 d-block mb-2"></i>
                No hay propiedades disponibles en esta categoría por el momento.
            </div>`;
        return;
    }
    
    propiedades.forEach((propiedad, idx) => {
        const col = document.createElement('div');
        col.className = 'col-12 col-sm-6 col-md-4 col-lg-4';
        
        const card = document.createElement('div');
        card.className = 'card property-card h-100 shadow-sm border-0';
        
        const imagenes = (propiedad.imagenes && propiedad.imagenes.length > 0) ? propiedad.imagenes : ['imagenes/casa1.jpg'];
        const primeraImagen = imagenes[0];
        
        const cardId = `propiedad-${idx}`;
        const imgId = `img-${idx}`;
        const counterId = `counter-${idx}`;
        const sufijo = propiedad.tipoPrecio ? propiedad.tipoPrecio : "/ Valor Total";
        
        // Operación dinámica (Venta / Arriendo)
        const badgeOperacion = propiedad.tipoOperacion === 'Arriendo' ? 
            `<span class="badge bg-warning text-dark me-1"><i class="bi bi-key-fill me-1"></i>Arriendo</span>` : 
            `<span class="badge bg-success text-white me-1"><i class="bi bi-cash-coin me-1"></i>Venta</span>`;

        // Renderizado dinámico de Gastos Comunes
        const htmlGastosComunes = (propiedad.gastosComunes && propiedad.gastosComunes > 0) ? 
            `<div class="text-muted small mb-2"><i class="bi bi-receipt me-1"></i>GGCC: <strong>${formatearPrecio(propiedad.gastosComunes)}</strong></div>` : '';

        // Botón dinámico para el video/tour virtual de YouTube
        const htmlBotonVideo = propiedad.videoUrl ? 
            `<a href="${propiedad.videoUrl}" target="_blank" class="btn btn-outline-danger btn-sm w-100 mb-2 mt-auto d-flex align-items-center justify-content-center">
                <i class="bi bi-youtube me-2"></i>Ver Video / Tour Virtual
             </a>` : '';
        
        // ARMADO DEL MENSAJE AUTOMÁTICO DE WHATSAPP
        const numeroTelefono = "56985519073"; 
        const textoMensaje = `Hola, me interesa la propiedad ("${propiedad.tipoOperacion}") publicada: "${propiedad.titulo}" con un valor de ${formatearPrecio(propiedad.precio)} ${sufijo}. Me gustaría recibir más información.`;
        const urlWhatsapp = `https://wa.me/${numeroTelefono}?text=${encodeURIComponent(textoMensaje)}`;
        
        card.innerHTML = `
            <div class="image-carousel" style="position: relative; overflow: hidden;">
                <button class="carousel-nav carousel-prev" onclick="cambiarImagen('${cardId}', -1); event.stopPropagation();" style="display: ${imagenes.length > 1 ? 'block' : 'none'}">‹</button>
                <img id="${imgId}" src="${primeraImagen}" alt="${propiedad.titulo}" loading="lazy" data-current-index="0" 
                     onclick="ampliarImagen(this.src)" 
                     title="Haz clic para expandir la imagen"
                     style="height: 220px; object-fit: cover; width: 100%; cursor: pointer; transition: transform 0.2s ease;">
                <button class="carousel-nav carousel-next" onclick="cambiarImagen('${cardId}', 1); event.stopPropagation();" style="display: ${imagenes.length > 1 ? 'block' : 'none'}">›</button>
                <div id="${counterId}" class="image-counter" style="display: ${imagenes.length > 1 ? 'block' : 'none'}">1/${imagenes.length}</div>
            </div>
            <div class="card-body d-flex flex-column">
                <div class="mb-2">
                    ${badgeOperacion}
                    <span class="badge bg-secondary">${propiedad.categoria}</span>
                </div>
                <h5 class="card-title fw-bold" style="color: #111;">${propiedad.titulo}</h5>
                <p class="price text-success fw-bold fs-5 mb-2">${formatearPrecio(propiedad.precio)} <span style="font-size: 0.7em; color: #666; font-weight: normal;">${sufijo}</span></p>
                
                <div class="features mb-2 d-flex gap-2 flex-wrap text-muted small">
                    <span class="feature me-2">
                        <i class="bi bi-door-open-fill me-1"></i>${propiedad.piezas} ${propiedad.piezas === 1 ? 'dorm.' : 'dorm.'}
                    </span>
                    <span class="feature me-2">
                        <i class="bi bi-droplet-fill me-1"></i>${propiedad.banos} ${propiedad.banos === 1 ? 'baño' : 'baños'}
                    </span>
                    <span class="feature me-2">
                        <i class="bi bi-p-circle-fill me-1"></i>${propiedad.estacionamiento} estac.
                    </span>
                    <span class="feature">
                        <i class="bi bi-box-seam-fill me-1"></i>${propiedad.bodega} bod.
                    </span>
                </div>

                ${htmlGastosComunes}

                <p class="location text-muted small mb-3 mt-auto">
                    <i class="bi bi-geo-alt-fill me-1 text-danger"></i>${propiedad.ubicacion}
                </p>
                
                ${htmlBotonVideo}

                <a href="${urlWhatsapp}" target="_blank" class="btn btn-primary btn-contact w-100 d-flex align-items-center justify-content-center" style="text-decoration: none;">
                    <i class="bi bi-whatsapp me-2"></i>Contactar 
                </a>
            </div>
        `;
        
        card.dataset.imagenes = JSON.stringify(imagenes);
        col.appendChild(card);
        contenedor.appendChild(col);
    });
}

function cambiarImagen(cardId, direction) {
    const idx = parseInt(cardId.replace('propiedad-', ''));
    const imgElement = document.getElementById(`img-${idx}`);
    const counterElement = document.getElementById(`counter-${idx}`);
    
    let imagenes;
    if (propiedades[idx]) {
        imagenes = (propiedades[idx].imagenes && propiedades[idx].imagenes.length > 0) ? propiedades[idx].imagenes : ['imagenes/casa1.jpg'];
    }
    
    if (imagenes && imagenes.length > 1) {
        let currentIndex = parseInt(imgElement.dataset.currentIndex || 0);
        currentIndex += direction;
        if (currentIndex < 0) {
            currentIndex = imagenes.length - 1;
        } else if (currentIndex >= imagenes.length) {
            currentIndex = 0;
        }
        
        imgElement.src = imagenes[currentIndex];
        imgElement.dataset.currentIndex = currentIndex;
        counterElement.textContent = `${currentIndex + 1}/${imagenes.length}`;
    }
}

function ampliarImagen(urlImagen) {
    const imagenGrande = document.getElementById('imagenGrandeVisor');
    const modalElement = document.getElementById('visorImagenModal');
    
    if (imagenGrande && modalElement) {
        imagenGrande.src = urlImagen;
        const miVisorModal = new bootstrap.Modal(modalElement);
        miVisorModal.show();
    }
}

async function cargarPropiedades() {
    try {
        console.log("Sincronizando: Conectando a Supabase Cloud...");
        
        const { data, error } = await supabaseClient
            .from('Proyecto') 
            .select('*')
            .order('id', { ascending: false }); 

        if (error) throw error;

        todasLasPropiedades = data.map(p => {
            let urlsArray = [];
            if (p.foto_url) {
                urlsArray = p.foto_url.split(',').map(url => url.trim()).filter(url => url !== "");
            }

            return {
                titulo: p.nombre,
                categoria: p.categoria || "Casa", 
                precio: p.precio || "0",
                tipoPrecio: p.tipo_precio || "/ Valor Total",
                ubicacion: p.ubicacion || "",
                piezas: p.piezas || 0,
                banos: p.banos || 0,
                imagenes: urlsArray,
                // NUEVOS CAMPOS OBTENIDOS DESDE SUPABASE DE MANERA COHERENTE:
                tipoOperacion: p.tipo_operacion || "Venta",
                gastosComunes: p.gastos_comunes || 0,
                estacionamiento: p.estacionamiento || 0,
                bodega: p.bodega || 0,
                videoUrl: p.video_url || ""
            };
        });

        propiedades = [...todasLasPropiedades];
        renderizarPropiedades();

    } catch (error) {
        console.error("Error al comunicar con la base de datos de Supabase:", error);
    }
}

function filtrarCategoria(catSeleccionada) {
    const botones = document.querySelectorAll('.filter-btn');
    botones.forEach(btn => btn.classList.remove('active'));
    
    if (window.event && window.event.target) {
        window.event.target.classList.add('active');
    }

    if (catSeleccionada === 'Todos') {
        propiedades = [...todasLasPropiedades];
    } else {
        propiedades = todasLasPropiedades.filter(p => p.categoria === catSeleccionada);
    }
    
    renderizarPropiedades();
}

document.addEventListener('DOMContentLoaded', () => {
    cargarPropiedades();
    window.filtrarCategoria = filtrarCategoria;
    window.cambiarImagen = cambiarImagen; 
    window.ampliarImagen = ampliarImagen; 
});