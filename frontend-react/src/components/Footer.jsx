import React from 'react';
import { Container, Row, Col } from 'react-bootstrap';

const Footer = () => {
  return (
    <footer id="contacto">
      <Container>
        <Row className="align-items-center">
          <Col xs={12} md={6} className="text-md-start text-center mb-4 mb-md-0">
            <h4>Contáctanos</h4>
            <p className="mb-3">
              <i className="bi bi-telephone-fill me-2 text-info"></i>
              +56 9 67676767
            </p>
            <p className="mb-3">
              <i className="bi bi-telephone-fill me-2 text-info"></i>
              +56 9 69696969
            </p>
            <p className="mb-3">
              <i className="bi bi-envelope-fill me-2 text-info"></i>
              Duoc@gmail.cl
            </p>
            <p className="mb-0">
              <a 
                href="" 
                target="_blank" 
                rel="noopener noreferrer"
                style={{ color: 'white', textDecoration: 'none', transition: 'opacity 0.3s' }}
                onMouseOver={(e) => e.target.style.opacity = '0.8'}
                onMouseOut={(e) => e.target.style.opacity = '1'}
              >
                <i className="bi bi-instagram me-2" style={{ color: '#e1306c' }}></i>
                @Lamaldito
              </a>
            </p>
          </Col>

          <Col xs={12} md={6} className="text-center">
            <div className="qr-wrapper">
              <div>
                <div className="qr-container">
                  <img src="imagenes/qr_instagram.jpeg" alt="QR Instagram" className="qr-img" />
                </div>
                <div className="qr-box-text">Escanéanos en Instagram</div>
              </div>
              
              <div>
                <div className="qr-container">
                  <img src="imagenes/qr_wsp.jpeg" alt="QR WhatsApp" className="qr-img" />
                </div>
                <div className="qr-box-text">Escríbenos por WhatsApp</div>
              </div>
            </div>
          </Col>
        </Row>

        <hr className="my-4" style={{ borderColor: 'rgba(255,255,255,0.15)' }} />
        <div className="text-center">
          <p className="mb-0" style={{ fontSize: '0.85rem', color: '#a0aec0' }}>
            &copy; 2026 Duoc Propiedades Derechos reservados.
          </p>
        </div>
      </Container>
    </footer>
  );
};

export default Footer;
