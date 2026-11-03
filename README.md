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

   Sem os arquivos o app compila e roda normalmente; só não exibe as imagens.
5. Rode no emulador/celular (**Run**) ou gere o APK em **Build > Build APK(s)**.

Observação técnica: como `minSdk = 24` e o app usa `java.time`, o projeto ativa o *core library
desugaring* (`desugar_jdk_libs`, ferramenta de build do Google; não vai lógica extra no APK além
do necessário).

## Editando o horarios.json

Arquivo embutido: `app/src/main/assets/horarios.json`. Cópia oficial para atualização: `updates/horarios.json`
no repositório do GitHub (mesmo formato; já há uma cópia em `updates/` neste projeto). Campos:

- `atualizado_em`, `locais`, `rotas` — obrigatórios (feriados ficam em arquivo separado, abaixo). `empresas` — opcional.
- Rota: `origem`, `destino`, `horarios` obrigatórios; `observacao` e `itinerario` opcionais
  (sem `itinerario`, o botão "Itinerário" não aparece).
- Rota: `empresa` (sigla, igual à `sigla` em `empresas`) e `saida` (ponto de saída mostrado na
  tabela; se faltar, usa a `origem`). Várias empresas podem ter a mesma origem/destino: basta
  uma rota para cada empresa; a tela de horários junta todas numa só tabela
  (horário | saída | empresa).
- Empresa: `sigla` (opcional), `logo` (opcional, nome do arquivo em `res/drawable/`).
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

## Publicar no GitHub

1. Crie o repositório público `petrolina-alternativo`.
2. Envie `updates/horarios.json` e `updates/feriados.json` para a branch `main`.
3. URLs raw usadas pelo app (sem token):
   `https://raw.githubusercontent.com/cleberson1/petrolina-alternativo/main/updates/horarios.json`
   `https://raw.githubusercontent.com/cleberson1/petrolina-alternativo/main/updates/feriados.json`
4. Essa URL já está em `AtualizadorWorker.URL_JSON`. Atenção: use sempre `raw.githubusercontent.com`, não `github.com`.
5. Para atualizar horários: edite o arquivo e faça commit na `main`. Os celulares recebem
   dentro de ~1 dia (o cache do GitHub raw pode atrasar alguns minutos).

## Atualização automática (WorkManager)

- `App.onCreate` agenda um `PeriodicWorkRequest` de 1 dia, só com rede, em modo `KEEP`
  (não duplica).
- O `AtualizadorWorker` baixa os dois arquivos (horários e feriados) com `HttpURLConnection`, valida cada um (JSON parseável + campos
  obrigatórios) e grava em `filesDir/horarios_atualizado.json` e `filesDir/feriados_atualizado.json`. Falha de rede = tenta depois;
  JSON inválido = descarta. Nenhuma notificação.
- `carregarDados()` usa o arquivo atualizado se existir e for válido; senão, o embutido.
  Os dados novos aparecem na próxima abertura do app.

## Licenciamento

- Código: **GPLv3**
- Dados (horarios.json) e identidade visual: **CC BY-SA 4.0**

Inclua os textos completos das licenças (`LICENSE`) no repositório.

## Permissões

O app declara **somente `INTERNET`**, usada apenas para baixar o `horarios.json` atualizado do
GitHub. Funciona 100% offline sem ela, e nenhum dado do usuário é coletado ou enviado.
