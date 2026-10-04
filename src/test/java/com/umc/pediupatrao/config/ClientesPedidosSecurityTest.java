package com.umc.pediupatrao.config;

import com.umc.pediupatrao.controller.ClienteController;
import com.umc.pediupatrao.controller.HomeController;
import com.umc.pediupatrao.controller.PedidoController;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.util.List;
import com.umc.pediupatrao.service.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({HomeController.class, ClienteController.class, PedidoController.class})
@Import({SecurityConfig.class, PedidoService.class})
class ClientesPedidosSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean UsuarioDetailsService usuarioDetailsService;
    @MockitoBean UsuarioService usuarioService;
    @MockitoBean ClienteService clienteService;
    @MockitoBean ProdutoService produtoService;
    @MockitoBean PedidoRepository pedidoRepository;
    @MockitoBean ClienteRepository clienteRepository;
    @MockitoBean ProdutoRepository produtoRepository;

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "GERENTE", "ATENDENTE"})
    void perfisConsultamClientesEPedidos(String perfil) throws Exception {
        for (String rota : new String[]{"/clientes", "/api/clientes", "/pedidos", "/api/pedidos"}) {
            mvc.perform(get(rota).with(user("usuario").roles(perfil)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void consultasExigemAutenticacao() throws Exception {
        for (String rota : new String[]{"/clientes", "/api/clientes", "/pedidos", "/api/pedidos"}) {
            mvc.perform(get(rota)).andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));
        }
        verifyNoInteractions(clienteService, pedidoRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "GERENTE"})
    void adminEGerenteNaoCadastramNemEditamClientes(String perfil) throws Exception {
        var autenticado = user("usuario").roles(perfil);
        mvc.perform(get("/clientes/novo").with(autenticado)).andExpect(status().isForbidden());
        mvc.perform(get("/clientes/editar/cliente-1").with(autenticado)).andExpect(status().isForbidden());
        mvc.perform(post("/clientes/salvar").with(autenticado).with(csrf()).param("nome", "Cliente"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/clientes/editar/cliente-1").with(autenticado).with(csrf()).param("nome", "Cliente"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/clientes").with(autenticado).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Cliente\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/clientes/cliente-1").with(autenticado).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Cliente\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(clienteService);
    }

    @Test
    void atendenteCadastraEEditaClientesPorFormularioEApi() throws Exception {
        var autenticado = user("atendente").roles("ATENDENTE");
        Cliente cliente = new Cliente();
        cliente.setId("cliente-1");
        cliente.setNome("Cliente");
        when(clienteService.buscarPorId("cliente-1")).thenReturn(Optional.of(cliente));
        when(clienteService.salvar(any())).thenAnswer(invocation -> invocation.getArgument(0));
        mvc.perform(get("/clientes/novo").with(autenticado)).andExpect(status().isOk());
        mvc.perform(get("/clientes/editar/cliente-1").with(autenticado)).andExpect(status().isOk());
        mvc.perform(post("/clientes/salvar").with(autenticado).with(csrf()).param("nome", "Cliente"))
                .andExpect(redirectedUrl("/clientes"));
        mvc.perform(post("/clientes/editar/cliente-1").with(autenticado).with(csrf()).param("nome", "Cliente"))
                .andExpect(redirectedUrl("/clientes"));
        mvc.perform(post("/api/clientes").with(autenticado).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Cliente\"}"))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/clientes/cliente-1").with(autenticado).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Cliente\"}"))
                .andExpect(status().isOk());
        verify(clienteService, times(4)).salvar(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GERENTE", "ATENDENTE"})
    void gerenteEAtendenteNaoExcluemClientes(String perfil) throws Exception {
        mvc.perform(delete("/api/clientes/cliente-1").with(user("usuario").roles(perfil)).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(clienteService);
    }

    @Test
    void adminPodeExcluirCliente() throws Exception {
        mvc.perform(delete("/api/clientes/cliente-1").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        verify(clienteService).excluir("cliente-1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "ATENDENTE"})
    void adminEAtendenteNaoAlteramStatus(String perfil) throws Exception {
        mvc.perform(put("/api/pedidos/pedido-1/status").param("status", "CANCELADO")
                .with(user("usuario").roles(perfil)).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(pedidoRepository);
    }

    @Test
    void gerentePodeAlterarStatus() throws Exception {
        Pedido pedido = new Pedido();
        pedido.setId("pedido-1");
        pedido.setStatus("RECEBIDO");
        when(pedidoRepository.findById("pedido-1")).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        mvc.perform(put("/api/pedidos/pedido-1/status").param("status", "EM_PREPARACAO")
                .with(user("gerente").roles("GERENTE")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_PREPARACAO"));
        verify(pedidoRepository).save(pedido);
    }



    @Test
    void adminNaoRegistraPedidos() throws Exception {
        mvc.perform(get("/pedidos/novo").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/pedidos/salvar").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/pedidos").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"clienteId\":\"cliente-1\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(pedidoRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GERENTE", "ATENDENTE"})
    void perfisOperacionaisRegistramPedidosRecebidos(String perfil) throws Exception {
        Produto produto = new Produto();
        produto.setId("produto-1");
        produto.setNome("Pizza");
        produto.setPreco(25.0);
        Cliente cliente = new Cliente();
        cliente.setId("cliente-1");
        cliente.setNome("Cliente");
        when(clienteRepository.existsById("cliente-1")).thenReturn(true);
        when(produtoRepository.findById("produto-1")).thenReturn(Optional.of(produto));
        when(clienteService.listarClientes()).thenReturn(List.of(cliente));
        when(produtoService.listarProdutos()).thenReturn(List.of(produto));
        when(pedidoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        mvc.perform(get("/pedidos/novo").with(user("usuario").roles(perfil)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pedidos").with(user("usuario").roles(perfil)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"clienteId":"cliente-1","responsavelRegistro":"falso","valorTotal":0.01,
                         "descontoPercentual":20,"dataHoraEntrada":"2000-01-01T00:00:00",
                         "itens":[{"produtoId":"produto-1","quantidade":2,"precoUnitario":0.01}]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEBIDO"))
                .andExpect(jsonPath("$.valorTotal").value(50.0))
                .andExpect(jsonPath("$.descontoPercentual").value(0))
                .andExpect(jsonPath("$.responsavelRegistro").value("usuario"))
                .andExpect(jsonPath("$.dataHoraEntrada").value(not("2000-01-01T00:00:00")))
                .andExpect(jsonPath("$.itens[0].precoUnitario").value(25.0));
        mvc.perform(post("/pedidos/salvar").with(user("usuario").roles(perfil)).with(csrf())
                .param("clienteId", "cliente-1").param("itens[0].produtoId", "produto-1")
                .param("itens[0].quantidade", "2"))
                .andExpect(redirectedUrl("/pedidos"));
        mvc.perform(post("/pedidos/salvar").with(user("usuario").roles(perfil)).with(csrf())
                .param("clienteId", "cliente-1").param("itens[0].produtoId", "produto-1")
                .param("itens[0].quantidade", "0"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("erro"));
        ArgumentCaptor<Pedido> pedidosSalvos = ArgumentCaptor.forClass(Pedido.class);
        verify(pedidoRepository, times(2)).save(pedidosSalvos.capture());
        when(pedidoRepository.findAll()).thenReturn(List.of(pedidosSalvos.getValue()));
        mvc.perform(get("/pedidos").with(user("usuario").roles(perfil)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("R$ 50,00")))
                .andExpect(content().string(containsString("Pizza")))
                .andExpect(content().string(containsString("RECEBIDO")));
    }

    @Test
    void cadastroNaoContornaPermissoesDeStatus() throws Exception {
        mvc.perform(post("/api/pedidos").with(user("usuario").roles("ATENDENTE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":\"cliente-1\",\"status\":\"CANCELADO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").exists());
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void cadastroNaoSobrescrevePedidoExistente() throws Exception {
        mvc.perform(post("/api/pedidos").with(user("usuario").roles("ATENDENTE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"pedido-existente\",\"clienteId\":\"cliente-1\"}"))
                .andExpect(status().isBadRequest());
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void operacoesPermitidasTambemExigemCsrf() throws Exception {
        mvc.perform(post("/api/pedidos").with(user("atendente").roles("ATENDENTE"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/pedidos/pedido-1/status").with(user("gerente").roles("GERENTE"))
                .param("status", "EM_PREPARACAO"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/clientes/salvar").with(user("atendente").roles("ATENDENTE")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(clienteService, pedidoRepository);
    }

    @ParameterizedTest
    @CsvSource({"ADMIN,false,true", "GERENTE,false,false", "ATENDENTE,true,false"})
    void interfaceMostraPermissoesDoPerfil(String perfil, boolean podeEditar, boolean podeExcluir) throws Exception {
        var resultado = mvc.perform(get("/clientes").with(user("usuario").roles(perfil)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("podeEditarClientes", podeEditar))
                .andExpect(model().attribute("podeExcluirClientes", podeExcluir))
                .andExpect(content().string(containsString("const podeEditarClientes = " + podeEditar + ";")))
                .andExpect(content().string(containsString("const podeExcluirClientes = " + podeExcluir + ";")));
        if (podeEditar) {
            resultado.andExpect(content().string(containsString("href=\"/clientes/novo\"")));
        } else {
            resultado.andExpect(content().string(not(containsString("href=\"/clientes/novo\""))));
        }
        if ("ADMIN".equals(perfil)) {
            resultado.andExpect(content().string(containsString("href=\"/usuarios\"")));
        } else {
            resultado.andExpect(content().string(not(containsString("href=\"/usuarios\""))));
        }
    }

    @Test
    void rotasNaoPrevistasNaoRecebemPermissaoGenerica() throws Exception {
        mvc.perform(patch("/api/clientes/cliente-1").with(user("atendente").roles("ATENDENTE")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/pedidos/pedido-1/status").with(user("gerente").roles("GERENTE")).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(clienteService, pedidoRepository);
    }
}
