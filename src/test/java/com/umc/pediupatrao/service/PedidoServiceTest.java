package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {
    @Mock PedidoRepository pedidoRepository;
    @Mock ClienteRepository clienteRepository;
    @Mock ProdutoRepository produtoRepository;
    @InjectMocks PedidoService service;

    private ItemPedido item(String produtoId, int quantidade) {
        ItemPedido item = new ItemPedido();
        item.setProdutoId(produtoId);
        item.setQuantidade(quantidade);
        return item;
    }

    private Pedido pedido(List<ItemPedido> itens) {
        Pedido pedido = new Pedido();
        pedido.setClienteId("cliente-1");
        pedido.setItens(itens);
        return pedido;
    }

    private Produto produto(String id, double preco) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setNome(id);
        produto.setPreco(preco);
        return produto;
    }

    @Test
    void calculaVariosItensComCentavosEPreservaPrecosDoRegistro() {
        Produto pizza = produto("pizza", 19.90);
        Produto bebida = produto("bebida", 6.50);
        when(clienteRepository.existsById("cliente-1")).thenReturn(true);
        when(produtoRepository.findById("pizza")).thenReturn(Optional.of(pizza));
        when(produtoRepository.findById("bebida")).thenReturn(Optional.of(bebida));
        when(pedidoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Pedido salvo = service.criarPedido(pedido(List.of(item("pizza", 2), item("bebida", 3))), "atendente");
        assertThat(salvo.getValorTotal()).isEqualByComparingTo("59.30");
        pizza.setPreco(30.0);
        assertThat(salvo.getItens().get(0).getPrecoUnitario()).isEqualByComparingTo("19.90");
        assertThat(salvo.getResponsavelRegistro()).isEqualTo("atendente");
        assertThat(salvo.getDataHoraEntrada()).isNotNull();
    }

    @Test
    void rejeitaClienteInexistente() {
        assertThatThrownBy(() -> service.criarPedido(pedido(List.of(item("pizza", 1))), "atendente"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cliente cadastrado");
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void rejeitaPedidoSemItens() {
        when(clienteRepository.existsById("cliente-1")).thenReturn(true);
        assertThatThrownBy(() -> service.criarPedido(pedido(List.of()), "atendente"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ao menos um item");
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void rejeitaQuantidadeZero() {
        when(clienteRepository.existsById("cliente-1")).thenReturn(true);
        assertThatThrownBy(() -> service.criarPedido(pedido(List.of(item("pizza", 0))), "atendente"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maior que zero");
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void rejeitaProdutoInexistente() {
        when(clienteRepository.existsById("cliente-1")).thenReturn(true);
        assertThatThrownBy(() -> service.criarPedido(pedido(List.of(item("pizza", 1))), "atendente"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Produto não encontrado");
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void rejeitaProdutoComPrecoInvalido() {
        when(clienteRepository.existsById("cliente-1")).thenReturn(true);
        when(produtoRepository.findById("pizza")).thenReturn(Optional.of(produto("pizza", -1)));
        assertThatThrownBy(() -> service.criarPedido(pedido(List.of(item("pizza", 1))), "atendente"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("preço válido");
        verify(pedidoRepository, never()).save(any());
    }

}
