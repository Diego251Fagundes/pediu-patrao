package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {
    @Mock PedidoRepository pedidoRepository;
    @Mock ClienteRepository clienteRepository;
    @Mock ProdutoRepository produtoRepository;
    @Mock AuditoriaService auditoriaService;
    @InjectMocks PedidoService service;

    private final Authentication gerente = new UsernamePasswordAuthenticationToken("gerente", null,
            List.of(new SimpleGrantedAuthority("ROLE_GERENTE")));
    private final Authentication atendente = new UsernamePasswordAuthenticationToken("atendente", null,
            List.of(new SimpleGrantedAuthority("ROLE_ATENDENTE")));
    private final Authentication outroGerente = new UsernamePasswordAuthenticationToken("outro-gerente", null,
            List.of(new SimpleGrantedAuthority("ROLE_GERENTE")));

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
        Pedido salvo = service.criarPedido(pedido(List.of(item("pizza", 2), item("bebida", 3))), atendente);
        assertThat(salvo.getValorTotal()).isEqualByComparingTo("59.30");
        pizza.setPreco(30.0);
        assertThat(salvo.getItens().get(0).getPrecoUnitario()).isEqualByComparingTo("19.90");
        assertThat(salvo.getResponsavelRegistro()).isEqualTo("atendente");
        assertThat(salvo.getDataHoraEntrada()).isNotNull();
        verify(auditoriaService).registrar(eq("CADASTRO_PEDIDO"), eq("PEDIDO"), isNull(), eq(java.util.Map.of()),
                argThat(valores -> "59.30".equals(valores.get("valorTotal"))
                        && "2".equals(valores.get("itens[0][quantidade]"))
                        && "19.90".equals(valores.get("itens[0][precoUnitario]"))),
                isNull(), same(atendente));
    }

    @Test
    void rejeitaClienteInexistente() {
        assertThatThrownBy(() -> service.criarPedido(pedido(List.of(item("pizza", 1))), atendente))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cliente cadastrado");
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void rejeitaPedidoSemItens() {
        when(clienteRepository.existsById("cliente-1")).thenReturn(true);
        assertThatThrownBy(() -> service.criarPedido(pedido(List.of()), atendente))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ao menos um item");
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void rejeitaQuantidadeZero() {
        when(clienteRepository.existsById("cliente-1")).thenReturn(true);
        assertThatThrownBy(() -> service.criarPedido(pedido(List.of(item("pizza", 0))), atendente))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maior que zero");
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void rejeitaProdutoInexistente() {
        when(clienteRepository.existsById("cliente-1")).thenReturn(true);
        assertThatThrownBy(() -> service.criarPedido(pedido(List.of(item("pizza", 1))), atendente))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Produto não encontrado");
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void rejeitaProdutoComPrecoInvalido() {
        when(clienteRepository.existsById("cliente-1")).thenReturn(true);
        when(produtoRepository.findById("pizza")).thenReturn(Optional.of(produto("pizza", -1)));
        assertThatThrownBy(() -> service.criarPedido(pedido(List.of(item("pizza", 1))), atendente))
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

            service.atualizarStatus("pedido-1", "EM_PREPARACAO", gerente);
            service.atualizarStatus("pedido-1", "PRONTO", gerente);
            assertThat(pedido.getDataHoraSaida()).isNull();
            assertThat(pedido.getResponsavelSaida()).isNull();

            service.atualizarStatus("pedido-1", saida, gerente);
            LocalDateTime horarioSaida = pedido.getDataHoraSaida();
            assertThat(horarioSaida).isNotNull();
            assertThat(pedido.getResponsavelSaida()).isEqualTo("gerente");

            service.atualizarStatus("pedido-1", "FINALIZADO", outroGerente);
            assertThat(pedido.getStatus()).isEqualTo("FINALIZADO");
            assertThat(pedido.getDataHoraEntrada()).isEqualTo(entrada);
            assertThat(pedido.getResponsavelRegistro()).isEqualTo("atendente");
            assertThat(pedido.getDataHoraSaida()).isEqualTo(horarioSaida);
            assertThat(pedido.getResponsavelSaida()).isEqualTo("gerente");
        }
        verify(pedidoRepository, times(8)).save(any());
        verify(auditoriaService, times(2)).registrar(eq("ALTERACAO_STATUS_PEDIDO"), eq("PEDIDO"), eq("pedido-1"),
                argThat(valores -> "PRONTO".equals(valores.get("status")) && valores.get("dataHoraSaida") == null),
                argThat(valores -> "gerente".equals(valores.get("responsavelSaida")) && valores.get("dataHoraSaida") != null),
                isNull(), same(gerente));
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
            assertThatThrownBy(() -> service.atualizarStatus("pedido-1", transicao[1], gerente))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(pedido.getStatus()).isEqualTo(transicao[0]);
            assertThat(pedido.getDataHoraSaida()).isNull();
        }
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void rejeitaAtualizacaoDePedidoInexistente() {
        assertThatThrownBy(() -> service.atualizarStatus("inexistente", "EM_PREPARACAO", gerente))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Pedido não encontrado.");
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void recalculaDescontoSemAcumularPercentuaisERegistraValoresNaAuditoria() {
        Pedido pedido = pedido(List.of(item("pizza", 2), item("bebida", 3)));
        pedido.setId("pedido-1");
        pedido.setStatus("PRONTO");
        pedido.getItens().get(0).setPrecoUnitario(new BigDecimal("19.90"));
        pedido.getItens().get(1).setPrecoUnitario(new BigDecimal("6.50"));
        pedido.setValorTotal(new BigDecimal("59.30"));
        when(pedidoRepository.findById("pedido-1")).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.aplicarDesconto("pedido-1", new BigDecimal("20"), gerente);
        assertThat(pedido.getValorTotal()).isEqualByComparingTo("47.44");
        verify(auditoriaService).registrar(eq("DESCONTO_PEDIDO"), eq("PEDIDO"), eq("pedido-1"),
                argThat(valores -> "0".equals(valores.get("descontoPercentual")) && "59.30".equals(valores.get("valorTotal"))),
                argThat(valores -> "20".equals(valores.get("descontoPercentual")) && "47.44".equals(valores.get("valorTotal"))),
                isNull(), same(gerente));
        service.aplicarDesconto("pedido-1", new BigDecimal("10"), gerente);
        assertThat(pedido.getValorTotal()).isEqualByComparingTo("53.37");
        service.aplicarDesconto("pedido-1", BigDecimal.ZERO, gerente);
        assertThat(pedido.getValorTotal()).isEqualByComparingTo("59.30");
        assertThat(pedido.getStatus()).isEqualTo("PRONTO");
        verify(pedidoRepository, times(3)).save(pedido);
    }

    @Test
    void rejeitaDescontoNegativoAcimaDoLimiteOuAusente() {
        Pedido pedido = new Pedido();
        pedido.setStatus("RECEBIDO");
        when(pedidoRepository.findById("pedido-1")).thenReturn(Optional.of(pedido));
        for (BigDecimal percentual : new BigDecimal[]{new BigDecimal("-1"), new BigDecimal("20.01"), null}) {
            assertThatThrownBy(() -> service.aplicarDesconto("pedido-1", percentual, gerente))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("entre 0% e 20%");
        }
        verify(pedidoRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void cancelaComJustificativaPreservaPedidoEAuditaStatusAnteriorENovo() {
        Pedido pedido = pedido(List.of(item("pizza", 2)));
        pedido.setId("pedido-1");
        pedido.setStatus("EM_PREPARACAO");
        pedido.setValorTotal(new BigDecimal("50.00"));
        pedido.setResponsavelRegistro("atendente");
        LocalDateTime entrada = LocalDateTime.of(2026, 10, 3, 18, 0);
        pedido.setDataHoraEntrada(entrada);
        List<ItemPedido> itens = pedido.getItens();
        when(pedidoRepository.findById("pedido-1")).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.cancelarPedido("pedido-1", "  Cliente desistiu  ", gerente);
        assertThat(pedido.getStatus()).isEqualTo("CANCELADO");
        assertThat(pedido.getJustificativaCancelamento()).isEqualTo("Cliente desistiu");
        assertThat(pedido.getItens()).isSameAs(itens);
        assertThat(pedido.getValorTotal()).isEqualByComparingTo("50.00");
        assertThat(pedido.getDataHoraEntrada()).isEqualTo(entrada);
        assertThat(pedido.getResponsavelRegistro()).isEqualTo("atendente");
        verify(auditoriaService).registrar(eq("CANCELAMENTO_PEDIDO"), eq("PEDIDO"), eq("pedido-1"),
                argThat(valores -> "EM_PREPARACAO".equals(valores.get("status"))),
                argThat(valores -> "CANCELADO".equals(valores.get("status"))),
                eq("Cliente desistiu"), same(gerente));
        assertThatThrownBy(() -> service.aplicarDesconto("pedido-1", BigDecimal.TEN, gerente))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.cancelarPedido("pedido-1", "Outra justificativa", gerente))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.atualizarStatus("pedido-1", "EM_PREPARACAO", gerente))
                .isInstanceOf(IllegalArgumentException.class);
        verify(pedidoRepository).save(pedido);
    }

    @Test
    void rejeitaCancelamentoSemJustificativa() {
        Pedido pedido = new Pedido();
        pedido.setStatus("PRONTO");
        when(pedidoRepository.findById("pedido-1")).thenReturn(Optional.of(pedido));
        for (String justificativa : new String[]{null, "", "   "}) {
            assertThatThrownBy(() -> service.cancelarPedido("pedido-1", justificativa, gerente))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("justificativa");
        }
        assertThat(pedido.getStatus()).isEqualTo("PRONTO");
        verify(pedidoRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void bloqueiaDescontoECancelamentoAposSaidaOuEncerramento() {
        for (String status : List.of("SAIU_PARA_ENTREGA", "RETIRADO", "FINALIZADO", "CANCELADO", "PRONTO")) {
            Pedido pedido = new Pedido();
            pedido.setStatus(status);
            if ("PRONTO".equals(status)) pedido.setDataHoraSaida(LocalDateTime.now());
            when(pedidoRepository.findById("pedido-1")).thenReturn(Optional.of(pedido));
            assertThatThrownBy(() -> service.aplicarDesconto("pedido-1", BigDecimal.TEN, gerente))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.cancelarPedido("pedido-1", "Cliente desistiu", gerente))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(pedido.getStatus()).isEqualTo(status);
        }
        verify(pedidoRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }
}
