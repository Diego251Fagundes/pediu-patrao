package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.IndicadoresDashboard;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    private final PedidoRepository pedidoRepository;

    public DashboardService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    public IndicadoresDashboard consultarIndicadores(LocalDate dataInicio, LocalDate dataFim) {
        if (dataInicio == null || dataFim == null || dataInicio.isAfter(dataFim)
                || LocalDate.MAX.equals(dataFim)) {
            throw new IllegalArgumentException("Informe um período válido com a data inicial anterior ou igual à final.");
        }

        // O limite exclusivo inclui todos os horários da data final.
        List<Pedido> pedidos = pedidoRepository
                .findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(
                        dataInicio.atStartOfDay(), dataFim.plusDays(1).atStartOfDay());

        long finalizados = 0;
        long cancelados = 0;
        BigDecimal valorVendas = BigDecimal.ZERO;
        for (Pedido pedido : pedidos) {
            if ("FINALIZADO".equals(pedido.getStatus())) {
                if (pedido.getValorTotal() == null || pedido.getValorTotal().signum() < 0) {
                    throw new IllegalStateException("Pedido finalizado sem valor total válido.");
                }
                finalizados++;
                // O valor persistido já inclui o desconto do pedido.
                valorVendas = valorVendas.add(pedido.getValorTotal());
            } else if ("CANCELADO".equals(pedido.getStatus())) {
                cancelados++;
            }
        }

        valorVendas = valorVendas.setScale(2, RoundingMode.HALF_UP);
        BigDecimal ticketMedio = finalizados == 0 ? new BigDecimal("0.00")
                : valorVendas.divide(BigDecimal.valueOf(finalizados), 2, RoundingMode.HALF_UP);
        return new IndicadoresDashboard(pedidos.size(), finalizados, cancelados, valorVendas, ticketMedio);
    }
}
