package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.repository.PedidoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class PedidoService {

    @Autowired
    private PedidoRepository pedidoRepository;

    public Pedido criarPedido(Pedido pedido) {
        if (pedido.getId() != null) {
            throw new IllegalArgumentException("O cadastro de um novo pedido não pode informar ID.");
        }
        if (pedido.getStatus() != null && !pedido.getStatus().isBlank()
                && !"RECEBIDO".equals(pedido.getStatus())) {
            throw new IllegalArgumentException("Um novo pedido deve iniciar com o status RECEBIDO.");
        }
        pedido.setStatus("RECEBIDO");
        return pedidoRepository.save(pedido);
    }

    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll();
    }

    public Pedido atualizarStatus(String id, String status) {
        Pedido pedido = pedidoRepository.findById(id).orElseThrow();
        pedido.setStatus(status);
        return pedidoRepository.save(pedido);
    }

}
