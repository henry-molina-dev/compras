package com.ferrocompras.ordenescompra.modulocompras.service.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferrocompras.ordenescompra.modulocompras.entity.EstadoOrden;
import com.ferrocompras.ordenescompra.modulocompras.entity.OrdenCompra;
import org.junit.jupiter.api.Test;

class EstadoOrdenStateTest {

    private final CreadaState creada = new CreadaState();
    private final AprobadaState aprobada = new AprobadaState();
    private final CerradaState cerrada = new CerradaState();
    private final AnuladaState anulada = new AnuladaState();

    private OrdenCompra ordenEn(EstadoOrden estado) {
        return OrdenCompra.builder().estado(estado).build();
    }

    @Test
    void creadaAprobarTransicionaAAprobada() {
        OrdenCompra orden = ordenEn(EstadoOrden.CREADA);
        creada.aprobar(orden);
        assertThat(orden.getEstado()).isEqualTo(EstadoOrden.APROBADA);
    }

    @Test
    void creadaAnularTransicionaAAnuladaConMotivo() {
        OrdenCompra orden = ordenEn(EstadoOrden.CREADA);
        creada.anular(orden, "Proveedor sin stock");
        assertThat(orden.getEstado()).isEqualTo(EstadoOrden.ANULADA);
        assertThat(orden.getMotivoAnulacion()).isEqualTo("Proveedor sin stock");
    }

    @Test
    void aprobadaAnularTransicionaAAnuladaConMotivo() {
        OrdenCompra orden = ordenEn(EstadoOrden.APROBADA);
        aprobada.anular(orden, "Cambio de condiciones comerciales");
        assertThat(orden.getEstado()).isEqualTo(EstadoOrden.ANULADA);
        assertThat(orden.getMotivoAnulacion()).isEqualTo("Cambio de condiciones comerciales");
    }

    @Test
    void aprobadaCerrarTransicionaACerradaConConformeYObservacion() {
        OrdenCompra orden = ordenEn(EstadoOrden.APROBADA);
        aprobada.cerrar(orden, true, "Recibido completo");
        assertThat(orden.getEstado()).isEqualTo(EstadoOrden.CERRADA);
        assertThat(orden.getConforme()).isTrue();
        assertThat(orden.getObservacionCierre()).isEqualTo("Recibido completo");
    }

    @Test
    void creadaCerrarEsUnaTransicionInvalida() {
        OrdenCompra orden = ordenEn(EstadoOrden.CREADA);
        assertThatThrownBy(() -> creada.cerrar(orden, true, null))
            .isInstanceOf(EstadoInvalidoException.class);
    }

    @Test
    void aprobadaAprobarEsUnaTransicionInvalida() {
        OrdenCompra orden = ordenEn(EstadoOrden.APROBADA);
        assertThatThrownBy(() -> aprobada.aprobar(orden))
            .isInstanceOf(EstadoInvalidoException.class);
    }

    @Test
    void cerradaNoPermiteNingunaTransicion() {
        OrdenCompra orden = ordenEn(EstadoOrden.CERRADA);
        assertThatThrownBy(() -> cerrada.aprobar(orden)).isInstanceOf(EstadoInvalidoException.class);
        assertThatThrownBy(() -> cerrada.anular(orden, "motivo")).isInstanceOf(EstadoInvalidoException.class);
        assertThatThrownBy(() -> cerrada.cerrar(orden, true, null)).isInstanceOf(EstadoInvalidoException.class);
    }

    @Test
    void anuladaEsEstadoFinalSinTransicionesValidas() {
        OrdenCompra orden = ordenEn(EstadoOrden.ANULADA);
        assertThatThrownBy(() -> anulada.aprobar(orden)).isInstanceOf(EstadoInvalidoException.class);
        assertThatThrownBy(() -> anulada.anular(orden, "motivo")).isInstanceOf(EstadoInvalidoException.class);
        assertThatThrownBy(() -> anulada.cerrar(orden, true, null)).isInstanceOf(EstadoInvalidoException.class);
    }
}
