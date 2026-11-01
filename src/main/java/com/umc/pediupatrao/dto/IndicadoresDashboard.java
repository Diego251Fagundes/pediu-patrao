package com.umc.pediupatrao.dto;

import java.math.BigDecimal;

public record IndicadoresDashboard(
        long totalPedidos,
        long pedidosFinalizados,
        long pedidosCancelados,
        BigDecimal valorVendas,
        BigDecimal ticketMedio) {
}
