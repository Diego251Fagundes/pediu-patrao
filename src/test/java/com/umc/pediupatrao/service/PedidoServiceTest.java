package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.time.LocalDateTime;
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

    @Test
    void percorreFluxoDeEntregaERetiradaPreservandoEntradaESaida() {
        when(pedidoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        LocalDateTime entrada = LocalDateTime.of(2026, 10, 3, 18, 0);
        for (String saida : List.of("SAIU_PARA_ENTREGA", "RETIRADO")) {
            Pedido pedido = new Pedido();
            pedido.setId("pedido-1");
            pedido.setStatus("RECEBIDO");
            pedido.setDataHoraEntrada(entrada);
            pedido.setResponsavelRegistro("atendente");
            when(pedidoRepository.findById("pedido-1")).thenReturn(Optional.of(pedido));

            service.atualizarStatus("pedido-1", "EM_PREPARACAO", "gerente");
            service.atualizarStatus("pedido-1", "PRONTO", "gerente");
            assertThat(pedido.getDataHoraSaida()).isNull();
            assertThat(pedido.getResponsavelSaida()).isNull();

            service.atualizarStatus("pedido-1", saida, "gerente");
            LocalDateTime horarioSaida = pedido.getDataHoraSaida();
            assertThat(horarioSaida).isNotNull();
            assertThat(pedido.getResponsavelSaida()).isEqualTo("gerente");

            service.atualizarStatus("pedido-1", "FINALIZADO", "outro-gerente");
            assertThat(pedido.getStatus()).isEqualTo("FINALIZADO");
            assertThat(pedido.getDataHoraEntrada()).isEqualTo(entrada);
            assertThat(pedido.getResponsavelRegistro()).isEqualTo("atendente");
            assertThat(pedido.getDataHoraSaida()).isEqualTo(horarioSaida);
            assertThat(pedido.getResponsavelSaida()).isEqualTo("gerente");
        }
        verify(pedidoRepository, times(8)).save(any());
    }

    @Test
    void rejeitaSaltosRetornosEstadosInvalidosECancelamentoPelaTrocaDeStatus() {
        String[][] transicoesInvalidas = {
                {"RECEBIDO", "RECEBIDO"},
                {"EM_PREPARACAO", "FINALIZADO"},
                {"PRONTO", "RECEBIDO"},
                {"PRONTO", "CANCELADO"},
                {"CANCELADO", "EM_PREPARACAO"},
                {"FINALIZADO", "RETIRADO"},
                {"SAIU_PARA_ENTREGA", "RETIRADO"},
                {"RECEBIDO", "INEXISTENTE"},
                {"RECEBIDO", null},
                {null, "EM_PREPARACAO"}
        };
        for (String[] transicao : transicoesInvalidas) {
            Pedido pedido = new Pedido();
            pedido.setStatus(transicao[0]);
            when(pedidoRepository.findById("pedido-1")).thenReturn(Optional.of(pedido));
            assertThatThrownBy(() -> service.atualizarStatus("pedido-1", transicao[1], "gerente"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(pedido.getStatus()).isEqualTo(transicao[0]);
            assertThat(pedido.getDataHoraSaida()).isNull();
        }
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void rejeitaAtualizacaoDePedidoInexistente() {
        assertThatThrownBy(() -> service.atualizarStatus("inexistente", "EM_PREPARACAO", "gerente"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Pedido não encontrado.");
        verify(pedidoRepository, never()).save(any());
    }

}
