package com.umc.pediupatrao.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import com.umc.pediupatrao.service.UsuarioService;
import java.util.List;
import java.util.ArrayList;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private ProdutoService produtoService;

    @Autowired
    private UsuarioService usuarioService;

    @ModelAttribute
    public void permissoes(HttpServletRequest request, Model model) {
        model.addAttribute("podeAdministrarUsuarios", request.isUserInRole("ADMIN"));
        model.addAttribute("podeEditarClientes", request.isUserInRole("ATENDENTE"));
        model.addAttribute("podeExcluirClientes", request.isUserInRole("ADMIN"));
        model.addAttribute("podeRegistrarPedidos", request.isUserInRole("GERENTE") || request.isUserInRole("ATENDENTE"));
        model.addAttribute("podeGerenciarPedidos", request.isUserInRole("GERENTE"));
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("content", "home :: content");
        return "layout";
    }

    // Usuários
    @GetMapping("/usuarios")
    public String listarUsuarios(Model model) {
        List<Usuario> usuarios = (List<Usuario>) usuarioService.listarTodos();
        model.addAttribute("usuarios", usuarios);
        model.addAttribute("content", "usuarios/lista :: content");
        log.info("Carregando fragmento: usuarios/lista :: content");
        return "layout";
    }

    @GetMapping("/usuarios/novo")
    public String novoUsuarioForm(Model model) {
        model.addAttribute("usuario", new Usuario());
        model.addAttribute("content", "usuarios/form :: content");
        return "layout";
    }

    @GetMapping("/usuarios/editar/{id}")
    public String editarUsuarioForm(@PathVariable String id, Model model,
            RedirectAttributes redirectAttributes) {
        boolean encontrado = usuarioService.buscarPorId(id).map(u -> {
            model.addAttribute("usuario", u);
            return true;
        }).orElse(false);

        if (!encontrado) {
            redirectAttributes.addFlashAttribute("erro", "Usuário não encontrado.");
            return "redirect:/usuarios";
        }

        model.addAttribute("content", "usuarios/form :: content");
        return "layout";
    }

    @PostMapping("/usuarios/salvar")
    public String salvarUsuario(@ModelAttribute Usuario usuario,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            usuarioService.salvarUsuario(usuario);
        } catch (IllegalArgumentException exception) {
            model.addAttribute("usuario", usuario);
            model.addAttribute("erro", exception.getMessage());
            model.addAttribute("content", "usuarios/form :: content");
            return "layout";
        }
        redirectAttributes.addFlashAttribute("sucesso", "Usuário criado com sucesso!");
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/editar/{id}")
    public String atualizarUsuario(@PathVariable String id,
            @ModelAttribute Usuario usuario,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            usuarioService.atualizarUsuario(id, usuario);
        } catch (IllegalArgumentException exception) {
            usuario.setId(id);
            model.addAttribute("usuario", usuario);
            model.addAttribute("erro", exception.getMessage());
            model.addAttribute("content", "usuarios/form :: content");
            return "layout";
        }
        redirectAttributes.addFlashAttribute("sucesso", "Usuário atualizado com sucesso!");
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/deletar/{id}")
    public String deletarUsuario(@PathVariable String id,
            RedirectAttributes redirectAttributes) {
        usuarioService.deletarUsuario(id);
        redirectAttributes.addFlashAttribute("sucesso", "Usuário removido com sucesso!");
        return "redirect:/usuarios";
    }

    // Pedidos
    @GetMapping("/pedidos")
    public String pedidos(Model model) {
        List<Pedido> pedidos = pedidoService.listarPedidos();
        model.addAttribute("pedidos", pedidos);
        model.addAttribute("transicoesPedido", pedidoService.getTransicoesPermitidas());
        model.addAttribute("content", "pedidos :: content");
        log.info("Carregando fragmento: pedidos :: content");
        return "layout";
    }

    @PostMapping("/pedidos/{id}/status")
    public String atualizarStatusPedido(@PathVariable String id, @RequestParam String status,
            Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            pedidoService.atualizarStatus(id, status, authentication.getName());
            redirectAttributes.addFlashAttribute("sucesso", "Status do pedido atualizado com sucesso!");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("erro", exception.getMessage());
        }
        return "redirect:/pedidos";
    }

    @GetMapping("/pedidos/novo")
    public String novoPedidoForm(Model model) {
        Pedido pedido = new Pedido();
        pedido.getItens().add(new ItemPedido());
        model.addAttribute("pedido", pedido);
        carregarFormularioPedido(model);
        return "layout";
    }

    @PostMapping("/pedidos/salvar")
    public String salvarPedido(@ModelAttribute Pedido pedido, Authentication authentication,
            Model model, RedirectAttributes redirectAttributes) {
        try {
            pedidoService.criarPedido(pedido, authentication.getName());
        } catch (IllegalArgumentException exception) {
            model.addAttribute("pedido", pedido);
            model.addAttribute("erro", exception.getMessage());
            carregarFormularioPedido(model);
            return "layout";
        }
        redirectAttributes.addFlashAttribute("sucesso", "Pedido registrado com sucesso!");
        return "redirect:/pedidos";
    }

    private void carregarFormularioPedido(Model model) {
        Pedido pedido = (Pedido) model.getAttribute("pedido");
        if (pedido.getItens() == null || pedido.getItens().isEmpty()) {
            pedido.setItens(new ArrayList<>(List.of(new ItemPedido())));
        }
        model.addAttribute("clientes", clienteService.listarClientes());
        model.addAttribute("produtos", produtoService.listarProdutos());
        model.addAttribute("content", "pedido-form :: content");
    }

    // Produtos
    @GetMapping("/produtos")
    public String produtos(Model model) {
        List<Produto> produtos = produtoService.listarProdutos();
        model.addAttribute("produtos", produtos);
        model.addAttribute("content", "produtos/lista :: content");
        log.info("Carregando fragmento: produtos/lista :: content");
        return "layout";
    }

    // Clientes
    @GetMapping("/clientes")
    public String clientes(Model model) {
        List<Cliente> clientes = clienteService.listarClientes();
        model.addAttribute("clientes", clientes);
        model.addAttribute("content", "clientes/lista :: content");
        log.info("Carregando fragmento: clientes/lista :: content");
        return "layout";
    }

    @GetMapping("/clientes/novo")
    public String novoClienteForm(Model model) {
        model.addAttribute("cliente", new Cliente());
        model.addAttribute("content", "clientes/form :: content");
        return "layout";
    }

    @PostMapping("/clientes/salvar")
    public String salvarCliente(@ModelAttribute Cliente cliente,
            RedirectAttributes redirectAttributes) {
        clienteService.salvar(cliente);
        redirectAttributes.addFlashAttribute("sucesso", "Cliente criado com sucesso!");
        return "redirect:/clientes";
    }

    @GetMapping("/clientes/editar/{id}")
    public String editarClienteForm(@PathVariable String id, Model model,
            RedirectAttributes redirectAttributes) {
        boolean encontrado = clienteService.buscarPorId(id).map(c -> {
            model.addAttribute("cliente", c);
            return true;
        }).orElse(false);

        if (!encontrado) {
            redirectAttributes.addFlashAttribute("erro", "Cliente não encontrado.");
            return "redirect:/clientes";
        }

        model.addAttribute("content", "clientes/form :: content");
        return "layout";
    }

    @PostMapping("/clientes/editar/{id}")
    public String atualizarCliente(@PathVariable String id,
            @ModelAttribute Cliente cliente,
            RedirectAttributes redirectAttributes) {
        cliente.setId(id);
        clienteService.salvar(cliente);
        redirectAttributes.addFlashAttribute("sucesso", "Cliente atualizado com sucesso!");
        return "redirect:/clientes";
    }

    // Outras páginas
    @GetMapping("/configuracoes")
    public String configuracoes(Model model) {
        model.addAttribute("content", "configuracoes :: content");
        return "layout";
    }

    @GetMapping("/teste")
    public String teste() {
        return "teste";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }
}
