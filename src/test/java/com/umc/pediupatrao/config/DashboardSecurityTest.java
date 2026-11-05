package com.umc.pediupatrao.config;

import com.umc.pediupatrao.controller.DashboardController;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, DashboardService.class})
class DashboardSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean UsuarioDetailsService usuarioDetailsService;
    @MockitoBean PedidoRepository pedidoRepository;

    private static final String ROTA = "/api/dashboard/indicadores";

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "GERENTE"})
    void consultaIndicadoresEmJson(String perfil) throws Exception {
        Pedido pedido = new Pedido();
        pedido.setStatus("FINALIZADO");
        pedido.setValorTotal(new BigDecimal("45.90"));
        when(pedidoRepository.findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(any(), any()))
                .thenReturn(List.of(pedido));
        mvc.perform(get(ROTA).with(user("usuario").roles(perfil))
                        .param("dataInicio", "2026-10-01").param("dataFim", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalPedidos").value(1))
                .andExpect(jsonPath("$.pedidosFinalizados").value(1))
                .andExpect(jsonPath("$.pedidosCancelados").value(0))
                .andExpect(jsonPath("$.valorVendas").value(45.90))
                .andExpect(jsonPath("$.ticketMedio").value(45.90));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/dashboard/indicadores", "/api/dashboard/vendas", "/api/dashboard/situacoes",
        "/api/dashboard/produtos-mais-vendidos"})
    void exigeAutenticacaoSemRedirecionarParaHtml(String rota) throws Exception {
        mvc.perform(get(rota)).andExpect(status().isUnauthorized());
        verifyNoInteractions(pedidoRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/dashboard/indicadores", "/api/dashboard/vendas", "/api/dashboard/situacoes",
        "/api/dashboard/produtos-mais-vendidos"})
    void atendenteNaoConsultaDashboard(String rota) throws Exception {
        mvc.perform(get(rota).with(user("atendente").roles("ATENDENTE")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(pedidoRepository);
    }

    @ParameterizedTest
    @CsvSource({"2026-10-31,2026-10-01", "invalida,2026-10-31", "2026-02-30,2026-10-31"})
    void rejeitaDatasInvalidas(String inicio, String fim) throws Exception {
        for (String rota : List.of(ROTA, "/api/dashboard/vendas", "/api/dashboard/situacoes",
                "/api/dashboard/produtos-mais-vendidos")) {
            mvc.perform(get(rota).with(user("gerente").roles("GERENTE"))
                        .param("dataInicio", inicio).param("dataFim", fim))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").isNotEmpty());
        }
        verifyNoInteractions(pedidoRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dataInicio", "dataFim"})
    void exigeAmbasAsDatas(String parametro) throws Exception {
        for (String rota : List.of(ROTA, "/api/dashboard/vendas", "/api/dashboard/situacoes",
                "/api/dashboard/produtos-mais-vendidos")) {
            mvc.perform(get(rota).with(user("gerente").roles("GERENTE")).param(parametro, "2026-10-01"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").isNotEmpty());
        }
        verifyNoInteractions(pedidoRepository);
    }

    @Test
    void informaErroQuandoVendaEstaSemValor() throws Exception {
        Pedido pedido = new Pedido();
        pedido.setStatus("FINALIZADO");
        when(pedidoRepository.findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(any(), any()))
                .thenReturn(List.of(pedido));
        for (String rota : List.of(ROTA, "/api/dashboard/vendas")) {
            mvc.perform(get(rota).with(user("gerente").roles("GERENTE"))
                        .param("dataInicio", "2026-10-01").param("dataFim", "2026-10-31"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.erro").isNotEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "GERENTE"})
    void consultaRankingEmJson(String perfil) throws Exception {
        ItemPedido item = new ItemPedido();
        item.setProdutoId("pizza");
        item.setNomeProduto("Pizza de queijo");
        item.setQuantidade(3);
        Pedido pedido = new Pedido();
        pedido.setStatus("FINALIZADO");
        pedido.setDataHoraEntrada(LocalDateTime.of(2026, 10, 2, 20, 30));
        pedido.setItens(List.of(item));
        when(pedidoRepository.findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(any(), any()))
                .thenReturn(List.of(pedido));
        mvc.perform(get("/api/dashboard/produtos-mais-vendidos").with(user("usuario").roles(perfil))
                        .param("dataInicio", "2026-10-01").param("dataFim", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].produtoId").value("pizza"))
                .andExpect(jsonPath("$[0].nomeProduto").value("Pizza de queijo"))
                .andExpect(jsonPath("$[0].quantidadeVendida").value(3));
    }

    @Test
    void retornaRankingVazioQuandoNaoHaVendas() throws Exception {
        when(pedidoRepository.findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(any(), any()))
                .thenReturn(List.of());
        mvc.perform(get("/api/dashboard/produtos-mais-vendidos").with(user("gerente").roles("GERENTE"))
                        .param("dataInicio", "2026-10-01").param("dataFim", "2026-10-31"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void informaErroQuandoPedidoFinalizadoEstaSemItens() throws Exception {
        Pedido pedido = new Pedido();
        pedido.setStatus("FINALIZADO");
        pedido.setDataHoraEntrada(LocalDateTime.of(2026, 10, 2, 20, 30));
        when(pedidoRepository.findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(any(), any()))
                .thenReturn(List.of(pedido));
        mvc.perform(get("/api/dashboard/produtos-mais-vendidos").with(user("gerente").roles("GERENTE"))
                        .param("dataInicio", "2026-10-01").param("dataFim", "2026-10-31"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.erro").isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "GERENTE"})
    void consultaSituacoesEmJson(String perfil) throws Exception {
        Pedido recebido = new Pedido();
        recebido.setStatus("RECEBIDO");
        Pedido cancelado = new Pedido();
        cancelado.setStatus("CANCELADO");
        when(pedidoRepository.findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(any(), any()))
                .thenReturn(List.of(recebido, cancelado));
        mvc.perform(get("/api/dashboard/situacoes").with(user("usuario").roles(perfil))
                        .param("dataInicio", "2026-10-01").param("dataFim", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].status").value("RECEBIDO"))
                .andExpect(jsonPath("$[0].quantidadePedidos").value(1))
                .andExpect(jsonPath("$[1].status").value("EM_PREPARACAO"))
                .andExpect(jsonPath("$[1].quantidadePedidos").value(0))
                .andExpect(jsonPath("$[2].status").value("PRONTO"))
                .andExpect(jsonPath("$[2].quantidadePedidos").value(0))
                .andExpect(jsonPath("$[3].status").value("SAIU_PARA_ENTREGA"))
                .andExpect(jsonPath("$[3].quantidadePedidos").value(0))
                .andExpect(jsonPath("$[4].status").value("RETIRADO"))
                .andExpect(jsonPath("$[4].quantidadePedidos").value(0))
                .andExpect(jsonPath("$[5].status").value("FINALIZADO"))
                .andExpect(jsonPath("$[5].quantidadePedidos").value(0))
                .andExpect(jsonPath("$[6].status").value("CANCELADO"))
                .andExpect(jsonPath("$[6].quantidadePedidos").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "GERENTE"})
    void consultaSerieDiariaEmJson(String perfil) throws Exception {
        Pedido pedido = new Pedido();
        pedido.setStatus("FINALIZADO");
        pedido.setValorTotal(new BigDecimal("45.90"));
        pedido.setDataHoraEntrada(LocalDateTime.of(2026, 10, 2, 20, 30));
        when(pedidoRepository.findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(any(), any()))
                .thenReturn(List.of(pedido));
        mvc.perform(get("/api/dashboard/vendas").with(user("usuario").roles(perfil))
                        .param("dataInicio", "2026-10-01").param("dataFim", "2026-10-02"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].data").value("2026-10-01"))
                .andExpect(jsonPath("$[0].quantidadePedidos").value(0))
                .andExpect(jsonPath("$[0].valorVendas").value(0.00))
                .andExpect(jsonPath("$[1].data").value("2026-10-02"))
                .andExpect(jsonPath("$[1].quantidadePedidos").value(1))
                .andExpect(jsonPath("$[1].valorVendas").value(45.90));
    }
}
