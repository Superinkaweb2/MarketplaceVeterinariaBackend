package com.vet_saas.modules.delivery.mapper;

import com.vet_saas.modules.client.model.PerfilCliente;
import com.vet_saas.modules.client.repository.ClienteRepository;
import com.vet_saas.modules.delivery.dto.response.DeliveryResponseDTO;
import com.vet_saas.modules.delivery.dto.response.RepartidorResponseDTO;
import com.vet_saas.modules.delivery.model.Delivery;
import com.vet_saas.modules.delivery.model.Repartidor;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class DeliveryMapper {

    @Autowired
    protected ClienteRepository clienteRepository;

    @Mapping(target = "idDelivery",       source = "id")
    @Mapping(target = "ordenId",            source = "orden.id")
    @Mapping(target = "repartidorId",       source = "repartidor.idRepartidor")
    @Mapping(target = "repartidorNombre",   expression = "java(getRepartidorNombre(delivery))")
    @Mapping(target = "repartidorFoto",     source = "repartidor.fotoPerfil")
    @Mapping(target = "repartidorTelefono", source = "repartidor.telefono")
    @Mapping(target = "repartidorVehiculo", expression = "java(delivery.getRepartidor() != null ? delivery.getRepartidor().getTipoVehiculo().name() : null)")
    @Mapping(target = "repartidorLat",      source = "repartidor.ubicacionLat")
    @Mapping(target = "repartidorLng",      source = "repartidor.ubicacionLng")
    @Mapping(target = "repartidorCalificacionPromedio", source = "repartidor.calificacionPromedio")
    @Mapping(target = "clienteNombre",      expression = "java(getClienteNombre(delivery))")
    @Mapping(target = "clienteTelefono",    expression = "java(getClienteTelefono(delivery))")
    @Mapping(target = "otpCliente",         ignore = true)
    public abstract DeliveryResponseDTO toResponseDTO(Delivery delivery);

    /** Copia de toResponseDTO pero con OTP incluido (solo al crear) */
    @Mapping(target = "idDelivery",       source = "delivery.id")
    @Mapping(target = "ordenId",            source = "delivery.orden.id")
    @Mapping(target = "repartidorId",       source = "delivery.repartidor.idRepartidor")
    @Mapping(target = "repartidorNombre",   expression = "java(getRepartidorNombre(delivery))")
    @Mapping(target = "repartidorFoto",     source = "delivery.repartidor.fotoPerfil")
    @Mapping(target = "repartidorTelefono", source = "delivery.repartidor.telefono")
    @Mapping(target = "repartidorLat",      source = "delivery.repartidor.ubicacionLat")
    @Mapping(target = "repartidorLng",      source = "delivery.repartidor.ubicacionLng")
    @Mapping(target = "repartidorCalificacionPromedio", source = "delivery.repartidor.calificacionPromedio")
    @Mapping(target = "repartidorVehiculo", ignore = true)
    @Mapping(target = "clienteNombre",      expression = "java(getClienteNombre(delivery))")
    @Mapping(target = "clienteTelefono",    expression = "java(getClienteTelefono(delivery))")
    @Mapping(target = "otpCliente",         source = "otp")
    public abstract DeliveryResponseDTO toResponseDTOConOTP(Delivery delivery, String otp);

    @Mapping(target = "fotoPerfil", source = "fotoPerfil")
    public abstract RepartidorResponseDTO toRepartidorDTO(Repartidor repartidor);

    protected String getRepartidorNombre(Delivery delivery) {
        if (delivery.getRepartidor() == null) return null;
        return delivery.getRepartidor().getNombres() + " " + delivery.getRepartidor().getApellidos();
    }

    protected String getClienteNombre(Delivery delivery) {
        if (delivery.getOrden() == null || delivery.getOrden().getUsuarioCliente() == null) return "Cliente";
        return clienteRepository.findByUsuarioId(delivery.getOrden().getUsuarioCliente().getId())
                .map(p -> p.getNombres() + " " + p.getApellidos())
                .orElse("Cliente");
    }

    protected String getClienteTelefono(Delivery delivery) {
        if (delivery.getOrden() == null || delivery.getOrden().getUsuarioCliente() == null) return null;
        return clienteRepository.findByUsuarioId(delivery.getOrden().getUsuarioCliente().getId())
                .map(PerfilCliente::getTelefono)
                .orElse(null);
    }
}
