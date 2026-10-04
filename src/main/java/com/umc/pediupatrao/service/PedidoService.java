package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.repository.PedidoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;

@Service
public class PedidoService {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    public Pedido criarPedido(Pedido pedido, String responsavel) {
        if (pedido.getId() != null) {
            throw new IllegalArgumentException("O cadastro de um novo pedido não pode informar ID.");
        }
        if (pedido.getStatus() != null && !pedido.getStatus().isBlank()
                && !"RECEBIDO".equals(pedido.getStatus())) {
            throw new IllegalArgumentException("Um novo pedido deve iniciar com o status RECEBIDO.");
        }
        if (pedido.getClienteId() == null || pedido.getClienteId().isBlank()
                || !clienteRepository.existsById(pedido.getClienteId())) {
            throw new IllegalArgumentException("Selecione um cliente cadastrado.");
        }
        if (pedido.getItens() == null || pedido.getItens().isEmpty()) {
            throw new IllegalArgumentException("Adicione ao menos um item ao pedido.");
        }

        List<ItemPedido> itens = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ItemPedido informado : pedido.getItens()) {
            if (informado == null || informado.getProdutoId() == null || informado.getProdutoId().isBlank()) {
                throw new IllegalArgumentException("Selecione um produto para cada item.");
            }
            if (informado.getQuantidade() <= 0) {
                throw new IllegalArgumentException("A quantidade de cada item deve ser maior que zero.");
            }
            Produto produto = produtoRepository.findById(informado.getProdutoId())
                    .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado."));
            if (produto.getPreco() == null || !Double.isFinite(produto.getPreco()) || produto.getPreco() < 0) {
                throw new IllegalArgumentException("O produto deve ter um preço válido.");
            }

            ItemPedido item = new ItemPedido();
            item.setProdutoId(produto.getId());
            item.setNomeProduto(produto.getNome());
            item.setQuantidade(informado.getQuantidade());
            item.setPrecoUnitario(BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP));
            itens.add(item);
            total = total.add(item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade())));
        }

        pedido.setItens(itens);
        pedido.setDescontoPercentual(BigDecimal.ZERO);
        pedido.setValorTotal(total.setScale(2, RoundingMode.HALF_UP));
        pedido.setResponsavelRegistro(responsavel);
        pedido.setDataHoraEntrada(LocalDateTime.now());
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
