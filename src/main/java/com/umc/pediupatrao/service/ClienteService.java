package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.repository.ClienteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;

@Service
public class ClienteService {

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    public Cliente novoCliente(Cliente cliente, Authentication authentication) {
        return salvar(cliente, authentication);
    }

    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    public void excluir(String id, Authentication authentication) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado."));
        Map<String, String> anteriores = valoresCliente(cliente);
        clienteRepository.deleteById(id);
        auditoriaService.registrar("EXCLUSAO_CLIENTE", "CLIENTE", id, anteriores, Map.of(), null, authentication);
    }

    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    public Cliente salvar(Cliente cliente, Authentication authentication) {
        boolean novo = cliente.getId() == null;
        Map<String, String> anteriores = novo ? Map.of()
                : valoresCliente(clienteRepository.findById(cliente.getId())
                        .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado para atualização.")));
        Cliente salvo = clienteRepository.save(cliente);
        auditoriaService.registrar(novo ? "CADASTRO_CLIENTE" : "ALTERACAO_CLIENTE", "CLIENTE", salvo.getId(),
                anteriores, valoresCliente(salvo), null, authentication);
        return salvo;
    }

    private Map<String, String> valoresCliente(Cliente cliente) {
        Map<String, String> valores = new LinkedHashMap<>();
        valores.put("nome", cliente.getNome());
        valores.put("telefone", cliente.getTelefone());
        valores.put("cep", cliente.getCep());
        valores.put("logradouro", cliente.getLogradouro());
        valores.put("numero", cliente.getNumero());
        valores.put("complemento", cliente.getComplemento());
        valores.put("bairro", cliente.getBairro());
        valores.put("cidade", cliente.getCidade());
        valores.put("estado", cliente.getEstado());
        valores.put("endereco", cliente.getEndereco());
        return valores;
    }

}
