package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {
    @Mock UsuarioRepository repository;
    @Mock PasswordEncoder encoder;
    @InjectMocks UsuarioService service;

    private Usuario novoUsuario() {
        Usuario usuario = new Usuario();
        usuario.setUsername("atendente");
        usuario.setPassword("senha");
        usuario.setRole("ATENDENTE");
        return usuario;
    }

    @Test
    void cadastroNaoPodeSobrescreverUsuarioInformandoId() {
        Usuario usuario = novoUsuario();
        usuario.setId("admin-id");
        assertThatThrownBy(() -> service.salvarUsuario(usuario)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository, encoder);
    }

    @Test
    void cadastroNaoAceitaUsernameDuplicado() {
        when(repository.findByUsername("atendente")).thenReturn(Optional.of(novoUsuario()));
        assertThatThrownBy(() -> service.salvarUsuario(novoUsuario()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("já está cadastrado");
        verify(repository, never()).save(any());
        verifyNoInteractions(encoder);
    }

    @Test
    void cadastroExigeSenha() {
        Usuario usuario = novoUsuario();
        usuario.setPassword(" ");
        assertThatThrownBy(() -> service.salvarUsuario(usuario)).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void atualizacaoCriptografaNovaSenha() {
        Usuario existente = novoUsuario();
        existente.setId("usuario-1");
        when(repository.findById("usuario-1")).thenReturn(Optional.of(existente));
        when(repository.findByUsername("atendente")).thenReturn(Optional.of(existente));
        when(encoder.encode("senha")).thenReturn("hash-novo");
        service.atualizarUsuario("usuario-1", novoUsuario());
        verify(repository).save(argThat(usuario -> "hash-novo".equals(usuario.getPassword())));
    }
}
