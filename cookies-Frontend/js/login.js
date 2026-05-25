const API = 'http://localhost:8080';

const form      = document.getElementById('loginForm');
const submitBtn = document.getElementById('submitBtn');
const btnText   = document.getElementById('btnText');
const btnLoader = document.getElementById('btnLoader');
const errorMsg  = document.getElementById('errorMsg');

function setLoading(loading) {
  submitBtn.disabled = loading;
  btnText.classList.toggle('hidden', loading);
  btnLoader.classList.toggle('hidden', !loading);
}

function showError(msg) {
  errorMsg.textContent = msg;
  errorMsg.classList.remove('hidden');
}

function hideError() {
  errorMsg.classList.add('hidden');
}

form.addEventListener('submit', async (e) => {
  e.preventDefault();
  hideError();

  const usuario = document.getElementById('usuario').value.trim();
  const clave   = document.getElementById('clave').value;

  if (!usuario || !clave) {
    showError('Por favor completa todos los campos.');
    return;
  }

  setLoading(true);

  try {
    const res = await fetch(`${API}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',          // Necesario para que el browser guarde la cookie
      body: JSON.stringify({ usuario, clave }),
    });

    const data = await res.json();

    if (!res.ok) {
      showError(data.error || 'Error al iniciar sesión.');
      return;
    }

    // Guardar usuario y token en sessionStorage:
    // - 'usuario' → para mostrar nombre inmediatamente en el dashboard
    // - 'token'   → fallback Bearer cuando el navegador no envía la HttpOnly cookie
    sessionStorage.setItem('usuario', data.usuario || usuario);
    sessionStorage.setItem('token',   data.token   || '');
    window.location.href = 'dashboard.html';

  } catch (err) {
    showError('No se pudo conectar con el servidor. ¿Está corriendo en puerto 8080?');
  } finally {
    setLoading(false);
  }
});
