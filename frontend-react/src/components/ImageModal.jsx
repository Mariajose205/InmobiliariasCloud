import React from 'react';
import { Modal } from 'react-bootstrap';

const ImageModal = ({ imageUrl, onClose }) => {
  return (
    <Modal show={!!imageUrl} onHide={onClose} centered size="lg" contentClassName="bg-dark border-0">
      <Modal.Header closeButton className="border-0" closeVariant="white" />
      <Modal.Body className="text-center p-4">
        {imageUrl && (
          <img 
            src={imageUrl} 
            alt="Vista ampliada" 
            style={{ 
              maxWidth: '100%', 
              maxHeight: '80vh', 
              objectFit: 'contain', 
              borderRadius: '8px', 
              boxShadow: '0 4px 20px rgba(0,0,0,0.5)' 
            }} 
          />
        )}
      </Modal.Body>
    </Modal>
  );
};

export default ImageModal;
