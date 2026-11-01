package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardRankingTest {
    @Mock PedidoRepository pedidoRepository;
    @InjectMocks DashboardService service;
    private final LocalDate inicio = LocalDate.of(2026, 10, 1);
    private final LocalDate fim = LocalDate.of(2026, 10, 31);

    @Test
    void somaUnidadesPorProdutoSomenteDeFinalizadosEPreservaNomeMaisRecente() {
        consultar(List.of(
                pedido("FINALIZADO", inicio, item("pizza", "Pizza antiga", 2), item("bebida", "Suco", 1)),
                pedido("CANCELADO", inicio, item("bebida", "Suco", 100)),
                pedido("PRONTO", inicio, item("bebida", "Suco", 50)),
                pedido("FINALIZADO", fim, item("pizza", "Pizza atual", 3), item("pizza", "Pizza atual", 1))));

        var ranking = service.consultarProdutosMaisVendidos(inicio, fim);

        assertThat(ranking).hasSize(2);
        assertThat(ranking.get(0).produtoId()).isEqualTo("pizza");
        assertThat(ranking.get(0).nomeProduto()).isEqualTo("Pizza atual");
        assertThat(ranking.get(0).quantidadeVendida()).isEqualTo(6);
        assertThat(ranking.get(1).produtoId()).isEqualTo("bebida");
        assertThat(ranking.get(1).quantidadeVendida()).isEqualTo(1);
        verify(pedidoRepository).findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(
                inicio.atStartOfDay(), fim.plusDays(1).atStartOfDay());
    }

    @Test
    void separaProdutosComMesmoNomeEDesempataPeloIdentificador() {
        consultar(List.of(pedido("FINALIZADO", inicio,
                item("b", "Pizza", 2), item("a", "Pizza", 2))));
        var ranking = service.consultarProdutosMaisVendidos(inicio, fim);
        assertThat(ranking).extracting(produto -> produto.produtoId()).containsExactly("a", "b");
        assertThat(ranking).allSatisfy(produto -> assertThat(produto.quantidadeVendida()).isEqualTo(2));
    }

    @Test
    void limitaAosCincoProdutosComMaisUnidadesVendidas() {
        consultar(List.of(pedido("FINALIZADO", inicio, item("a", "A", 1), item("b", "B", 2),
                item("c", "C", 3), item("d", "D", 4), item("e", "E", 5), item("f", "F", 6))));
        var ranking = service.consultarProdutosMaisVendidos(inicio, fim);
        assertThat(ranking).extracting(produto -> produto.produtoId()).containsExactly("f", "e", "d", "c", "b");
        assertThat(ranking).extracting(produto -> produto.quantidadeVendida()).containsExactly(6L, 5L, 4L, 3L, 2L);
    }

    @Test
    void retornaListaVaziaSemPedidosOuSemFinalizados() {
        consultar(List.of());
        assertThat(service.consultarProdutosMaisVendidos(inicio, inicio)).isEmpty();
        verify(pedidoRepository).findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(
                inicio.atStartOfDay(), inicio.plusDays(1).atStartOfDay());
        consultar(List.of(pedido("RECEBIDO", inicio), pedido("CANCELADO", fim)));
        assertThat(service.consultarProdutosMaisVendidos(inicio, fim)).isEmpty();
    }

    @Test
    void rejeitaPeriodoInvalidoAntesDeConsultarBanco() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.consultarProdutosMaisVendidos(fim, inicio));
        assertThatIllegalArgumentException().isThrownBy(() -> service.consultarProdutosMaisVendidos(null, fim));
        assertThatIllegalArgumentException().isThrownBy(() -> service.consultarProdutosMaisVendidos(inicio, null));
        assertThatIllegalArgumentException().isThrownBy(() -> service.consultarProdutosMaisVendidos(inicio, LocalDate.MAX));
        verifyNoInteractions(pedidoRepository);
    }

    @Test
    void informaInconsistenciaEmVezDeExibirRankingParcial() {
        Pedido pedido = pedido("FINALIZADO", inicio);
        consultar(List.of(pedido));
        assertThatIllegalStateException().isThrownBy(() -> service.consultarProdutosMaisVendidos(inicio, fim));
        pedido.setItens(null);
        assertThatIllegalStateException().isThrownBy(() -> service.consultarProdutosMaisVendidos(inicio, fim));
        pedido.setItens(List.of(item("pizza", "Pizza", 0)));
        assertThatIllegalStateException().isThrownBy(() -> service.consultarProdutosMaisVendidos(inicio, fim));
        pedido.setItens(List.of(item(null, "Pizza", 2)));
        assertThatIllegalStateException().isThrownBy(() -> service.consultarProdutosMaisVendidos(inicio, fim));
    }

    private void consultar(List<Pedido> pedidos) {
        when(pedidoRepository.findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(any(), any()))
                .thenReturn(pedidos);
    }

    private Pedido pedido(String status, LocalDate data, ItemPedido... itens) {
        Pedido pedido = new Pedido();
        pedido.setStatus(status);
        pedido.setDataHoraEntrada(data.atStartOfDay());
        pedido.setItens(List.of(itens));
        return pedido;
    }

    private ItemPedido item(String id, String nome, int quantidade) {
        ItemPedido item = new ItemPedido();
        item.setProdutoId(id);
        item.setNomeProduto(nome);
        item.setQuantidade(quantidade);
        return item;
    }
}
