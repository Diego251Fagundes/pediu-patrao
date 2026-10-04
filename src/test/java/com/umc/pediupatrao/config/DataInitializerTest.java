package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DataInitializerTest {
    private Usuario usuario(String nome, String perfil) {
        Usuario usuario = new Usuario();
        usuario.setUsername(nome);
        usuario.setRole(perfil);
        usuario.setPassword("hash-original");
        return usuario;
    }

    @Test
    void criaAdministradorComPerfilCorreto() throws Exception {
        UsuarioRepository repository = mock(UsuarioRepository.class);
        when(repository.findByUsername("admin")).thenReturn(Optional.empty());
        when(repository.findAll()).thenReturn(List.of());
        new DataInitializer().initDatabase(repository).run();
        verify(repository).save(argThat(usuario -> "ADMIN".equals(usuario.getRole())
                && new BCryptPasswordEncoder().matches("teste123", usuario.getPassword())));
    }

    @Test
    void adaptaPerfisAntigosSemTrocarSenhasOuPerfisJaValidos() throws Exception {
        UsuarioRepository repository = mock(UsuarioRepository.class);
        Usuario admin = usuario("admin", null);
        Usuario antigo = usuario("antigo", "USER");
        Usuario semPerfil = usuario("sem-perfil", null);
        Usuario gerente = usuario("gerente", "GERENTE");
        when(repository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(repository.findAll()).thenReturn(List.of(admin, antigo, semPerfil, gerente));
        new DataInitializer().initDatabase(repository).run();
        assertThat(admin.getRole()).isEqualTo("ADMIN");
        assertThat(antigo.getRole()).isEqualTo("ATENDENTE");
        assertThat(semPerfil.getRole()).isEqualTo("ATENDENTE");
        assertThat(gerente.getRole()).isEqualTo("GERENTE");
        assertThat(admin.getPassword()).isEqualTo("hash-original");
        verify(repository, never()).save(gerente);
        clearInvocations(repository);
        new DataInitializer().initDatabase(repository).run();
        verify(repository, never()).save(any());
    }
}
