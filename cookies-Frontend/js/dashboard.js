const API = 'http://localhost:8080';

// Helper: construye los headers con el Bearer token guardado en sessionStorage.
// El backend acepta TANTO la HttpOnly cookie como este header → doble cobertura.
function authHeaders() {
  const token = sessionStorage.getItem('token');
  return token ? { 'Authorization': `Bearer ${token}` } : {};
}

// Mostrar el usuario guardado en sessionStorage de forma inmediata
// (evita el flash de "?" mientras llega la respuesta del backend)
const cachedUser = sessionStorage.getItem('usuario');
if (cachedUser) setUserUI(cachedUser);

function setUserUI(usuario) {
  document.getElementById('navUsername').textContent   = usuario;
  document.getElementById('welcomeUser').textContent   = usuario;
  document.getElementById('infoUsuario').textContent   = usuario;
  document.getElementById('avatarInitial').textContent = usuario.charAt(0).toUpperCase();
}

async function loadUserInfo() {
  let res;
  try {
    res = await fetch(`${API}/api/auth/me`, {
      method: 'GET',
      headers: authHeaders(),    // Bearer token (fallback)
      credentials: 'include',   // HttpOnly cookie (principal)
    });
  } catch (networkErr) {
    // Error de red (servidor caído, CORS bloqueado, etc.)
    // NO redirigir → mostrar aviso para que el usuario sepa qué ocurrió
    console.error('[Dashboard] Error de red al contactar /api/auth/me:', networkErr);
    document.getElementById('infoTech').textContent = '⚠ Sin conexión al servidor';
    return;
  }

  if (res.status === 401 || res.status === 403) {
    // Token ausente o expirado → limpiar sesión y volver al login
    sessionStorage.removeItem('usuario');
    sessionStorage.removeItem('token');
    window.location.href = 'login.html';
    return;
  }

  if (!res.ok) {
    console.error('[Dashboard] Respuesta inesperada:', res.status);
    document.getElementById('infoTech').textContent = `⚠ Error del servidor (${res.status})`;
    return;
  }

  const data = await res.json();
  const usuario = data.usuario || sessionStorage.getItem('usuario') || 'Usuario';

  sessionStorage.setItem('usuario', usuario);
  setUserUI(usuario);
  document.getElementById('infoTech').textContent = data.tecnologia || '—';
}

async function logout() {
  try {
    await fetch(`${API}/api/auth/logout`, {
      method: 'POST',
      headers: authHeaders(),
      credentials: 'include',
    });
  } finally {
    sessionStorage.removeItem('usuario');
    sessionStorage.removeItem('token');
    window.location.href = 'login.html';
  }
}

// ── Inicialización ──────────────────────────
loadUserInfo();

document.getElementById('logoutBtn').addEventListener('click', logout);
