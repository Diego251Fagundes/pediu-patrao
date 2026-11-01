(() => {
    'use strict';
    const painel = document.getElementById('dashboard');
    if (!painel) return;

    const formulario = document.getElementById('dashboard-filtros');
    const inicio = document.getElementById('data-inicio');
    const fim = document.getElementById('data-fim');
    const atualizar = document.getElementById('dashboard-atualizar');
    const resultados = document.getElementById('dashboard-resultados');
    const erro = document.getElementById('dashboard-erro');
    const status = document.getElementById('dashboard-status');
    const moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
    const numero = new Intl.NumberFormat('pt-BR');
    let consultaAtual = 0;
    let controle;
    let periodoAplicado;

    // Usa o calendário local, evitando a mudança de dia causada por toISOString().
    function dataLocal(data) {
        return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}-${String(data.getDate()).padStart(2, '0')}`;
    }
    const hoje = new Date();
    const primeiroDia = new Date(hoje);
    primeiroDia.setDate(hoje.getDate() - 29);
    inicio.value = dataLocal(primeiroDia);
    fim.value = dataLocal(hoje);

    async function consultar(recurso, parametros, signal) {
        const resposta = await fetch(`${painel.dataset.api}${recurso}?${parametros}`, {
            headers: { Accept: 'application/json' }, credentials: 'same-origin', signal
        });
        if (resposta.status === 401 || resposta.redirected) {
            throw new Error('Sua sessão expirou. Entre novamente para consultar o dashboard.');
        }
        if (resposta.status === 403) throw new Error('Você não tem permissão para consultar o dashboard.');
        if (!resposta.ok) throw new Error('Não foi possível consultar os dados. Tente atualizar novamente.');
        return resposta.json();
    }

    function mostrarIndicadores(dados) {
        document.getElementById('valor-vendas').textContent = moeda.format(dados.valorVendas);
        document.getElementById('ticket-medio').textContent = moeda.format(dados.ticketMedio);
        document.getElementById('pedidos-finalizados').textContent = numero.format(dados.pedidosFinalizados);
        document.getElementById('total-pedidos').textContent = numero.format(dados.totalPedidos);
        document.getElementById('pedidos-cancelados').textContent = numero.format(dados.pedidosCancelados);
        document.getElementById('dashboard-vazio').hidden = dados.totalPedidos !== 0;
    }

    async function carregar(periodo) {
        const consulta = ++consultaAtual;
        if (controle) controle.abort();
        controle = new AbortController();
        resultados.hidden = true;
        resultados.setAttribute('aria-busy', 'true');
        erro.hidden = true;
        status.textContent = 'Carregando dados…';
        atualizar.disabled = true;
        try {
            const parametros = new URLSearchParams(periodo);
            const indicadores = await consultar('indicadores', parametros, controle.signal);
            if (consulta !== consultaAtual) return;
            mostrarIndicadores(indicadores);
            periodoAplicado = { ...periodo };
            resultados.hidden = false;
            const rotulo = data => data.split('-').reverse().join('/');
            status.textContent = `Período: ${rotulo(periodo.dataInicio)} a ${rotulo(periodo.dataFim)}. Atualizado às ${new Date().toLocaleTimeString('pt-BR')}.`;
        } catch (falha) {
            if (consulta !== consultaAtual || falha.name === 'AbortError') return;
            erro.textContent = falha.message;
            erro.hidden = false;
            status.textContent = 'Dados indisponíveis. Use Atualizar para tentar novamente.';
        } finally {
            if (consulta === consultaAtual) {
                resultados.setAttribute('aria-busy', 'false');
                atualizar.disabled = false;
            }
        }
    }

    formulario.addEventListener('submit', evento => {
        evento.preventDefault();
        fim.setCustomValidity(inicio.value > fim.value ? 'A data final deve ser igual ou posterior à inicial.' : '');
        if (formulario.reportValidity()) carregar({ dataInicio: inicio.value, dataFim: fim.value });
    });
    [inicio, fim].forEach(campo => campo.addEventListener('input', () => fim.setCustomValidity('')));
    atualizar.addEventListener('click', () => carregar(periodoAplicado || { dataInicio: inicio.value, dataFim: fim.value }));
    carregar({ dataInicio: inicio.value, dataFim: fim.value });
})();
