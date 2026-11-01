package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Pedido;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.time.LocalDateTime;

public interface PedidoRepository extends MongoRepository<Pedido, String> {
    List<Pedido> findByClienteId(String clienteId);
    List<Pedido> findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(
            LocalDateTime inicio, LocalDateTime fimExclusivo);
}
