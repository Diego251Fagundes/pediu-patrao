package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.math.BigDecimal;
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
class DashboardServiceTest {
    @Mock PedidoRepository pedidoRepository;
    @InjectMocks DashboardService service;

    private final LocalDate inicio = LocalDate.of(2026, 10, 1);
    private final LocalDate fim = LocalDate.of(2026, 10, 31);

    @Test
    void calculaVendasSomenteDeFinalizadosSemDescontarNovamente() {
        Pedido comDesconto = pedido("FINALIZADO", "80.00");
        comDesconto.setDescontoPercentual(new BigDecimal("20"));
        when(pedidoRepository.findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(any(), any()))
                .thenReturn(List.of(comDesconto, pedido("FINALIZADO", "19.99"),
                        pedido("CANCELADO", "200.00"), pedido("RECEBIDO", "50.00")));

        var indicadores = service.consultarIndicadores(inicio, fim);

        assertThat(indicadores.totalPedidos()).isEqualTo(4);
        assertThat(indicadores.pedidosFinalizados()).isEqualTo(2);
        assertThat(indicadores.pedidosCancelados()).isEqualTo(1);
        assertThat(indicadores.valorVendas()).isEqualByComparingTo("99.99");
        assertThat(indicadores.ticketMedio()).isEqualByComparingTo("50.00");
        verify(pedidoRepository).findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(
                inicio.atStartOfDay(), LocalDate.of(2026, 11, 1).atStartOfDay());
    }

    @Test
    void retornaZerosSemPedidos() {
        when(pedidoRepository.findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(any(), any()))
                .thenReturn(List.of());
        var indicadores = service.consultarIndicadores(inicio, fim);
        assertThat(indicadores.totalPedidos()).isZero();
        assertThat(indicadores.pedidosFinalizados()).isZero();
        assertThat(indicadores.pedidosCancelados()).isZero();
        assertThat(indicadores.valorVendas()).isEqualByComparingTo("0.00");
        assertThat(indicadores.ticketMedio()).isEqualByComparingTo("0.00");
    }

    @Test
    void retornaVendasZeradasQuandoHaSomentePedidosAbertosECancelados() {
        when(pedidoRepository.findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(any(), any()))
                .thenReturn(List.of(pedido("PRONTO", "30.00"), pedido("CANCELADO", "40.00")));
        var indicadores = service.consultarIndicadores(inicio, fim);
        assertThat(indicadores.totalPedidos()).isEqualTo(2);
        assertThat(indicadores.pedidosCancelados()).isEqualTo(1);
        assertThat(indicadores.pedidosFinalizados()).isZero();
        assertThat(indicadores.valorVendas()).isEqualByComparingTo("0.00");
        assertThat(indicadores.ticketMedio()).isEqualByComparingTo("0.00");
    }

    @Test
    void aceitaConsultaDeUmUnicoDia() {
        when(pedidoRepository.findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(any(), any()))
                .thenReturn(List.of());
        service.consultarIndicadores(inicio, inicio);
        verify(pedidoRepository).findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(
                inicio.atStartOfDay(), inicio.plusDays(1).atStartOfDay());
    }

    @Test
    void rejeitaPeriodoInvalidoAntesDeConsultarBanco() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.consultarIndicadores(fim, inicio));
        assertThatIllegalArgumentException().isThrownBy(() -> service.consultarIndicadores(null, fim));
        assertThatIllegalArgumentException().isThrownBy(() -> service.consultarIndicadores(inicio, null));
        assertThatIllegalArgumentException().isThrownBy(() -> service.consultarIndicadores(inicio, LocalDate.MAX));
        verifyNoInteractions(pedidoRepository);
    }

    @Test
    void naoApresentaVendaIncompletaComoZero() {
        Pedido pedido = pedido("FINALIZADO", "10.00");
        pedido.setValorTotal(null);
        when(pedidoRepository.findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(any(), any()))
                .thenReturn(List.of(pedido));
        assertThatIllegalStateException().isThrownBy(() -> service.consultarIndicadores(inicio, fim));
        pedido.setValorTotal(new BigDecimal("-1.00"));
        assertThatIllegalStateException().isThrownBy(() -> service.consultarIndicadores(inicio, fim));
    }

    @Test
    void agrupaVendasPorDataDeEntradaEmOrdemComDiasZerados() {
        Pedido ultimo = pedido("FINALIZADO", "25.10");
        ultimo.setDataHoraEntrada(inicio.plusDays(2).atTime(23, 59, 59));
        Pedido primeiro = pedido("FINALIZADO", "80.00");
        primeiro.setDescontoPercentual(new BigDecimal("20"));
        primeiro.setDataHoraEntrada(inicio.atStartOfDay());
        Pedido segundo = pedido("FINALIZADO", "19.99");
        segundo.setDataHoraEntrada(inicio.atTime(18, 30));
        Pedido cancelado = pedido("CANCELADO", "200.00");
        cancelado.setDataHoraEntrada(inicio.plusDays(1).atTime(12, 0));
        Pedido aberto = pedido("RECEBIDO", "50.00");
        aberto.setDataHoraEntrada(inicio.plusDays(1).atTime(13, 0));
        when(pedidoRepository.findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(any(), any()))
                .thenReturn(List.of(ultimo, cancelado, primeiro, aberto, segundo));

        var vendas = service.consultarVendasDiarias(inicio, inicio.plusDays(2));

        assertThat(vendas).extracting(venda -> venda.data())
                .containsExactly(inicio, inicio.plusDays(1), inicio.plusDays(2));
        assertThat(vendas).extracting(venda -> venda.quantidadePedidos()).containsExactly(2L, 0L, 1L);
        assertThat(vendas.get(0).valorVendas()).isEqualByComparingTo("99.99");
        assertThat(vendas.get(1).valorVendas()).isEqualByComparingTo("0.00");
        assertThat(vendas.get(2).valorVendas()).isEqualByComparingTo("25.10");
        verify(pedidoRepository).findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(
                inicio.atStartOfDay(), inicio.plusDays(3).atStartOfDay());

        var indicadores = service.consultarIndicadores(inicio, inicio.plusDays(2));
        assertThat(vendas.stream().mapToLong(venda -> venda.quantidadePedidos()).sum())
                .isEqualTo(indicadores.pedidosFinalizados());
        assertThat(vendas.stream().map(venda -> venda.valorVendas()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(indicadores.valorVendas());
    }

    @Test
    void retornaSerieZeradaSemVendasInclusiveParaUmUnicoDia() {
        when(pedidoRepository.findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(any(), any()))
                .thenReturn(List.of());
        var vendas = service.consultarVendasDiarias(inicio, inicio.plusDays(2));
        assertThat(vendas).hasSize(3).allSatisfy(venda -> {
            assertThat(venda.quantidadePedidos()).isZero();
            assertThat(venda.valorVendas()).isEqualByComparingTo("0.00");
        });
        var unicoDia = service.consultarVendasDiarias(inicio, inicio);
        assertThat(unicoDia).hasSize(1);
        assertThat(unicoDia.get(0).data()).isEqualTo(inicio);
        assertThat(unicoDia.get(0).valorVendas()).isEqualByComparingTo("0.00");
    }

    @Test
    void rejeitaPeriodoInvalidoNaSerieAntesDeConsultarBanco() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.consultarVendasDiarias(fim, inicio));
        assertThatIllegalArgumentException().isThrownBy(() -> service.consultarVendasDiarias(null, fim));
        assertThatIllegalArgumentException().isThrownBy(() -> service.consultarVendasDiarias(inicio, null));
        assertThatIllegalArgumentException().isThrownBy(() -> service.consultarVendasDiarias(inicio, LocalDate.MAX));
        verifyNoInteractions(pedidoRepository);
    }

    @Test
    void naoApresentaSerieComValorInvalidoComoZero() {
        Pedido pedido = pedido("FINALIZADO", "10.00");
        pedido.setDataHoraEntrada(inicio.atStartOfDay());
        pedido.setValorTotal(null);
        when(pedidoRepository.findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(any(), any()))
                .thenReturn(List.of(pedido));
        assertThatIllegalStateException().isThrownBy(() -> service.consultarVendasDiarias(inicio, fim));
        pedido.setValorTotal(new BigDecimal("-1.00"));
        assertThatIllegalStateException().isThrownBy(() -> service.consultarVendasDiarias(inicio, fim));
    }

    @Test
    void contaPedidosEmTodasAsSituacoesIncluindoCancelados() {
        when(pedidoRepository.findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(any(), any()))
                .thenReturn(List.of(pedido("RECEBIDO", "10.00"), pedido("RECEBIDO", "20.00"),
                        pedido("EM_PREPARACAO", "30.00"), pedido("PRONTO", "40.00"),
                        pedido("SAIU_PARA_ENTREGA", "50.00"), pedido("RETIRADO", "60.00"),
                        pedido("FINALIZADO", "70.00"), pedido("CANCELADO", "80.00")));

        var situacoes = service.consultarSituacoes(inicio, fim);

        assertThat(situacoes).extracting(situacao -> situacao.status()).containsExactly(
                "RECEBIDO", "EM_PREPARACAO", "PRONTO", "SAIU_PARA_ENTREGA", "RETIRADO", "FINALIZADO", "CANCELADO");
        assertThat(situacoes).extracting(situacao -> situacao.quantidadePedidos())
                .containsExactly(2L, 1L, 1L, 1L, 1L, 1L, 1L);
        var indicadores = service.consultarIndicadores(inicio, fim);
        assertThat(situacoes.stream().mapToLong(situacao -> situacao.quantidadePedidos()).sum())
                .isEqualTo(indicadores.totalPedidos());
        verify(pedidoRepository, times(2)).findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(
                inicio.atStartOfDay(), fim.plusDays(1).atStartOfDay());
    }

    @Test
    void retornaSituacoesZeradasQuandoNaoHaPedidosNoPeriodo() {
        when(pedidoRepository.findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(any(), any()))
                .thenReturn(List.of());
        var situacoes = service.consultarSituacoes(inicio, inicio);
        assertThat(situacoes).hasSize(7).allSatisfy(situacao ->
                assertThat(situacao.quantidadePedidos()).isZero());
        verify(pedidoRepository).findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(
                inicio.atStartOfDay(), inicio.plusDays(1).atStartOfDay());
    }

    @Test
    void mantemSituacoesSemPedidosComZeroAoContarUmaSituacao() {
        when(pedidoRepository.findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(any(), any()))
                .thenReturn(List.of(pedido("PRONTO", "40.00")));
        var situacoes = service.consultarSituacoes(inicio, fim);
        assertThat(situacoes).hasSize(7).allSatisfy(situacao ->
                assertThat(situacao.quantidadePedidos()).isEqualTo("PRONTO".equals(situacao.status()) ? 1L : 0L));
    }

    @Test
    void rejeitaPeriodoInvalidoNasSituacoesAntesDeConsultarBanco() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.consultarSituacoes(fim, inicio));
        assertThatIllegalArgumentException().isThrownBy(() -> service.consultarSituacoes(null, fim));
        assertThatIllegalArgumentException().isThrownBy(() -> service.consultarSituacoes(inicio, null));
        assertThatIllegalArgumentException().isThrownBy(() -> service.consultarSituacoes(inicio, LocalDate.MAX));
        verifyNoInteractions(pedidoRepository);
    }

    private Pedido pedido(String status, String valor) {
        Pedido pedido = new Pedido();
        pedido.setStatus(status);
        pedido.setValorTotal(new BigDecimal(valor));
        return pedido;
    }
}
