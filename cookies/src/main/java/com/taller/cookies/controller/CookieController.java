package com.taller.cookies.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * TALLER: Manejo de Cookies con Spring Boot
 *
 * Endpoints disponibles:
 *  POST  /api/cookies/login        → crea una cookie de sesión
 *  GET   /api/cookies/perfil       → lee la cookie y devuelve el usuario
 *  GET   /api/cookies/listar       → lista todas las cookies recibidas
 *  POST  /api/cookies/preferencia  → guarda preferencia en cookie
 *  DELETE /api/cookies/logout      → elimina la cookie de sesión
 */
@RestController
@RequestMapping("/api/cookies")
public class CookieController {

    // ─────────────────────────────────────────────
    // 1. LOGIN → crea cookie de sesión
    // ─────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(
            @RequestBody Map<String, String> body,
            HttpServletResponse response) {

        String usuario = body.getOrDefault("usuario", "juan");
        String clave   = body.getOrDefault("clave", "");

        // Validación simple (en producción usa DB + BCrypt)
        if (!"1234".equals(clave)) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Credenciales inválidas"));
        }

        // Crear la cookie de sesión
        Cookie sessionCookie = new Cookie("session_id", "usr_" + usuario + "_token_abc123");
        sessionCookie.setHttpOnly(true);   // No accesible desde JS (previene XSS)
        sessionCookie.setSecure(false);    // true en producción (solo HTTPS)
        sessionCookie.setPath("/");        // Aplica a todas las rutas
        sessionCookie.setMaxAge(3600);     // Expira en 1 hora (en segundos)
        // sessionCookie.setAttribute("SameSite", "Strict"); // Spring Boot 3+

        // Crear cookie de preferencia de idioma
        Cookie langCookie = new Cookie("idioma", "es");
        langCookie.setPath("/");
        langCookie.setMaxAge(86400 * 30); // 30 días

        response.addCookie(sessionCookie);
        response.addCookie(langCookie);

        Map<String, String> resp = new HashMap<>();
        resp.put("mensaje", "Login exitoso para: " + usuario);
        resp.put("session", "Revisa las cookies en Postman → Cookies tab");
        return ResponseEntity.ok(resp);
    }

    // ─────────────────────────────────────────────
    // 2. PERFIL → lee la cookie de sesión
    // ─────────────────────────────────────────────
    @GetMapping("/perfil")
    public ResponseEntity<Map<String, String>> perfil(HttpServletRequest request) {

        Optional<Cookie> sessionCookie = getCookie(request, "session_id");

        if (sessionCookie.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "No hay sesión activa. Haz login primero."));
        }

        String valor = sessionCookie.get().getValue();

        Map<String, String> resp = new HashMap<>();
        resp.put("estado",      "Autenticado");
        resp.put("cookie",      "session_id=" + valor);
        resp.put("idioma",      getCookie(request, "idioma")
                .map(Cookie::getValue)
                .orElse("no definido"));
        return ResponseEntity.ok(resp);
    }

    // ─────────────────────────────────────────────
    // 3. LISTAR → muestra todas las cookies recibidas
    // ─────────────────────────────────────────────
    @GetMapping("/listar")
    public ResponseEntity<?> listarCookies(HttpServletRequest request) {

        Cookie[] cookies = request.getCookies();

        if (cookies == null || cookies.length == 0) {
            return ResponseEntity.ok(Map.of("mensaje", "No se recibieron cookies"));
        }

        Map<String, String> mapa = new HashMap<>();
        Arrays.stream(cookies).forEach(c -> mapa.put(c.getName(), c.getValue()));
        return ResponseEntity.ok(mapa);
    }

    // ─────────────────────────────────────────────
    // 4. PREFERENCIA → guarda una preferencia del usuario
    // ─────────────────────────────────────────────
    @PostMapping("/preferencia")
    public ResponseEntity<Map<String, String>> guardarPreferencia(
            @RequestParam String nombre,
            @RequestParam String valor,
            HttpServletResponse response) {

        // Solo permitimos nombres de preferencia válidos
        if (!nombre.matches("[a-zA-Z_]+")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Nombre de preferencia inválido"));
        }

        Cookie pref = new Cookie("pref_" + nombre, valor);
        pref.setPath("/");
        pref.setMaxAge(86400 * 7); // 7 días
        response.addCookie(pref);

        return ResponseEntity.ok(Map.of(
                "mensaje", "Preferencia guardada",
                "cookie",  "pref_" + nombre + "=" + valor
        ));
    }

    // ─────────────────────────────────────────────
    // 5. LOGOUT → elimina la cookie de sesión
    // ─────────────────────────────────────────────
    @DeleteMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletResponse response) {

        // Para borrar una cookie: mismo nombre y Path, MaxAge = 0
        Cookie sessionCookie = new Cookie("session_id", "");
        sessionCookie.setPath("/");
        sessionCookie.setMaxAge(0); // Expira inmediatamente

        Cookie langCookie = new Cookie("idioma", "");
        langCookie.setPath("/");
        langCookie.setMaxAge(0);

        response.addCookie(sessionCookie);
        response.addCookie(langCookie);

        return ResponseEntity.ok(Map.of("mensaje", "Sesión cerrada. Cookies eliminadas."));
    }

    // ─────────────────────────────────────────────
    // Utilidad: buscar cookie por nombre
    // ─────────────────────────────────────────────
    private Optional<Cookie> getCookie(HttpServletRequest request, String nombre) {
        if (request.getCookies() == null) return Optional.empty();
        return Arrays.stream(request.getCookies())
                .filter(c -> nombre.equals(c.getName()))
                .findFirst();
    }
}
