package com.umc.pediupatrao.config;

import com.umc.pediupatrao.controller.ClienteController;
import com.umc.pediupatrao.controller.HomeController;
import com.umc.pediupatrao.controller.UsuarioController;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.service.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({HomeController.class, UsuarioController.class, ClienteController.class})
@Import({SecurityConfig.class, UsuarioDetailsService.class, UsuarioService.class})
class UsuarioSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean UsuarioRepository repository;
    @MockitoBean ClienteService clienteService;
    @MockitoBean ProdutoService produtoService;
    @MockitoBean PedidoService pedidoService;

    private Usuario usuario(String perfil) {
        Usuario usuario = new Usuario();
        usuario.setId("usuario-1");
        usuario.setUsername("funcionario");
        usuario.setRole(perfil);
        usuario.setPassword(new BCryptPasswordEncoder().encode("senhaTeste"));
        return usuario;
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "GERENTE", "ATENDENTE"})
    void loginUsaPerfilSalvoNoBanco(String perfil) throws Exception {
        when(repository.findByUsername("funcionario")).thenReturn(Optional.of(usuario(perfil)));
        mvc.perform(formLogin().user("funcionario").password("senhaTeste"))
                .andExpect(authenticated().withRoles(perfil))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void perfilDesconhecidoNaoAutentica() throws Exception {
        when(repository.findByUsername("funcionario")).thenReturn(Optional.of(usuario("SUPERADMIN")));
        mvc.perform(formLogin().user("funcionario").password("senhaTeste"))
                .andExpect(unauthenticated());
    }

    @Test
    void apiDeUsuariosExigeLogin() throws Exception {
        mvc.perform(get("/api/usuarios"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GERENTE", "ATENDENTE"})
    void perfisOperacionaisNaoAdministramUsuariosNemElevamPrivilegios(String perfil) throws Exception {
        var autenticado = user("funcionario").roles(perfil);
        mvc.perform(get("/usuarios").with(autenticado)).andExpect(status().isForbidden());
        mvc.perform(get("/usuarios/novo").with(autenticado)).andExpect(status().isForbidden());
        mvc.perform(get("/usuarios/editar/usuario-1").with(autenticado)).andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios").with(autenticado)).andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios/usuario-1").with(autenticado)).andExpect(status().isForbidden());
        mvc.perform(post("/api/usuarios").with(autenticado).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"invasor\",\"password\":\"senha\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/usuarios/usuario-1").with(autenticado).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"funcionario\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/usuarios/usuario-1").with(autenticado).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/usuarios/salvar").with(autenticado).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/usuarios/editar/usuario-1").with(autenticado).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/usuarios/deletar/usuario-1").with(autenticado).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }

    @Test
    void adminConsultaUsuariosSemExporSenha() throws Exception {
        when(repository.findAll()).thenReturn(List.of(usuario("ADMIN")));
        mvc.perform(get("/usuarios").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/usuarios").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void formularioDeEdicaoNaoPreencheHashDaSenha() throws Exception {
        Usuario existente = usuario("ADMIN");
        when(repository.findById("usuario-1")).thenReturn(Optional.of(existente));
        mvc.perform(get("/usuarios/editar/usuario-1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(existente.getPassword()))));
    }

    @Test
    void cadastroSemCsrfNaoSalvaMesmoParaAdmin() throws Exception {
        mvc.perform(post("/api/usuarios").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"novo\",\"password\":\"senha\",\"role\":\"ATENDENTE\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }

    @Test
    void perfilInvalidoRetornaErroSemSalvar() throws Exception {
        mvc.perform(post("/api/usuarios").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"novo\",\"password\":\"senha\",\"role\":\"SUPERADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").exists());
        verify(repository, never()).save(any());
    }

    @Test
    void adminCriaUsuarioComSenhaCriptografadaEPerfil() throws Exception {
        when(repository.findByUsername("novo")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        mvc.perform(post("/api/usuarios").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"novo\",\"password\":\"senha\",\"role\":\"GERENTE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("GERENTE"))
                .andExpect(jsonPath("$.password").doesNotExist());
        verify(repository).save(argThat(salvo -> new BCryptPasswordEncoder().matches("senha", salvo.getPassword())));
    }

    @Test
    void apiAtualizaPerfilEPreservaSenhaEmBranco() throws Exception {
        Usuario existente = usuario("ATENDENTE");
        String senhaAnterior = existente.getPassword();
        when(repository.findById("usuario-1")).thenReturn(Optional.of(existente));
        when(repository.findByUsername("funcionario")).thenReturn(Optional.of(existente));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        mvc.perform(put("/api/usuarios/usuario-1").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"funcionario\",\"password\":\"\",\"role\":\"GERENTE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("GERENTE"))
                .andExpect(jsonPath("$.password").doesNotExist());
        assertThat(existente.getPassword()).isEqualTo(senhaAnterior);
    }

    @Test
    void erroDeValidacaoDoFormularioMantemTelaDeEdicao() throws Exception {
        when(repository.findById("usuario-1")).thenReturn(Optional.of(usuario("ATENDENTE")));
        mvc.perform(post("/usuarios/editar/usuario-1").with(user("admin").roles("ADMIN")).with(csrf())
                .param("username", "funcionario").param("role", "INVALIDO"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("erro"))
                .andExpect(content().string(containsString("Editar Usuário")));
        verify(repository, never()).save(any());
    }

    @Test
    void loginEFormulariosRenderizamTokenCsrf() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(get("/usuarios/novo").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void sairUsaPostProtegidoEEncerraAutenticacao() throws Exception {
        mvc.perform(get("/").with(user("atendente").roles("ATENDENTE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/logout\"")));
        mvc.perform(post("/logout").with(user("atendente").roles("ATENDENTE")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/logout").with(user("atendente").roles("ATENDENTE")).with(csrf()))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?logout"));
    }

    @Test
    void exclusaoDeClienteExigeEAceitaTokenNoCabecalho() throws Exception {
        mvc.perform(delete("/api/clientes/cliente-1").with(user("atendente").roles("ATENDENTE")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(clienteService);
        mvc.perform(delete("/api/clientes/cliente-1").with(user("atendente").roles("ATENDENTE")).with(csrf().asHeader()))
                .andExpect(status().isNoContent());
        verify(clienteService).excluir("cliente-1");
    }
}
