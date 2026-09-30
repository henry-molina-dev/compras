package com.ferrocompras.ordenescompra.modulocompras.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Year;
import org.springframework.stereotype.Component;

@Component
public class NumeroOrdenGenerator {

    @PersistenceContext
    private EntityManager entityManager;

    // INSERT ... ON CONFLICT DO UPDATE es atomico por fila en Postgres: dos transacciones
    // concurrentes para el mismo anio se serializan sobre el lock de esa fila, sin necesitar un
    // SELECT ... FOR UPDATE explicito por separado ni arriesgar un COUNT(*) no seguro.
    public String generar() {
        int anio = Year.now().getValue();
        Number ultimoValor = (Number) entityManager.createNativeQuery("""
            INSERT INTO numero_orden_contador (anio, ultimo_valor)
            VALUES (:anio, 1)
            ON CONFLICT (anio) DO UPDATE SET ultimo_valor = numero_orden_contador.ultimo_valor + 1
            RETURNING ultimo_valor
            """)
            .setParameter("anio", anio)
            .getSingleResult();

        return "OC-%d-%06d".formatted(anio, ultimoValor.intValue());
    }
}
