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
    const nomesSituacoes = {
        RECEBIDO: 'Recebido', EM_PREPARACAO: 'Em preparação', PRONTO: 'Pronto',
        SAIU_PARA_ENTREGA: 'Saiu para entrega', RETIRADO: 'Retirado', FINALIZADO: 'Finalizado', CANCELADO: 'Cancelado'
    };
    let graficos = [];
    let consultaAtual = 0;
    let controle;
    let periodoAplicado;
    const rotuloData = data => data.split('-').reverse().join('/');

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

    function preencherTabela(id, linhas) {
        const tabela = document.getElementById(id);
        tabela.replaceChildren();
        linhas.forEach(valores => {
            const linha = document.createElement('tr');
            valores.forEach((valor, indice) => {
                const celula = document.createElement('td');
                celula.textContent = valor;
                if (indice === valores.length - 1) celula.className = 'text-end';
                linha.appendChild(celula);
            });
            tabela.appendChild(linha);
        });
    }

    function mostrarDetalhes(vendas, situacoes, ranking) {
        preencherTabela('tabela-vendas', vendas.map(dia =>
            [rotuloData(dia.data), numero.format(dia.quantidadePedidos), moeda.format(dia.valorVendas)]));
        preencherTabela('tabela-situacoes', situacoes.map(situacao =>
            [nomesSituacoes[situacao.status] || situacao.status || 'Sem situação', numero.format(situacao.quantidadePedidos)]));
        preencherTabela('tabela-ranking', ranking.map((produto, indice) =>
            [numero.format(indice + 1), produto.nomeProduto, numero.format(produto.quantidadeVendida)]));
        document.getElementById('ranking-vazio').hidden = ranking.length !== 0;
        document.getElementById('ranking-tabela').hidden = ranking.length === 0;
        document.getElementById('vendas-vazias').hidden = vendas.some(dia => dia.quantidadePedidos > 0);
    }

    function mostrarGraficos(vendas, situacoes) {
        graficos.forEach(grafico => grafico.destroy());
        graficos = [];
        const disponivel = typeof Chart === 'function';
        document.getElementById('dashboard-aviso-graficos').hidden = disponivel;
        document.querySelectorAll('.dashboard-grafico').forEach(elemento => { elemento.hidden = !disponivel; });
        if (!disponivel) return;
        try {
            graficos.push(new Chart(document.getElementById('grafico-vendas'), {
                type: 'line',
                data: {
                    labels: vendas.map(dia => rotuloData(dia.data)),
                    datasets: [{ label: 'Valor das vendas (R$)', data: vendas.map(dia => dia.valorVendas),
                        borderColor: '#0d6efd', backgroundColor: 'transparent', fill: false, lineTension: 0, pointRadius: 2 }]
                },
                options: { responsive: true, maintainAspectRatio: false, animation: { duration: 0 },
                    legend: { display: false },
                    scales: { yAxes: [{ ticks: { beginAtZero: true, callback: valor => moeda.format(valor) } }],
                        xAxes: [{ ticks: { maxTicksLimit: 8 } }] },
                    tooltips: { callbacks: { label: item => moeda.format(item.yLabel) } } }
            }));
            graficos.push(new Chart(document.getElementById('grafico-situacoes'), {
                type: 'horizontalBar',
                data: {
                    labels: situacoes.map(situacao => nomesSituacoes[situacao.status] || situacao.status || 'Sem situação'),
                    datasets: [{ label: 'Pedidos', data: situacoes.map(situacao => situacao.quantidadePedidos), backgroundColor: '#495057' }]
                },
                options: { responsive: true, maintainAspectRatio: false, animation: { duration: 0 },
                    legend: { display: false },
                    scales: { xAxes: [{ ticks: { beginAtZero: true, precision: 0 } }] } }
            }));
        } catch {
            document.getElementById('dashboard-aviso-graficos').hidden = false;
            document.querySelectorAll('.dashboard-grafico').forEach(elemento => { elemento.hidden = true; });
        }
    }

    async function carregar(periodo) {
        const consulta = ++consultaAtual;
        if (controle) controle.abort();
        controle = new AbortController();
        const controleDaConsulta = controle;
        let demorou = false;
        const temporizador = setTimeout(() => { demorou = true; controleDaConsulta.abort(); }, 15000);
        resultados.hidden = true;
        resultados.setAttribute('aria-busy', 'true');
        erro.hidden = true;
        status.textContent = 'Carregando dados…';
        atualizar.disabled = true;
        try {
            const parametros = new URLSearchParams(periodo);
            const [indicadores, vendas, situacoes, ranking] = await Promise.all(
                ['indicadores', 'vendas', 'situacoes', 'produtos-mais-vendidos']
                    .map(recurso => consultar(recurso, parametros, controleDaConsulta.signal)));
            if (consulta !== consultaAtual) return;
            mostrarIndicadores(indicadores);
            mostrarDetalhes(vendas, situacoes, ranking);
            periodoAplicado = { ...periodo };
            resultados.hidden = false;
            mostrarGraficos(vendas, situacoes);
            status.textContent = `Período: ${rotuloData(periodo.dataInicio)} a ${rotuloData(periodo.dataFim)}. Atualizado às ${new Date().toLocaleTimeString('pt-BR')}.`;
        } catch (falha) {
            if (consulta !== consultaAtual) return;
            controleDaConsulta.abort();
            resultados.hidden = true;
            erro.textContent = demorou ? 'A consulta demorou demais. Tente atualizar novamente.' : falha.message;
            erro.hidden = false;
            status.textContent = 'Dados indisponíveis. Use Atualizar para tentar novamente.';
        } finally {
            clearTimeout(temporizador);
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
