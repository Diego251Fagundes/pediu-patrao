const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const { runInNewContext } = require('node:vm');

const codigo = readFileSync(join(__dirname, '../../main/resources/static/js/dashboard.js'), 'utf8');
const esperar = () => new Promise(setImmediate);
const dados = (total = 2) => ({
    indicadores: { totalPedidos: total, pedidosFinalizados: total, pedidosCancelados: 0, valorVendas: 99.90, ticketMedio: 49.95 },
    vendas: [{ data: '2026-11-01', quantidadePedidos: total, valorVendas: 99.90 }],
    situacoes: [{ status: 'FINALIZADO', quantidadePedidos: total }],
    'produtos-mais-vendidos': [{ produtoId: 'pizza', nomeProduto: '<img src=x onerror=alert(1)>', quantidadeVendida: 3 }]
});
const resposta = corpo => ({ ok: true, status: 200, redirected: false, json: async () => corpo });

function iniciar(fetchPersonalizado, semGraficos = false) {
    const elementos = new Map();
    function elemento() {
        return {
            hidden: false, disabled: false, value: '', textContent: '', children: [], events: {}, dataset: {},
            addEventListener(nome, acao) { this.events[nome] = acao; },
            setAttribute(nome, valor) { this[nome] = valor; },
            setCustomValidity(mensagem) { this.validationMessage = mensagem; },
            reportValidity() { return !obter('data-fim').validationMessage && obter('data-inicio').value && obter('data-fim').value; },
            replaceChildren() { this.children = []; },
            appendChild(filho) { this.children.push(filho); }
        };
    }
    const obter = id => {
        if (!elementos.has(id)) elementos.set(id, elemento());
        return elementos.get(id);
    };
    obter('dashboard').dataset.api = '/api/dashboard/';
    const requisicoes = [];
    const graficos = [];
    const ambiente = {
        document: { getElementById: obter, createElement: elemento, querySelectorAll: () => [] },
        Intl, Date, AbortController, URLSearchParams, setTimeout, clearTimeout,
        fetch: (url, opcoes) => {
            requisicoes.push({ url, opcoes });
            if (fetchPersonalizado) return fetchPersonalizado(url, opcoes);
            return Promise.resolve(resposta(dados()[url.split('/').pop().split('?')[0]]));
        }
    };
    if (!semGraficos) ambiente.Chart = function (canvas, configuracao) {
        this.destroy = () => {};
        graficos.push(configuracao);
    };
    runInNewContext(codigo, ambiente);
    return { obter, requisicoes, graficos,
        aplicar: () => obter('dashboard-filtros').events.submit({ preventDefault() {} }),
        atualizar: () => obter('dashboard-atualizar').events.click() };
}

test('consulta os quatro recursos, formata valores e insere nomes como texto', async () => {
    const tela = iniciar();
    await esperar();
    assert.equal(tela.requisicoes.length, 4);
    assert.equal(new Set(tela.requisicoes.map(r => r.url.split('?')[1])).size, 1);
    assert.equal(tela.obter('total-pedidos').textContent, '2');
    assert.match(tela.obter('valor-vendas').textContent, /99,90/);
    assert.equal(tela.obter('dashboard-resultados').hidden, false);
    assert.equal(tela.obter('tabela-ranking').children[0].children[1].textContent, dados()['produtos-mais-vendidos'][0].nomeProduto);
    assert.equal(tela.graficos.length, 2);
});

test('apresenta período vazio e tabelas mesmo sem biblioteca de gráficos', async () => {
    const vazios = { indicadores: dados(0).indicadores, vendas: [], situacoes: [], 'produtos-mais-vendidos': [] };
    const tela = iniciar(url => Promise.resolve(resposta(vazios[url.split('/').pop().split('?')[0]])), true);
    await esperar();
    assert.equal(tela.obter('dashboard-vazio').hidden, false);
    assert.equal(tela.obter('ranking-vazio').hidden, false);
    assert.equal(tela.obter('ranking-tabela').hidden, true);
    assert.equal(tela.obter('dashboard-aviso-graficos').hidden, false);
    assert.equal(tela.obter('dashboard-resultados').hidden, false);
});

test('oculta resultados anteriores quando qualquer consulta falha e permite nova tentativa', async () => {
    let falhar = false;
    const tela = iniciar(url => {
        const recurso = url.split('/').pop().split('?')[0];
        if (falhar && recurso === 'situacoes') return Promise.reject(new Error('Falha de conexão'));
        return Promise.resolve(resposta(dados()[recurso]));
    });
    await esperar();
    falhar = true;
    tela.atualizar();
    await esperar();
    assert.equal(tela.obter('dashboard-resultados').hidden, true);
    assert.equal(tela.obter('dashboard-erro').hidden, false);
    assert.equal(tela.obter('dashboard-atualizar').disabled, false);
    falhar = false;
    tela.atualizar();
    await esperar();
    assert.equal(tela.obter('dashboard-resultados').hidden, false);
    assert.equal(tela.obter('dashboard-erro').hidden, true);
});

test('descarta respostas atrasadas após aplicar outro período', async () => {
    const pendentes = [];
    const tela = iniciar(url => new Promise(resolve => pendentes.push({ url, resolve })));
    tela.obter('data-inicio').value = '2026-10-01';
    tela.obter('data-fim').value = '2026-10-31';
    tela.aplicar();
    const completar = (inicio, total) => pendentes.slice(inicio, inicio + 4).forEach(({ url, resolve }) =>
        resolve(resposta(dados(total)[url.split('/').pop().split('?')[0]])));
    completar(4, 5);
    await esperar();
    completar(0, 2);
    await esperar();
    assert.equal(tela.obter('total-pedidos').textContent, '5');
    assert.match(tela.obter('dashboard-status').textContent, /01\/10\/2026 a 31\/10\/2026/);
    assert.equal(tela.requisicoes[0].opcoes.signal.aborted, true);
});

test('Atualizar mantém o período aplicado mesmo com datas editadas sem aplicar', async () => {
    const tela = iniciar();
    await esperar();
    const periodo = tela.requisicoes[0].url.split('?')[1];
    tela.obter('data-inicio').value = '2020-01-01';
    tela.atualizar();
    await esperar();
    assert.equal(tela.requisicoes[4].url.split('?')[1], periodo);
});

test('rejeita datas invertidas antes de consultar a API', async () => {
    const tela = iniciar();
    await esperar();
    tela.obter('data-inicio').value = '2026-11-02';
    tela.obter('data-fim').value = '2026-11-01';
    tela.aplicar();
    assert.equal(tela.requisicoes.length, 4);
    assert.match(tela.obter('data-fim').validationMessage, /posterior/);
});

test('informa sessão expirada sem mostrar resultados parciais', async () => {
    const tela = iniciar(() => Promise.resolve({ status: 401, ok: false }));
    await esperar();
    assert.match(tela.obter('dashboard-erro').textContent, /sessão expirou/);
    assert.equal(tela.obter('dashboard-resultados').hidden, true);
    assert.equal(tela.obter('dashboard-atualizar').disabled, false);
});
