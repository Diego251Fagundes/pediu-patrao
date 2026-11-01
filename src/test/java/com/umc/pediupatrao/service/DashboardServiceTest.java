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

    private Pedido pedido(String status, String valor) {
        Pedido pedido = new Pedido();
        pedido.setStatus(status);
        pedido.setValorTotal(new BigDecimal(valor));
        return pedido;
    }
}
