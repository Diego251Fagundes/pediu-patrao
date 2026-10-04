package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.repository.ClienteRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {
    @Mock ClienteRepository repository;
    @Mock AuditoriaService auditoriaService;
    @InjectMocks ClienteService service;

    private final Authentication atendente = new UsernamePasswordAuthenticationToken("atendente", null,
            List.of(new SimpleGrantedAuthority("ROLE_ATENDENTE")));
    private final Authentication admin = new UsernamePasswordAuthenticationToken("admin", null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    @Test
    void auditaCadastroAlteracaoDeTelefoneEnderecoEExclusao() {
        Cliente original = new Cliente();
        original.setNome("Cliente");
        original.setTelefone("11999990000");
        original.setLogradouro("Rua das Flores");
        when(repository.save(any())).thenAnswer(invocacao -> {
            Cliente cliente = invocacao.getArgument(0);
            cliente.setId("cliente-1");
            return cliente;
        });
        service.novoCliente(original, atendente);
        verify(auditoriaService).registrar(eq("CADASTRO_CLIENTE"), eq("CLIENTE"), eq("cliente-1"), anyMap(),
                argThat(valores -> "11999990000".equals(valores.get("telefone"))), isNull(), same(atendente));

        Cliente atualizado = new Cliente();
        atualizado.setId("cliente-1");
        atualizado.setNome("Cliente");
        atualizado.setTelefone("11888880000");
        atualizado.setLogradouro("Av. Brasil");
        when(repository.findById("cliente-1")).thenReturn(Optional.of(original));
        service.salvar(atualizado, atendente);
        verify(auditoriaService).registrar(eq("ALTERACAO_CLIENTE"), eq("CLIENTE"), eq("cliente-1"),
                argThat(valores -> "11999990000".equals(valores.get("telefone"))
                        && "Rua das Flores".equals(valores.get("logradouro"))),
                argThat(valores -> "11888880000".equals(valores.get("telefone"))
                        && "Av. Brasil".equals(valores.get("logradouro"))), isNull(), same(atendente));

        when(repository.findById("cliente-1")).thenReturn(Optional.of(atualizado));
        service.excluir("cliente-1", admin);
        verify(repository).deleteById("cliente-1");
        verify(auditoriaService).registrar(eq("EXCLUSAO_CLIENTE"), eq("CLIENTE"), eq("cliente-1"),
                argThat(valores -> "Av. Brasil".equals(valores.get("logradouro"))), eq(java.util.Map.of()),
                isNull(), same(admin));
    }

    @Test
    void clienteInexistenteNaoGeraAlteracaoNemAuditoria() {
        Cliente cliente = new Cliente();
        cliente.setId("inexistente");
        assertThatThrownBy(() -> service.salvar(cliente, atendente)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.excluir("inexistente", admin)).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
        verify(repository, never()).deleteById(anyString());
        verifyNoInteractions(auditoriaService);
    }
}
