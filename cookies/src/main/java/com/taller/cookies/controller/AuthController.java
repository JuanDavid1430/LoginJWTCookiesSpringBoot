package com.taller.cookies.controller;

import com.taller.cookies.security.JwtUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Controlador de autenticación JWT.
 *
 *  POST  /api/auth/login   → valida credenciales, genera JWT, lo guarda en HttpOnly cookie
 *  GET   /api/auth/me      → devuelve info del usuario autenticado (requiere JWT)
 *  POST  /api/auth/logout  → elimina la cookie del JWT
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtUtil jwtUtil;

    public AuthController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    // ─────────────────────────────────────────────
    // LOGIN → genera JWT y lo deposita en HttpOnly cookie
    // ─────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(
            @RequestBody Map<String, String> body,
            HttpServletResponse response) {

        String usuario = body.getOrDefault("usuario", "").trim();
        String clave   = body.getOrDefault("clave",   "");

        // Validación simple (en producción: DB + BCrypt)
        if (usuario.isBlank() || !"1234".equals(clave)) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Credenciales inválidas. Usa cualquier usuario y clave 1234"));
        }

        String token = jwtUtil.generateToken(usuario);

        // Guardar el JWT en una cookie HttpOnly (no accesible desde JavaScript → previene XSS)
        ResponseCookie authCookie = ResponseCookie.from("auth_token", token)
                .httpOnly(true)
                .secure(false)          // true en producción (HTTPS)
                .path("/")
                .maxAge(Duration.ofHours(1))
                .sameSite("Lax")        // Funciona entre localhost:5500 ↔ localhost:8080
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, authCookie.toString());

        Map<String, String> resp = new HashMap<>();
        resp.put("mensaje", "Login exitoso");
        resp.put("usuario", usuario);
        // El token también se devuelve en el body para que el frontend
        // pueda enviarlo como Bearer header (fallback al cookie HttpOnly)
        resp.put("token", token);
        return ResponseEntity.ok(resp);
    }

    // ─────────────────────────────────────────────
    // ME → devuelve datos del usuario autenticado
    //      Spring Security inyecta el Principal desde el JWT
    // ─────────────────────────────────────────────
    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> me(Principal principal) {
        Map<String, String> resp = new HashMap<>();
        resp.put("usuario",    principal.getName());
        resp.put("estado",     "Autenticado");
        resp.put("tecnologia", "Spring Boot + JWT + HttpOnly Cookie");
        return ResponseEntity.ok(resp);
    }

    // ─────────────────────────────────────────────
    // LOGOUT → invalida la cookie eliminándola
    // ─────────────────────────────────────────────
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletResponse response) {

        ResponseCookie expiredCookie = ResponseCookie.from("auth_token", "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(Duration.ZERO) // Expira inmediatamente
                .sameSite("Lax")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());

        return ResponseEntity.ok(Map.of("mensaje", "Sesión cerrada exitosamente"));
    }
}
