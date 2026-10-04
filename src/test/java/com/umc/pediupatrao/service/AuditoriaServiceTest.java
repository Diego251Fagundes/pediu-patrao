package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {
    @Mock AuditoriaRepository repository;
    @InjectMocks AuditoriaService service;

    @Test
    void insereEventoComUsuarioPerfilDataECopiaDosValores() {
        var autenticado = new UsernamePasswordAuthenticationToken("gerente", null,
                List.of(new SimpleGrantedAuthority("ROLE_GERENTE")));
        Map<String, String> anteriores = new HashMap<>();
        anteriores.put("status", "PRONTO");
        anteriores.put("justificativaCancelamento", null);
        anteriores.put("valorTotal", "50.00");
        service.registrar("CANCELAMENTO_PEDIDO", "PEDIDO", "pedido-1", anteriores,
                Map.of("status", "CANCELADO", "valorTotal", "50.00"), "Cliente desistiu", autenticado);
        anteriores.put("status", "FINALIZADO");

        ArgumentCaptor<Auditoria> evento = ArgumentCaptor.forClass(Auditoria.class);
        verify(repository).insert(evento.capture());
        Auditoria auditoria = evento.getValue();
        assertThat(auditoria.getId()).isNull();
        assertThat(auditoria.getUsuario()).isEqualTo("gerente");
        assertThat(auditoria.getPerfil()).isEqualTo("GERENTE");
        assertThat(auditoria.getDataHora()).isNotNull();
        assertThat(auditoria.getOperacao()).isEqualTo("CANCELAMENTO_PEDIDO");
        assertThat(auditoria.getEntidade()).isEqualTo("PEDIDO");
        assertThat(auditoria.getEntidadeId()).isEqualTo("pedido-1");
        assertThat(auditoria.getValoresAnteriores()).containsEntry("status", "PRONTO");
        assertThat(auditoria.getValoresNovos()).containsEntry("status", "CANCELADO");
        assertThat(auditoria.getValoresNovos()).doesNotContainKeys("valorTotal", "justificativaCancelamento");
        assertThat(auditoria.getJustificativa()).isEqualTo("Cliente desistiu");
    }

    @Test
    void naoInsereRegistroQuandoNaoHouverAlteracao() {
        var autenticado = new UsernamePasswordAuthenticationToken("atendente", null,
                List.of(new SimpleGrantedAuthority("ROLE_ATENDENTE")));
        service.registrar("ALTERACAO_CLIENTE", "CLIENTE", "cliente-1",
                Map.of("telefone", "11999990000"), Map.of("telefone", "11999990000"), null, autenticado);
        verifyNoInteractions(repository);
    }
}
