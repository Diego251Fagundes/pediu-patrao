package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository repo) {
        return args -> {

            if (repo.findByUsername("admin").isEmpty()) {

                BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

                Usuario user = new Usuario();
                user.setUsername("admin");
                user.setPassword(encoder.encode("teste123"));
                user.setRole("ADMIN");

                repo.save(user);

                System.out.println("Usuário admin criado!");
            }

            // Atualiza perfis antigos sem alterar as senhas.
            for (Usuario usuario : repo.findAll()) {
                if (usuario.getRole() == null || usuario.getRole().isBlank()) {
                    usuario.setRole("admin".equals(usuario.getUsername()) ? "ADMIN" : "ATENDENTE");
                    repo.save(usuario);
                } else if ("USER".equals(usuario.getRole())) {
                    usuario.setRole("ATENDENTE");
                    repo.save(usuario);
                }
            }
        };
    }
}
