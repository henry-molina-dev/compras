package com.ferrocompras.ordenescompra.security;

import com.ferrocompras.ordenescompra.shared.enums.Rol;

// Principal ligero reconstruido a partir de los claims del JWT en cada request, sin volver a
// consultar la base de datos: id/rol/sucursalId son exactamente lo que el resto del backend
// necesita del usuario autenticado (autoria, autorizacion por rol, filtro por sucursal).
public record UsuarioPrincipal(Integer id, String username, Rol rol, Integer sucursalId) {
}
