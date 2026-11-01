package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Import(DashboardService.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "PEDIUPATRAO_TEST_MONGODB_URI", matches = ".+")
class DashboardMongoTest {
    private static final String BANCO_TESTE = "pediupatrao_dashboard_test_" + UUID.randomUUID().toString().replace("-", "");
    @Autowired PedidoRepository repository;
    @Autowired DashboardService service;
    @Autowired MongoTemplate mongo;

    @DynamicPropertySource
    static void configurarBanco(DynamicPropertyRegistry propriedades) {
        propriedades.add("spring.data.mongodb.uri", () -> System.getenv("PEDIUPATRAO_TEST_MONGODB_URI"));
        propriedades.add("spring.data.mongodb.database", () -> BANCO_TESTE);
    }

    @AfterAll
    void removerSomenteBancoTemporario() {
        if (!BANCO_TESTE.equals(mongo.getDb().getName())) throw new IllegalStateException("Banco de teste inesperado.");
        mongo.getDb().drop();
    }

    @Test
    void consultaOsQuatroRecursosComPedidosPersistidosELimitesDoPeriodo() {
        Pedido primeiro = pedido("FINALIZADO", LocalDateTime.of(2026, 10, 1, 0, 0), "80.00");
        primeiro.setDescontoPercentual(new BigDecimal("20"));
        primeiro.setItens(List.of(item("pizza", "Pizza", 2), item("bebida", "Suco", 1)));
        Pedido ultimo = pedido("FINALIZADO", LocalDateTime.of(2026, 10, 3, 23, 59, 59), "19.99");
        ultimo.setItens(List.of(item("pizza", "Pizza", 3)));
        repository.saveAll(List.of(primeiro, ultimo,
                pedido("RECEBIDO", LocalDateTime.of(2026, 10, 2, 12, 0), "50.00"),
                pedido("CANCELADO", LocalDateTime.of(2026, 10, 2, 14, 0), "200.00"),
                pedido("FINALIZADO", LocalDateTime.of(2026, 9, 30, 23, 59, 59), "500.00"),
                pedido("FINALIZADO", LocalDateTime.of(2026, 10, 4, 0, 0), "800.00"),
                pedido("FINALIZADO", null, "900.00")));
        LocalDate inicio = LocalDate.of(2026, 10, 1);
        LocalDate fim = LocalDate.of(2026, 10, 3);

        var indicadores = service.consultarIndicadores(inicio, fim);
        assertThat(indicadores.totalPedidos()).isEqualTo(4);
        assertThat(indicadores.pedidosFinalizados()).isEqualTo(2);
        assertThat(indicadores.pedidosCancelados()).isEqualTo(1);
        assertThat(indicadores.valorVendas()).isEqualByComparingTo("99.99");
        assertThat(indicadores.ticketMedio()).isEqualByComparingTo("50.00");
        var vendas = service.consultarVendasDiarias(inicio, fim);
        assertThat(vendas).hasSize(3);
        assertThat(vendas.get(0).valorVendas()).isEqualByComparingTo("80.00");
        assertThat(vendas.get(1).quantidadePedidos()).isZero();
        assertThat(vendas.get(2).valorVendas()).isEqualByComparingTo("19.99");
        assertThat(service.consultarSituacoes(inicio, fim)).filteredOn(s -> "CANCELADO".equals(s.status()))
                .singleElement().satisfies(s -> assertThat(s.quantidadePedidos()).isEqualTo(1));
        var ranking = service.consultarProdutosMaisVendidos(inicio, fim);
        assertThat(ranking).hasSize(2);
        assertThat(ranking.get(0).produtoId()).isEqualTo("pizza");
        assertThat(ranking.get(0).quantidadeVendida()).isEqualTo(5);
        assertThat(ranking.get(1).produtoId()).isEqualTo("bebida");
        assertThat(ranking.get(1).quantidadeVendida()).isEqualTo(1);
        assertThat(service.consultarIndicadores(fim.plusDays(2), fim.plusDays(2)).totalPedidos()).isZero();
        assertThat(service.consultarProdutosMaisVendidos(fim.plusDays(2), fim.plusDays(2))).isEmpty();
    }

    private Pedido pedido(String status, LocalDateTime data, String valor) {
        Pedido pedido = new Pedido();
        pedido.setStatus(status);
        pedido.setDataHoraEntrada(data);
        pedido.setValorTotal(new BigDecimal(valor));
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
