// Sistema de autenticación usando localStorage

const USERS_KEY = 'inmobiliaria_users';
const SESSION_KEY = 'inmobiliaria_session';

// Obtener usuarios almacenados
function getUsers() {
    const users = localStorage.getItem(USERS_KEY);
    return users ? JSON.parse(users) : [];
}

// Guardar usuarios
function saveUsers(users) {
    localStorage.setItem(USERS_KEY, JSON.stringify(users));
}

// Mostrar alerta
function showAlert(elementId, message, type) {
    const alertElement = document.getElementById(elementId);
    alertElement.className = `alert alert-${type}`;
    alertElement.textContent = message;
    alertElement.classList.remove('d-none');
    
    setTimeout(() => {
        alertElement.classList.add('d-none');
    }, 5000);
}

// Iniciar sesión
function login(email, password) {
    const users = getUsers();
    const user = users.find(u => u.email === email && u.password === password);
    
    if (user) {
        const session = {
            email: user.email,
            name: user.name,
            loggedIn: true
        };
        localStorage.setItem(SESSION_KEY, JSON.stringify(session));
        showAlert('loginAlert', '¡Sesión iniciada correctamente! Redirigiendo...', 'success');
        
        setTimeout(() => {
            window.location.href = '../index.html';
        }, 1500);
        return true;
    } else {
        showAlert('loginAlert', 'Correo o contraseña incorrectos', 'danger');
        return false;
    }
}

// Registrar usuario
function register(name, email, password) {
    const users = getUsers();
    
    // Verificar si el email ya existe
    if (users.find(u => u.email === email)) {
        showAlert('registerAlert', 'Este correo ya está registrado', 'warning');
        return false;
    }
    
    // Crear nuevo usuario
    const newUser = {
        name: name,
        email: email,
        password: password,
        createdAt: new Date().toISOString()
    };
    
    users.push(newUser);
    saveUsers(users);
    
    showAlert('registerAlert', '¡Cuenta creada exitosamente! Ahora puedes iniciar sesión', 'success');
    
    // Cambiar a la pestaña de login
    setTimeout(() => {
        const loginTab = document.getElementById('login-tab');
        const registerTab = document.getElementById('register-tab');
        loginTab.click();
        
        // Llenar el email en el formulario de login
        document.getElementById('loginEmail').value = email;
    }, 1500);
    
    return true;
}

// Cerrar sesión
function logout() {
    localStorage.removeItem(SESSION_KEY);
    window.location.href = 'Login/login.html';
}

// Verificar si hay sesión activa
function getSession() {
    const session = localStorage.getItem(SESSION_KEY);
    return session ? JSON.parse(session) : null;
}

// Event listeners
document.addEventListener('DOMContentLoaded', () => {
    // Verificar si ya hay sesión activa
    const session = getSession();
    if (session && session.loggedIn) {
        window.location.href = '../index.html';
    }
    
    // Formulario de login
    const loginForm = document.getElementById('loginForm');
    if (loginForm) {
        loginForm.addEventListener('submit', (e) => {
            e.preventDefault();
            const email = document.getElementById('loginEmail').value;
            const password = document.getElementById('loginPassword').value;
            login(email, password);
        });
    }
    
    // Formulario de registro
    const registerForm = document.getElementById('registerForm');
    if (registerForm) {
        registerForm.addEventListener('submit', (e) => {
            e.preventDefault();
            const name = document.getElementById('registerName').value;
            const email = document.getElementById('registerEmail').value;
            const password = document.getElementById('registerPassword').value;
            const confirmPassword = document.getElementById('registerConfirmPassword').value;
            
            if (password !== confirmPassword) {
                showAlert('registerAlert', 'Las contraseñas no coinciden', 'danger');
                return;
            }
            
            if (password.length < 6) {
                showAlert('registerAlert', 'La contraseña debe tener al menos 6 caracteres', 'warning');
                return;
            }
            
            register(name, email, password);
        });
    }
});

// Hacer logout disponible globalmente
window.logout = logout;
