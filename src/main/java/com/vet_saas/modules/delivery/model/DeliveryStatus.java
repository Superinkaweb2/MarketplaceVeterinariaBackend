package com.vet_saas.modules.delivery.model;

import java.util.EnumSet;

public enum DeliveryStatus {
    BUSCANDO_REPARTIDOR,
    REPARTIDOR_ASIGNADO,
    EN_TIENDA,
    RECOGIDO,
    EN_CAMINO,
    CERCA,
    ENTREGADO,
    FALLIDO,
    CANCELADO;

    public boolean puedeTransicionarA(DeliveryStatus siguiente) {
        return switch (this) {
            case BUSCANDO_REPARTIDOR -> EnumSet.of(REPARTIDOR_ASIGNADO, FALLIDO, CANCELADO).contains(siguiente);
            case REPARTIDOR_ASIGNADO -> EnumSet.of(EN_TIENDA, CANCELADO).contains(siguiente);
            case EN_TIENDA           -> EnumSet.of(RECOGIDO, CANCELADO).contains(siguiente);
            case RECOGIDO            -> EnumSet.of(EN_CAMINO).contains(siguiente);
            case EN_CAMINO           -> EnumSet.of(CERCA, ENTREGADO).contains(siguiente);
            case CERCA               -> EnumSet.of(ENTREGADO, FALLIDO).contains(siguiente);
            default                  -> false;
        };
    }

    public boolean esFinal() {
        return this == ENTREGADO || this == FALLIDO || this == CANCELADO;
    }
}
