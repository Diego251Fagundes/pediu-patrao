package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import java.util.List;

@Service
public class PedidoService {

    private static final Set<String> STATUS_ANTES_DA_SAIDA = Set.of("RECEBIDO", "EM_PREPARACAO", "PRONTO");

    private static final Map<String, List<String>> TRANSICOES = Map.of(
            "RECEBIDO", List.of("EM_PREPARACAO"),
            "EM_PREPARACAO", List.of("PRONTO"),
            "PRONTO", List.of("SAIU_PARA_ENTREGA", "RETIRADO"),
            "SAIU_PARA_ENTREGA", List.of("FINALIZADO"),
            "RETIRADO", List.of("FINALIZADO"));

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    public Pedido criarPedido(Pedido pedido, Authentication authentication) {
        if (pedido.getId() != null) {
            throw new IllegalArgumentException("O cadastro de um novo pedido não pode informar ID.");
        }
        if (pedido.getStatus() != null && !pedido.getStatus().isBlank()
                && !"RECEBIDO".equals(pedido.getStatus())) {
            throw new IllegalArgumentException("Um novo pedido deve iniciar com o status RECEBIDO.");
        }
        if (pedido.getClienteId() == null || pedido.getClienteId().isBlank()
                || !clienteRepository.existsById(pedido.getClienteId())) {
            throw new IllegalArgumentException("Selecione um cliente cadastrado.");
        }
        if (pedido.getItens() == null || pedido.getItens().isEmpty()) {
            throw new IllegalArgumentException("Adicione ao menos um item ao pedido.");
        }

        List<ItemPedido> itens = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ItemPedido informado : pedido.getItens()) {
            if (informado == null || informado.getProdutoId() == null || informado.getProdutoId().isBlank()) {
                throw new IllegalArgumentException("Selecione um produto para cada item.");
            }
            if (informado.getQuantidade() <= 0) {
                throw new IllegalArgumentException("A quantidade de cada item deve ser maior que zero.");
            }
            Produto produto = produtoRepository.findById(informado.getProdutoId())
                    .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado."));
            if (produto.getPreco() == null || !Double.isFinite(produto.getPreco()) || produto.getPreco() < 0) {
                throw new IllegalArgumentException("O produto deve ter um preço válido.");
            }

            ItemPedido item = new ItemPedido();
            item.setProdutoId(produto.getId());
            item.setNomeProduto(produto.getNome());
            item.setQuantidade(informado.getQuantidade());
            item.setPrecoUnitario(BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP));
            itens.add(item);
            total = total.add(item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade())));
        }

        pedido.setItens(itens);
        pedido.setDescontoPercentual(BigDecimal.ZERO);
        pedido.setValorTotal(total.setScale(2, RoundingMode.HALF_UP));
        pedido.setResponsavelRegistro(authentication.getName());
        pedido.setDataHoraEntrada(LocalDateTime.now());
        pedido.setDataHoraSaida(null);
        pedido.setResponsavelSaida(null);
        pedido.setJustificativaCancelamento(null);
        pedido.setStatus("RECEBIDO");
        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar("CADASTRO_PEDIDO", "PEDIDO", salvo.getId(), Map.of(), valoresPedido(salvo),
                null, authentication);
        return salvo;
    }

    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll();
    }

    public Map<String, List<String>> getTransicoesPermitidas() {
        return TRANSICOES;
    }

    public Set<String> getStatusAntesDaSaida() {
        return STATUS_ANTES_DA_SAIDA;
    }

    private Pedido buscarPedidoAntesDaSaida(String id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado."));
        if (pedido.getDataHoraSaida() != null || pedido.getStatus() == null
                || !STATUS_ANTES_DA_SAIDA.contains(pedido.getStatus())) {
            throw new IllegalArgumentException("Esta operação só é permitida antes da saída de um pedido não cancelado.");
        }
        return pedido;
    }

    public Pedido cancelarPedido(String id, String justificativa, Authentication authentication) {
        Pedido pedido = buscarPedidoAntesDaSaida(id);
        if (justificativa == null || justificativa.isBlank()) {
            throw new IllegalArgumentException("Informe a justificativa do cancelamento.");
        }
        Map<String, String> anteriores = new HashMap<>();
        anteriores.put("status", pedido.getStatus());
        anteriores.put("justificativaCancelamento", pedido.getJustificativaCancelamento());
        pedido.setStatus("CANCELADO");
        pedido.setJustificativaCancelamento(justificativa.trim());
        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar("CANCELAMENTO_PEDIDO", "PEDIDO", salvo.getId(), anteriores,
                Map.of("status", salvo.getStatus(), "justificativaCancelamento", salvo.getJustificativaCancelamento()),
                salvo.getJustificativaCancelamento(), authentication);
        return salvo;
    }

    public Pedido aplicarDesconto(String id, BigDecimal percentual, Authentication authentication) {
        Pedido pedido = buscarPedidoAntesDaSaida(id);
        if (percentual == null || percentual.signum() < 0 || percentual.compareTo(new BigDecimal("20")) > 0) {
            throw new IllegalArgumentException("O desconto deve estar entre 0% e 20%.");
        }
        if (pedido.getItens() == null || pedido.getItens().isEmpty()) {
            throw new IllegalArgumentException("O pedido deve ter itens com preços registrados para aplicar desconto.");
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ItemPedido item : pedido.getItens()) {
            if (item == null || item.getPrecoUnitario() == null || item.getPrecoUnitario().signum() < 0
                    || item.getQuantidade() <= 0) {
                throw new IllegalArgumentException("O pedido possui um item com preço ou quantidade inválidos.");
            }
            subtotal = subtotal.add(item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade())));
        }
        Map<String, String> anteriores = valoresDesconto(pedido);
        pedido.setDescontoPercentual(percentual);
        pedido.setValorTotal(subtotal.multiply(BigDecimal.ONE.subtract(percentual.movePointLeft(2)))
                .setScale(2, RoundingMode.HALF_UP));
        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar("DESCONTO_PEDIDO", "PEDIDO", salvo.getId(), anteriores,
                valoresDesconto(salvo), null, authentication);
        return salvo;
    }

    private Map<String, String> valoresDesconto(Pedido pedido) {
        Map<String, String> valores = new HashMap<>();
        valores.put("descontoPercentual", pedido.getDescontoPercentual() == null
                ? null : pedido.getDescontoPercentual().toPlainString());
        valores.put("valorTotal", pedido.getValorTotal() == null ? null : pedido.getValorTotal().toPlainString());
        return valores;
    }

    public Pedido atualizarStatus(String id, String status, Authentication authentication) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado."));
        if ("CANCELADO".equals(pedido.getStatus())) {
            throw new IllegalArgumentException("Não é possível alterar um pedido cancelado.");
        }
        if ("CANCELADO".equals(status)) {
            throw new IllegalArgumentException("Cancelamento não é permitido nesta operação.");
        }
        List<String> proximos = pedido.getStatus() == null ? List.of()
                : TRANSICOES.getOrDefault(pedido.getStatus(), List.of());
        if (status == null || !proximos.contains(status)) {
            throw new IllegalArgumentException("Transição de status não permitida para este pedido.");
        }
        Map<String, String> anteriores = valoresPedido(pedido);
        if ("SAIU_PARA_ENTREGA".equals(status) || "RETIRADO".equals(status)) {
            pedido.setDataHoraSaida(LocalDateTime.now());
            pedido.setResponsavelSaida(authentication.getName());
        }
        pedido.setStatus(status);
        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar("ALTERACAO_STATUS_PEDIDO", "PEDIDO", salvo.getId(), anteriores,
                valoresPedido(salvo), null, authentication);
        return salvo;
    }

    private Map<String, String> valoresPedido(Pedido pedido) {
        Map<String, String> valores = new LinkedHashMap<>();
        valores.put("clienteId", pedido.getClienteId());
        valores.put("status", pedido.getStatus());
        valores.putAll(valoresDesconto(pedido));
        valores.put("responsavelRegistro", pedido.getResponsavelRegistro());
        valores.put("dataHoraEntrada", Objects.toString(pedido.getDataHoraEntrada(), null));
        valores.put("dataHoraSaida", Objects.toString(pedido.getDataHoraSaida(), null));
        valores.put("responsavelSaida", pedido.getResponsavelSaida());
        valores.put("justificativaCancelamento", pedido.getJustificativaCancelamento());
        valores.put("tipoPizza", pedido.getTipoPizza());
        valores.put("sabor", pedido.getSabor());
        if (pedido.getQuantidade() != 0) valores.put("quantidade", Integer.toString(pedido.getQuantidade()));
        if (pedido.getItens() != null) {
            for (int indice = 0; indice < pedido.getItens().size(); indice++) {
                ItemPedido item = pedido.getItens().get(indice);
                if (item == null) continue;
                String campo = "itens[" + indice + "][";
                valores.put(campo + "produtoId]", item.getProdutoId());
                valores.put(campo + "nomeProduto]", item.getNomeProduto());
                valores.put(campo + "quantidade]", Integer.toString(item.getQuantidade()));
                valores.put(campo + "precoUnitario]", item.getPrecoUnitario() == null
                        ? null : item.getPrecoUnitario().toPlainString());
            }
        }
        return valores;
    }

}
