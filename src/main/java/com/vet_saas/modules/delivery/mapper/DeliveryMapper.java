package com.vet_saas.modules.delivery.mapper;

import com.vet_saas.modules.delivery.dto.response.DeliveryResponseDTO;
import com.vet_saas.modules.delivery.dto.response.RepartidorResponseDTO;
import com.vet_saas.modules.delivery.model.Delivery;
import com.vet_saas.modules.delivery.model.Repartidor;
import org.mapstruct.*;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface DeliveryMapper {

    @Mapping(target = "idDelivery",       source = "id")
    @Mapping(target = "ordenId",            source = "orden.id")
    @Mapping(target = "repartidorId",       source = "repartidor.idRepartidor")
    @Mapping(target = "repartidorNombre",   expression = "java(getRepartidorNombre(delivery))")
    @Mapping(target = "repartidorFoto",     source = "repartidor.fotoPerfil")
    @Mapping(target = "repartidorTelefono", source = "repartidor.telefono")
    @Mapping(target = "repartidorVehiculo", expression = "java(delivery.getRepartidor() != null ? delivery.getRepartidor().getTipoVehiculo().name() : null)")
    @Mapping(target = "repartidorLat",      source = "repartidor.ubicacionLat")
    @Mapping(target = "repartidorLng",      source = "repartidor.ubicacionLng")
    @Mapping(target = "otpCliente",         ignore = true)
    DeliveryResponseDTO toResponseDTO(Delivery delivery);

    /** Copia de toResponseDTO pero con OTP incluido (solo al crear) */
    @Mapping(target = "idDelivery",       source = "delivery.id")
    @Mapping(target = "ordenId",            source = "delivery.orden.id")
    @Mapping(target = "repartidorId",       source = "delivery.repartidor.idRepartidor")
    @Mapping(target = "repartidorNombre",   expression = "java(getRepartidorNombre(delivery))")
    @Mapping(target = "repartidorFoto",     source = "delivery.repartidor.fotoPerfil")
    @Mapping(target = "repartidorTelefono", source = "delivery.repartidor.telefono")
    @Mapping(target = "repartidorLat",      source = "delivery.repartidor.ubicacionLat")
    @Mapping(target = "repartidorLng",      source = "delivery.repartidor.ubicacionLng")
    @Mapping(target = "repartidorVehiculo", ignore = true)
    @Mapping(target = "otpCliente",         source = "otp")
    DeliveryResponseDTO toResponseDTOConOTP(Delivery delivery, String otp);

    @Mapping(target = "fotoPerfil", source = "fotoPerfil")
    RepartidorResponseDTO toRepartidorDTO(Repartidor repartidor);

    default String getRepartidorNombre(Delivery delivery) {
        if (delivery.getRepartidor() == null) return null;
        return delivery.getRepartidor().getNombres() + " " + delivery.getRepartidor().getApellidos();
    }
}
