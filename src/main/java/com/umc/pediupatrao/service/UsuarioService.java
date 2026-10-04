package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    public Optional<Usuario> buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username);
    }

    public Usuario salvarUsuario(Usuario usuario) {
        if (usuario.getId() != null) {
            throw new IllegalArgumentException("O cadastro de um novo usuário não pode informar ID.");
        }
        validarUsuario(usuario, null);
        if (usuario.getPassword() == null || usuario.getPassword().isBlank()) {
            throw new IllegalArgumentException("Informe a senha do novo usuário.");
        }
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        return usuarioRepository.save(usuario);
    }

    public void deletarUsuario(String id) {
        usuarioRepository.deleteById(id);
    }

    public Iterable<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    public Usuario atualizarUsuario(String id, Usuario usuarioAtualizado) {
        Usuario usuarioExistente = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
        validarUsuario(usuarioAtualizado, id);
        usuarioExistente.setUsername(usuarioAtualizado.getUsername());
        usuarioExistente.setRole(usuarioAtualizado.getRole());

        // Mantém a senha atual se o campo ficar em branco.
        if (usuarioAtualizado.getPassword() != null && !usuarioAtualizado.getPassword().isBlank()) {
            usuarioExistente.setPassword(passwordEncoder.encode(usuarioAtualizado.getPassword()));
        }

        return usuarioRepository.save(usuarioExistente);
    }

    private void validarUsuario(Usuario usuario, String idAtual) {
        if (usuario.getUsername() == null || usuario.getUsername().isBlank()) {
            throw new IllegalArgumentException("Informe o nome de usuário.");
        }
        if (usuario.getRole() == null || !Usuario.PERFIS.contains(usuario.getRole())) {
            throw new IllegalArgumentException("Perfil inválido. Selecione ADMIN, GERENTE ou ATENDENTE.");
        }
        usuario.setUsername(usuario.getUsername().trim());
        usuarioRepository.findByUsername(usuario.getUsername()).ifPresent(existente -> {
            if (idAtual == null || !idAtual.equals(existente.getId())) {
                throw new IllegalArgumentException("Este nome de usuário já está cadastrado.");
            }
        });
    }
}
