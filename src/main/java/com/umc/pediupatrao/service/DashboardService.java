package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.IndicadoresDashboard;
import com.umc.pediupatrao.dto.ProdutoMaisVendido;
import com.umc.pediupatrao.dto.SituacaoPedido;
import com.umc.pediupatrao.dto.VendaDiaria;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    private final PedidoRepository pedidoRepository;

    public DashboardService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    public IndicadoresDashboard consultarIndicadores(LocalDate dataInicio, LocalDate dataFim) {
        List<Pedido> pedidos = consultarPedidosNoPeriodo(dataInicio, dataFim);

        long finalizados = 0;
        long cancelados = 0;
        BigDecimal valorVendas = BigDecimal.ZERO;
        for (Pedido pedido : pedidos) {
            if ("FINALIZADO".equals(pedido.getStatus())) {
                validarValorTotal(pedido);
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

    public List<VendaDiaria> consultarVendasDiarias(LocalDate dataInicio, LocalDate dataFim) {
        List<Pedido> pedidos = consultarPedidosNoPeriodo(dataInicio, dataFim);
        Map<LocalDate, VendaDiaria> vendas = new LinkedHashMap<>();
        for (LocalDate dia = dataInicio; !dia.isAfter(dataFim); dia = dia.plusDays(1)) {
            vendas.put(dia, new VendaDiaria(dia, 0, new BigDecimal("0.00")));
        }

        for (Pedido pedido : pedidos) {
            if ("FINALIZADO".equals(pedido.getStatus())) {
                validarValorTotal(pedido);
                // Mantém a mesma data de referência utilizada pelos indicadores.
                LocalDate dia = pedido.getDataHoraEntrada().toLocalDate();
                VendaDiaria anterior = vendas.get(dia);
                vendas.put(dia, new VendaDiaria(dia, anterior.quantidadePedidos() + 1,
                        anterior.valorVendas().add(pedido.getValorTotal()).setScale(2, RoundingMode.HALF_UP)));
            }
        }
        return new ArrayList<>(vendas.values());
    }

    public List<SituacaoPedido> consultarSituacoes(LocalDate dataInicio, LocalDate dataFim) {
        List<Pedido> pedidos = consultarPedidosNoPeriodo(dataInicio, dataFim);
        Map<String, Long> quantidades = new LinkedHashMap<>();
        for (String status : List.of("RECEBIDO", "EM_PREPARACAO", "PRONTO", "SAIU_PARA_ENTREGA",
                "RETIRADO", "FINALIZADO", "CANCELADO")) {
            quantidades.put(status, 0L);
        }
        for (Pedido pedido : pedidos) {
            quantidades.merge(pedido.getStatus(), 1L, Long::sum);
        }
        return quantidades.entrySet().stream()
                .map(entrada -> new SituacaoPedido(entrada.getKey(), entrada.getValue()))
                .toList();
    }

    public List<ProdutoMaisVendido> consultarProdutosMaisVendidos(LocalDate dataInicio, LocalDate dataFim) {
        List<Pedido> finalizados = consultarPedidosNoPeriodo(dataInicio, dataFim).stream()
                .filter(pedido -> "FINALIZADO".equals(pedido.getStatus()))
                .sorted(Comparator.comparing(Pedido::getDataHoraEntrada).reversed()
                        .thenComparing(Pedido::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Map<String, ProdutoMaisVendido> produtos = new LinkedHashMap<>();
        for (Pedido pedido : finalizados) {
            if (pedido.getItens() == null || pedido.getItens().isEmpty()) {
                throw new IllegalStateException("Pedido finalizado sem itens.");
            }
            for (ItemPedido item : pedido.getItens()) {
                if (item == null || item.getProdutoId() == null || item.getProdutoId().isBlank()
                        || item.getNomeProduto() == null || item.getNomeProduto().isBlank()
                        || item.getQuantidade() <= 0) {
                    throw new IllegalStateException("Pedido finalizado com item inválido.");
                }
                // Preserva o nome do registro mais recente, mesmo se o catálogo tiver sido alterado.
                produtos.merge(item.getProdutoId(), new ProdutoMaisVendido(
                        item.getProdutoId(), item.getNomeProduto(), item.getQuantidade()),
                        (anterior, atual) -> new ProdutoMaisVendido(anterior.produtoId(), anterior.nomeProduto(),
                                anterior.quantidadeVendida() + atual.quantidadeVendida()));
            }
        }
        return produtos.values().stream()
                .sorted(Comparator.comparingLong(ProdutoMaisVendido::quantidadeVendida).reversed()
                        .thenComparing(ProdutoMaisVendido::produtoId))
                .limit(5)
                .toList();
    }

    private List<Pedido> consultarPedidosNoPeriodo(LocalDate dataInicio, LocalDate dataFim) {
        if (dataInicio == null || dataFim == null || dataInicio.isAfter(dataFim)
                || LocalDate.MAX.equals(dataFim)) {
            throw new IllegalArgumentException("Informe um período válido com a data inicial anterior ou igual à final.");
        }
        // O limite exclusivo inclui todos os horários da data final.
        return pedidoRepository.findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(
                dataInicio.atStartOfDay(), dataFim.plusDays(1).atStartOfDay());
    }

    private void validarValorTotal(Pedido pedido) {
        if (pedido.getValorTotal() == null || pedido.getValorTotal().signum() < 0) {
            throw new IllegalStateException("Pedido finalizado sem valor total válido.");
        }
    }
}
