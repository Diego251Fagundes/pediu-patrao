package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class AuditoriaService {
    @Autowired
    private AuditoriaRepository auditoriaRepository;

    public void registrar(String operacao, String entidade, String entidadeId,
            Map<String, String> anteriores, Map<String, String> novos, String justificativa,
            Authentication authentication) {
        Map<String, String> valoresAnteriores = new LinkedHashMap<>();
        Map<String, String> valoresNovos = new LinkedHashMap<>();
        var campos = new LinkedHashSet<>(anteriores.keySet());
        campos.addAll(novos.keySet());
        for (String campo : campos) {
            if (!Objects.equals(anteriores.get(campo), novos.get(campo))) {
                valoresAnteriores.put(campo, anteriores.get(campo));
                valoresNovos.put(campo, novos.get(campo));
            }
        }
        if (valoresNovos.isEmpty() && anteriores.equals(novos)) {
            return;
        }
        Auditoria auditoria = new Auditoria();
        auditoria.setUsuario(authentication.getName());
        auditoria.setPerfil(authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(perfil -> perfil.startsWith("ROLE_"))
                .map(perfil -> perfil.substring(5))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Perfil não encontrado.")));
        auditoria.setDataHora(LocalDateTime.now());
        auditoria.setOperacao(operacao);
        auditoria.setEntidade(entidade);
        auditoria.setEntidadeId(entidadeId);
        auditoria.setValoresAnteriores(valoresAnteriores);
        auditoria.setValoresNovos(valoresNovos);
        auditoria.setJustificativa(justificativa);
        auditoriaRepository.insert(auditoria);
    }

    public List<Auditoria> listar() {
        return auditoriaRepository.findAll(Sort.by(Sort.Direction.DESC, "dataHora"));
    }
}
