package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.IndicadoresDashboard;
import com.umc.pediupatrao.dto.SituacaoPedido;
import com.umc.pediupatrao.dto.VendaDiaria;
import com.umc.pediupatrao.service.DashboardService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/indicadores")
    public IndicadoresDashboard indicadores(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {
        return dashboardService.consultarIndicadores(dataInicio, dataFim);
    }

    @GetMapping("/vendas")
    public List<VendaDiaria> vendas(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {
        return dashboardService.consultarVendasDiarias(dataInicio, dataFim);
    }

    @GetMapping("/situacoes")
    public List<SituacaoPedido> situacoes(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {
        return dashboardService.consultarSituacoes(dataInicio, dataFim);
    }

    @ExceptionHandler({IllegalArgumentException.class, MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> tratarPeriodoInvalido(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("erro",
                "Informe dataInicio e dataFim no formato AAAA-MM-DD, com início anterior ou igual ao fim."));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> tratarDadosInconsistentes() {
        return ResponseEntity.internalServerError().body(Map.of("erro",
                "Não foi possível calcular as vendas. Verifique os valores dos pedidos finalizados."));
    }
}
