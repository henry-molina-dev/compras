package com.ferrocompras.ordenescompra.exception;

import com.ferrocompras.ordenescompra.dto.ErrorResponse;
import com.ferrocompras.ordenescompra.modulocompras.service.state.EstadoInvalidoException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Deliberadamente NO maneja AccessDeniedException: esa la intercepta el
// ExceptionTranslationFilter de Spring Security (ver RestAccessDeniedHandler) sin importar si se
// lanza desde un filtro, un controlador o un servicio.
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<ErrorResponse> handleNegocio(NegocioException ex) {
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of("invalid_request_error", ex.getCode(), ex.getMessage(), ex.getParam()));
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleNoEncontrado(RecursoNoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of("invalid_request_error", ex.getCode(), ex.getMessage(), ex.getParam()));
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<ErrorResponse> handleConflicto(ConflictoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of("state_conflict_error", ex.getCode(), ex.getMessage(), ex.getParam()));
    }

    @ExceptionHandler(EstadoInvalidoException.class)
    public ResponseEntity<ErrorResponse> handleEstadoInvalido(EstadoInvalidoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of("state_conflict_error", "transicion_invalida", ex.getMessage(), null));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse.of("authentication_error", "credenciales_invalidas", ex.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidacion(MethodArgumentNotValidException ex) {
        FieldError error = ex.getBindingResult().getFieldError();
        String param = error != null ? error.getField() : null;
        String mensaje = error != null ? error.getDefaultMessage() : "Datos de entrada invalidos.";
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of("invalid_request_error", "validacion", mensaje, param));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        String mensaje = ex.getConstraintViolations().stream()
            .findFirst()
            .map(ConstraintViolation::getMessage)
            .orElse("Parametros de entrada invalidos.");
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of("invalid_request_error", "validacion", mensaje, null));
    }

    // Las columnas UNIQUE alcanzables desde la peticion de un usuario final (producto.codigo,
    // usuario.username) producen esta violacion al duplicarse. Sin este manejador, el duplicado se
    // colaba como 500 sin cuerpo tipado, rompiendo la convencion de errores del resto de la API.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegridad(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of("invalid_request_error", "valor_duplicado",
                "Ya existe un registro con ese valor unico.", null));
    }
}
