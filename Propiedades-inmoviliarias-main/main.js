document.addEventListener("DOMContentLoaded", function() {
    const datosLocales = localStorage.getItem('propiedades_local');
    if (datosLocales) {
        const propiedades = JSON.parse(datosLocales);
        const contenedor = document.getElementById("contenedor-propiedades");
        
        if (contenedor) {
            contenedor.innerHTML = "";
            
            propiedades.forEach((p, idx) => {
                const imagenes = (p.imagenes && p.imagenes.length > 0) ? p.imagenes : [p.imagen || 'images/casa1.jpg'];
                const primeraImagen = imagenes[0];

                const cardId = `propiedad-${idx}`;
                const imgId = `img-${idx}`;
                const counterId = `counter-${idx}`;
                const sufijoPrecio = p.tipoPrecio ? p.tipoPrecio : "/ Mes";
                const textoMensajeWsp = encodeURIComponent(`Hola, estoy interesado en la propiedad: ${p.titulo}`);

                contenedor.innerHTML += `
                    <div class="col-12 col-md-6 col-lg-4">
                        <div class="property-card card" data-propiedad-id="${idx}">
                            <div class="image-carousel">
                                <button class="carousel-nav carousel-prev" onclick="cambiarImagen('${cardId}', -1)">‹</button>
                                <img id="${imgId}" src="${primeraImagen}" alt="${p.titulo}" data-current-index="0" style="cursor: pointer;" onclick="abrirVisor('${imgId}')">
                                <button class="carousel-nav carousel-next" onclick="cambiarImagen('${cardId}', 1)">›</button>
                                <div id="${counterId}" class="image-counter">1/${imagenes.length}</div>
                            </div>
                            <div class="card-body">
                                <h5 class="card-title">${p.titulo}.</h5>
                                <div class="price">$${p.precio} ${sufijoPrecio}</div>
                                <div class="features mb-3">
                                    <div class="feature"><i class="bi bi-door-open"></i> ${p.piezas} piezas</div>
                                    <div class="feature"><i class="bi bi-droplet"></i> ${p.banos} baños</div>
                                </div>
                                <div class="location mb-3"><i class="bi bi-geo-alt"></i> ${p.ubicacion}</div>
                                <a href="https://wa.me/56985519073?text=${textoMensajeWsp}" 
                                   target="_blank" 
                                   class="btn btn-contact text-white w-100 d-flex align-items-center justify-content-center" 
                                   style="text-decoration: none;">
                                    <i class="bi bi-whatsapp me-2"></i>Contactar
                                </a>
                            </div>
                        </div>
                    </div>
                `;

                setTimeout(() => {
                    const cardElement = document.querySelector(`[data-propiedad-id="${idx}"]`);
                    if (cardElement) {
                        cardElement.dataset.imagenes = JSON.stringify(imagenes);
                        cardElement.dataset.currentIndex = 0;
                    }
                }, 0);
            });
        }
    }
});

function cambiarImagen(cardId, direction) {
    const idx = cardId.replace('propiedad-', '');
    const imgElement = document.getElementById(`img-${idx}`);
    const counterElement = document.getElementById(`counter-${idx}`);
    
    const datosLocales = localStorage.getItem('propiedades_local');
    if (datosLocales) {
        const propiedades = JSON.parse(datosLocales);
        const propiedad = propiedades[idx];
        
        if (propiedad) {
            const imagenes = (propiedad.imagenes && propiedad.imagenes.length > 0) ? propiedad.imagenes : [propiedad.imagen || 'images/casa1.jpg'];
            
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
}

function abrirVisor(imgId) {
    const imgPequena = document.getElementById(imgId);
    const imgGrande = document.getElementById('imagenGrandeVisor');
    
    if (imgPequena && imgGrande) {
        imgGrande.src = imgPequena.src;
        const miModal = new bootstrap.Modal(document.getElementById('visorImagenModal'));
        miModal.show();
    }
}