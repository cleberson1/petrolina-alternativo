# Petrolina Alternativo

App Android (Kotlin + Jetpack Compose, Material 3) com horários do transporte alternativo
(vans/topics) de Petrolina-PE. Sem GPS, sem mapas, sem backend. Funciona offline com um JSON
embutido e, quando há internet, baixa uma versão atualizada do GitHub.

## Compilar no Manjaro com Android Studio

1. Instale: `sudo pacman -S jdk17-openjdk` e o Android Studio (AUR: `yay -S android-studio`,
   ou o tarball oficial).
2. Abra a pasta do projeto em **File > Open**. O Android Studio baixa o Gradle, o SDK 34 e as
   dependências sozinho (precisa de internet na primeira vez; o wrapper do Gradle é gerado pelo IDE).
3. Se pedir, aceite instalar o **Android SDK Platform 34**.
4. Imagens (não incluídas). Coloque em `app/src/main/res/drawable/`:
   - `logo.png` — logo do app (rodapé)
   - `cc_by_sa.png` — faixa Creative Commons BY-SA 4.0 (rodapé)
   - `logo_coop.png` — logo da empresa de exemplo (qualquer logo de empresa: use o nome
     em minúsculas, sem espaços, igual ao campo `"logo"` do JSON)

   Exceção: o `logo.png` é **obrigatório**, porque também é o ícone do app (sem ele o build
   falha). As demais imagens são opcionais: sem elas o app compila e só não as exibe.
5. Rode no emulador/celular (**Run**) ou gere o APK em **Build > Build APK(s)**.

Observação técnica: como `minSdk = 24` e o app usa `java.time`, o projeto ativa o *core library
desugaring* (`desugar_jdk_libs`, ferramenta de build do Google; não vai lógica extra no APK além
do necessário).

## Editando o horarios.json

Arquivo embutido: `app/src/main/assets/horarios.json`. Cópia oficial para atualização: `updates/horarios.json`
no repositório do GitHub (mesmo formato; já há uma cópia em `updates/` neste projeto). Campos:

- `atualizado_em`, `locais`, `rotas` — obrigatórios. Feriados e empresas ficam em arquivos separados (abaixo).
- Rota: `origem`, `destino`, `horarios` obrigatórios; `observacao` e `itinerario` opcionais
  (sem `itinerario`, o botão "Itinerário" não aparece).
- Rota: `empresa` (sigla, igual à `sigla` em `empresas`) e `saida` (ponto de saída mostrado na
  tabela; se faltar, usa a `origem`). Várias empresas podem ter a mesma origem/destino: basta
  uma rota para cada empresa; a tela de horários junta todas numa só tabela
  (horário | saída | empresa).
- Horários no formato `"HH:mm"` (24h, com zero à esquerda). Lista vazia `[]` = não circula naquele dia.
- Regra do dia: sábado > domingo > feriado (só em dia útil) > dia útil. Os feriados vêm do `feriados.json`.
- Valide o JSON antes de publicar (ex.: `python -m json.tool horarios.json`). O app ignora
  arquivos inválidos e continua com o anterior.

## Feriados (feriados.json)

Arquivo separado: `app/src/main/assets/feriados.json` (cópia oficial em `updates/feriados.json`).
A lista é **recorrente**: vale para qualquer ano, então não precisa ser refeita todo ano.

- Feriado fixo: `{ "dia_mes": "09-07", "nome": "Independência do Brasil" }` (formato `MM-dd`).
- Feriado móvel: `{ "dias_apos_pascoa": 60, "nome": "Corpus Christi" }`. O app calcula a Páscoa
  de cada ano (Paixão de Cristo = -2, Páscoa = 0, Corpus Christi = 60; Carnaval seria -47).
- Feriado parcial (só rede de ensino, servidores etc.): acrescente `"parcial": true`. O app
  **ignora** esses e usa a tabela normal do dia. Troque para `false` (ou remova) se quiser que valha.

### Checklist (só quando algo mudar)

- [ ] Surgiu ou acabou algum feriado municipal/estadual/nacional? Edite o `feriados.json`.
- [ ] Algum feriado deixou de ter data fixa (ex.: "segunda-feira mais próxima")? Esse tipo não é
      suportado: ajuste o `dia_mes` quando mudar, ou peça uma nova regra ao desenvolvedor.
- [ ] Atualizar `atualizado_em` e publicar no GitHub.

## Empresas (empresas.json)

Arquivo separado: `app/src/main/assets/empresas.json` (cópia oficial em `updates/empresas.json`).
Formato: `{ "atualizado_em": "...", "empresas": [ ... ] }`. Cada empresa tem:

- `nome` (obrigatório), `sigla`, `telefone`
- `logo` (opcional): nome do arquivo em `res/drawable/` (ex.: `"atpi.png"`). A imagem fica dentro do
  app, não é baixada do GitHub: logo de empresa nova exige um APK novo.
- `presidente`, `vice_presidente`, `diretor`, `secretario`: todos opcionais, cada um com `nome` e
  `telefone`. Cargo que não existe na empresa: apague do JSON (ou deixe o `nome` vazio) e ele não
  aparece no app.

A `sigla` da empresa deve ser igual ao campo `"empresa"` usado nas rotas do `horarios.json`
(é o que aparece na coluna "Empresa" da tabela de horários).

## Itinerários em HTML

Em vez do texto `"itinerario"`, a rota pode apontar para um arquivo HTML:

```json
{ "origem": "...", "destino": "...", "itinerario_html": "terminal_cohab.html", ... }
```

- O arquivo fica em `updates/itinerarios/terminal_cohab.html` no GitHub (uma cópia embutida em
  `app/src/main/assets/itinerarios/`, usada quando não há internet).
- Nome simples: letras, números, `_` e `-`, terminando em `.html` (sem pastas). Nome fora desse
  padrão é ignorado.
- O app baixa os HTMLs citados nas rotas junto com os JSONs. Sem o HTML, mostra o texto
  `"itinerario"` (se existir); sem nenhum dos dois, o botão "Itinerário" não aparece.
- Regras do HTML: arquivo único, com texto e CSS dentro do próprio arquivo. **JavaScript, links e
  carregamento de internet são bloqueados**; para imagens use `data:` (base64) embutido no HTML.
- Dica: mantenha cada arquivo pequeno (alguns KB), pois todos são baixados a cada abertura.

## Tarifas (tarifas.json)

Arquivo separado: `app/src/main/assets/tarifas.json` (cópia oficial em `updates/tarifas.json`).
A tela "Tarifas" mostra, acima da planilha, uma linha com a portaria ou decreto e um ícone de link.

```json
{
  "atualizado_em": "2026-11-02",
  "legislacao": {
    "tipo": "Portaria",
    "numero": "123/2026",
    "descricao": "Texto curto (opcional)",
    "url": "https://link-da-legislacao"
  },
  "colunas": ["Trecho", "Inteira", "Estudante"],
  "linhas": [ ["Terminal Central → Cohab Massangano", "R$ 4,50", "R$ 2,25"] ]
}
```

- `legislacao` é opcional; `tipo` ("Portaria", "Decreto"...) é obrigatório nela. `numero`,
  `descricao` e `url` são opcionais (sem `url`, o ícone de link não aparece; só links
  `http://` ou `https://` são aceitos).
- `colunas` e `linhas` formam a planilha e você define as colunas livremente. Cada linha é ajustada
  ao número de colunas (falta = vazio, excesso = ignorado). A 1ª coluna é mais larga.
- Os valores do exemplo são fictícios.

## Publicar no GitHub

1. Crie o repositório público `petrolina-alternativo`.
2. Envie `updates/horarios.json`, `updates/feriados.json`, `updates/empresas.json`, `updates/tarifas.json` e os HTMLs de
   `updates/itinerarios/` para a branch `main`.
3. URLs raw usadas pelo app (sem token):
   `https://raw.githubusercontent.com/cleberson1/petrolina-alternativo/main/updates/horarios.json`
   `https://raw.githubusercontent.com/cleberson1/petrolina-alternativo/main/updates/feriados.json`
   `https://raw.githubusercontent.com/cleberson1/petrolina-alternativo/main/updates/empresas.json`
   `https://raw.githubusercontent.com/cleberson1/petrolina-alternativo/main/updates/tarifas.json`
4. Essa URL já está em `AtualizadorWorker.URL_JSON`. Atenção: use sempre `raw.githubusercontent.com`, não `github.com`.
5. Para atualizar horários: edite o arquivo e faça commit na `main`. Os celulares recebem
   dentro de ~1 dia (o cache do GitHub raw pode atrasar alguns minutos).

## Atualização automática (WorkManager)

- `App.onCreate` agenda um `PeriodicWorkRequest` de 1 dia, só com rede, em modo `KEEP`
  (não duplica).
- O `AtualizadorWorker` baixa os arquivos (horários, feriados, empresas, tarifas e itinerários) com `HttpURLConnection`, valida cada um (JSON parseável + campos
  obrigatórios) e grava em `filesDir/horarios_atualizado.json` `filesDir/feriados_atualizado.json` e `filesDir/empresas_atualizado.json`. Falha de rede = tenta depois;
  JSON inválido = descarta. Nenhuma notificação.
- `carregarDados()` usa o arquivo atualizado se existir e for válido; senão, o embutido.
  Os dados novos aparecem na próxima abertura do app.

### Sem internet (só Wi-Fi em casa)

Os dados baixados ficam guardados no aparelho e continuam valendo sem internet. Se o app for
aberto sem conexão, a verificação fica pendente e roda sozinha quando o aparelho conectar (Wi-Fi
ou dados), mesmo com o app fechado. Na abertura seguinte, os dados novos já estão lá.

## Atualização não apareceu?

O app baixa os JSONs a cada abertura (quando há internet) e também 1x por dia. Confira:

1. Os arquivos estão em `updates/horarios.json` e `updates/feriados.json`, na branch **main**,
   e o commit foi enviado (push)?
2. Abra no navegador do celular a URL raw (seção "Publicar no GitHub"). Se der 404, o caminho está
   errado. O GitHub raw pode demorar alguns minutos para refletir um commit novo.
3. O JSON é válido e tem os campos obrigatórios (`atualizado_em`, `locais`, `rotas`)? JSON inválido
   é descartado em silêncio e o app mantém os dados anteriores.
4. Abra o app com internet, espere alguns segundos (a tela atualiza sozinha) ou reabra.
5. Para ver o motivo exato: Android Studio > **Logcat**, filtro `AtualizadorWorker`. Ele mostra
   o código HTTP, falhas de rede e o erro de validação do JSON.
6. Editou o arquivo **embutido** (`assets/`) e o app mostra dados antigos? Um arquivo já baixado
   tem prioridade sobre o embutido. Desinstale o app (ou limpe os dados) para testar o embutido.
7. Mudou os nomes das empresas no `horarios.json`? As empresas agora ficam só no `empresas.json`.

## Licenciamento

- Código: **GPLv3**
- Dados (horarios.json) e identidade visual: **CC BY-SA 4.0**

Inclua os textos completos das licenças (`LICENSE`) no repositório.

## Permissões

O app declara **somente `INTERNET`**, usada apenas para baixar o `horarios.json` atualizado do
GitHub. Funciona 100% offline sem ela, e nenhum dado do usuário é coletado ou enviado.
