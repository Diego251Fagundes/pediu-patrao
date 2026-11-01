package com.umc.pediupatrao.config;

import com.umc.pediupatrao.controller.DashboardController;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import java.math.BigDecimal;
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

    @Test
    void exigeAutenticacaoSemRedirecionarParaHtml() throws Exception {
        mvc.perform(get(ROTA)).andExpect(status().isUnauthorized());
        verifyNoInteractions(pedidoRepository);
    }

    @Test
    void atendenteNaoConsultaIndicadores() throws Exception {
        mvc.perform(get(ROTA).with(user("atendente").roles("ATENDENTE")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(pedidoRepository);
    }

    @ParameterizedTest
    @CsvSource({"2026-10-31,2026-10-01", "invalida,2026-10-31", "2026-02-30,2026-10-31"})
    void rejeitaDatasInvalidas(String inicio, String fim) throws Exception {
        mvc.perform(get(ROTA).with(user("gerente").roles("GERENTE"))
                        .param("dataInicio", inicio).param("dataFim", fim))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").isNotEmpty());
        verifyNoInteractions(pedidoRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dataInicio", "dataFim"})
    void exigeAmbasAsDatas(String parametro) throws Exception {
        mvc.perform(get(ROTA).with(user("gerente").roles("GERENTE")).param(parametro, "2026-10-01"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").isNotEmpty());
        verifyNoInteractions(pedidoRepository);
    }

    @Test
    void informaErroQuandoVendaEstaSemValor() throws Exception {
        Pedido pedido = new Pedido();
        pedido.setStatus("FINALIZADO");
        when(pedidoRepository.findByDataHoraEntradaGreaterThanEqualAndDataHoraEntradaLessThan(any(), any()))
                .thenReturn(List.of(pedido));
        mvc.perform(get(ROTA).with(user("gerente").roles("GERENTE"))
                        .param("dataInicio", "2026-10-01").param("dataFim", "2026-10-31"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.erro").isNotEmpty());
    }
}
