package org.petrolinaalternativo.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

private enum class Tela { INICIAL, HORARIOS, SOBRE, CONTATOS }

/** Cor de fundo do app: CMYK C25 M5 Y0 K9 = RGB #AEDCE8. */
val CorFundo = Color(0xFFAEDCE8)

private const val AUTORIA = "Cleberson Carlos"
private const val URL_GITHUB = "https://github.com/cleberson1"
private const val EMAIL_CONTATO = "carlos.web2010@hotmail.com"

/** Raiz do app: Scaffold + Drawer; alterna as telas por estado (sem NavHost). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPetrolina() {
    val contexto = LocalContext.current
    val dados = remember { carregarDados(contexto) }
    var tela by rememberSaveable { mutableStateOf(Tela.INICIAL) }
    var origem by rememberSaveable { mutableStateOf("") }
    var destino by rememberSaveable { mutableStateOf("") }
    val estadoDrawer = rememberDrawerState(DrawerValue.Closed)
    val escopo = rememberCoroutineScope()

    BackHandler(enabled = tela != Tela.INICIAL) { tela = Tela.INICIAL }

    fun ir(alvo: Tela) {
        tela = alvo
        escopo.launch { estadoDrawer.close() }
    }

    // Layout RTL só no Drawer, para ele abrir pela DIREITA (o ícone fica no canto superior direito).
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = estadoDrawer,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ModalDrawerSheet {
                        Text(
                            "Menu",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                        NavigationDrawerItem(
                            label = { Text("Horários") },
                            selected = tela == Tela.INICIAL || tela == Tela.HORARIOS,
                            onClick = { ir(Tela.INICIAL) }
                        )
                        NavigationDrawerItem(
                            label = { Text("Contatos das empresas") },
                            selected = tela == Tela.CONTATOS,
                            onClick = { ir(Tela.CONTATOS) }
                        )
                        NavigationDrawerItem(
                            label = { Text("Sobre o aplicativo") },
                            selected = tela == Tela.SOBRE,
                            onClick = { ir(Tela.SOBRE) }
                        )
                    }
                }
            }
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Scaffold(
                    containerColor = if (tela == Tela.INICIAL) CorFundo else Color.White,
                    topBar = {
                        TopAppBar(
                            // Na tela inicial o nome fica abaixo da logo, não na barra
							title = { if (tela != Tela.INICIAL) Text("Petrolina Alternativo", fontWeight = FontWeight.Bold) },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = CorFundo),
                            actions = {
                                IconButton(onClick = { escopo.launch { estadoDrawer.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Abrir menu")
                                }
                            }
                        )
                    }
                ) { margem ->
                    val modificador = Modifier.padding(margem)
                    when (tela) {
                        Tela.INICIAL -> TelaInicial(
                            dados = dados,
                            origem = origem,
                            destino = destino,
                            aoEscolherOrigem = { nova ->
                                origem = nova
                                // Se o destino atual não existe a partir da nova origem, limpa
                                if (rotasDe(dados, nova, destino).isEmpty()) destino = ""
                            },
                            aoEscolherDestino = { novo ->
                                destino = novo
                                tela = Tela.HORARIOS   // abre a tela de horários
                            },
                            modifier = modificador
                        )
                        Tela.HORARIOS -> TelaHorarios(dados, origem, destino, modificador) {
                            tela = Tela.INICIAL
                        }
                        Tela.SOBRE -> TelaSobre(modificador) { tela = Tela.INICIAL }
                        Tela.CONTATOS -> TelaContatos(dados, modificador) { tela = Tela.INICIAL }
                    }
                }
            }
        }
    }
}

// ---------- Tela inicial ----------

@Composable
fun TelaInicial(
    dados: Dados,
    origem: String,
    destino: String,
    aoEscolherOrigem: (String) -> Unit,
    aoEscolherDestino: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val destinos = dados.rotas.filter { it.origem == origem }.map { it.destino }.distinct()

    Column(modifier.fillMaxSize()) {
        // Logo, nome e campos de seleção centralizados
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Coloque logo.png em res/drawable/ (ver README)
            ImagemPorNome("logo", "Logo do Petrolina Alternativo", Modifier.height(120.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                "Petrolina Alternativo",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(40.dp))

            CampoSelecao(
                rotulo = "Onde você está?",
                valor = origem,
                opcoes = dados.locais,
                habilitado = true,
                aoEscolher = aoEscolherOrigem
            )
            Spacer(Modifier.height(12.dp))
            CampoSelecao(
                rotulo = "Para onde você vai?",
                valor = destino,
                opcoes = destinos,
                habilitado = origem.isNotEmpty(),
                aoEscolher = aoEscolherDestino
            )
        }

        // Rodapé fixo, centralizado (cc_by_sa.png em res/drawable/)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            ImagemPorNome("cc_by_sa", "Creative Commons BY-SA 4.0", Modifier.width(120.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampoSelecao(
    rotulo: String,
    valor: String,
    opcoes: List<String>,
    habilitado: Boolean,
    aoEscolher: (String) -> Unit
) {
    var expandido by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expandido && habilitado,
        onExpandedChange = { if (habilitado) expandido = it }
    ) {
        OutlinedTextField(
            value = valor,
            onValueChange = {},
            readOnly = true,
            enabled = habilitado,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                disabledContainerColor = Color.White
            ),
            label = { Text(rotulo) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expandido && habilitado,
            onDismissRequest = { expandido = false }
        ) {
            opcoes.forEach { opcao ->
                DropdownMenuItem(
                    text = { Text(opcao) },
                    onClick = { aoEscolher(opcao); expandido = false }
                )
            }
        }
    }
}

// ---------- Tela de horários (abre ao escolher o destino) ----------

@Composable
fun TelaHorarios(
    dados: Dados,
    origem: String,
    destino: String,
    modifier: Modifier = Modifier,
    aoVoltar: () -> Unit
) {
    val hoje = LocalDate.now()
    val rotulo = rotuloDia(hoje, dados.feriados)
    val rotas = rotasDe(dados, origem, destino)
    val linhas = linhasHorarios(rotas, hoje, dados.feriados)
    val proximo = proximoHorario(linhas.map { it.horario }, LocalTime.now())
    val itinerarios = rotas.mapNotNull { r -> itinerarioDe(r)?.let { r to it } }
    var mostrarItinerario by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "$origem → $destino",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(rotulo, style = MaterialTheme.typography.titleMedium)
        rotas.filter { it.observacao != null }.forEach {
            val prefixo = it.empresa?.let { e -> "$e: " } ?: ""
            Text(prefixo + it.observacao, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(16.dp))

        if (linhas.isEmpty()) {
            Text("Esta linha não circula hoje ($rotulo).", textAlign = TextAlign.Center)
        } else {
            TabelaHorarios(linhas, proximo)
        }

        // Botão só aparece se alguma rota tiver itinerário cadastrado
        if (itinerarios.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = { mostrarItinerario = true }) { Text("Itinerário") }
            if (mostrarItinerario) {
                AlertDialog(
                    onDismissRequest = { mostrarItinerario = false },
                    title = { Text("Itinerário") },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            itinerarios.forEachIndexed { i, (rota, texto) ->
                                if (itinerarios.size > 1 && rota.empresa != null) {
                                    Text(rota.empresa, fontWeight = FontWeight.Bold)
                                }
                                Text(texto)
                                if (i < itinerarios.lastIndex) Spacer(Modifier.height(12.dp))
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { mostrarItinerario = false }) { Text("Fechar") }
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = aoVoltar) { Text("Voltar") }
    }
}

/** Tabela: horário | saída | empresa. O próximo horário fica em negrito. */
@Composable
private fun TabelaHorarios(linhas: List<LinhaHorario>, destaque: String?) {
    Column(Modifier.fillMaxWidth()) {
        LinhaTabela("Horário", "Saída", "Empresa", FontWeight.Bold)
        HorizontalDivider()
        linhas.forEach { l ->
            LinhaTabela(
                l.horario, l.saida, l.empresa,
                if (l.horario == destaque) FontWeight.Bold else FontWeight.Normal
            )
            HorizontalDivider(thickness = 0.5.dp)
        }
    }
}

@Composable
private fun LinhaTabela(horario: String, saida: String, empresa: String, peso: FontWeight) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(horario, Modifier.weight(1f), fontWeight = peso)
        Text(saida, Modifier.weight(2f), fontWeight = peso)
        Text(empresa, Modifier.weight(1f), fontWeight = peso)
    }
}

// ---------- Sobre ----------

@Composable
fun TelaSobre(modifier: Modifier = Modifier, aoVoltar: () -> Unit) {
    val contexto = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Sobre o aplicativo", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        ItemSobre("Versão do aplicativo", BuildConfig.VERSION_NAME)
        Column(Modifier.padding(bottom = 12.dp)) {
            Text("Desenvolvido por", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(AUTORIA, style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(URL_GITHUB))
                    runCatching { contexto.startActivity(intent) }
                }) {
                    Icon(
                        painterResource(R.drawable.ic_github),
                        contentDescription = "GitHub de $AUTORIA"
                    )
                }
            }
        }
        ItemSobre(
            "Licença",
            "Dados e identidade visual: Creative Commons BY-SA 4.0 (CC BY-SA 4.0)"
        )
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Dúvidas, dicas e alterações de horário por favor enviar por e-mail clicando aqui abaixo",
                textAlign = TextAlign.Center
            )
            Button(onClick = {
                // Abre o app de e-mail; se não houver nenhum instalado, não faz nada
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$EMAIL_CONTATO"))
                runCatching { contexto.startActivity(intent) }
            }) { Text("Entre em contato") }
            Button(onClick = aoVoltar) { Text("Voltar") }
        }
    }
}

@Composable
private fun ItemSobre(titulo: String, texto: String) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(titulo, style = MaterialTheme.typography.labelLarge)
        Text(texto, style = MaterialTheme.typography.bodyLarge)
    }
}

// ---------- Contatos ----------

@Composable
fun TelaContatos(dados: Dados, modifier: Modifier = Modifier, aoVoltar: () -> Unit) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(dados.empresas) { empresa ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Logo centralizada no topo, escalonada até as laterais do cartão
                    ImagemPorNome(
                        empresa.logo, "Logo de ${empresa.nome}",
                        Modifier.fillMaxWidth(), ContentScale.FillWidth
                    )
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val titulo = empresa.sigla?.let { "${empresa.nome} ($it)" } ?: empresa.nome
                        Text(
                            titulo,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text("Telefone: ${empresa.telefone}", textAlign = TextAlign.Center)
                        LinhaPessoa("Presidente", empresa.presidente)
                        LinhaPessoa("Vice-presidente", empresa.vicePresidente)
                        LinhaPessoa("Diretor", empresa.diretor)
                    }
                }
            }
        }
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(onClick = aoVoltar) { Text("Voltar") }
            }
        }
    }
}

@Composable
private fun LinhaPessoa(cargo: String, pessoa: Pessoa?) {
    if (pessoa != null) {
        Text("$cargo: ${pessoa.nome} — ${pessoa.telefone}", textAlign = TextAlign.Center)
    }
}

// ---------- Imagens opcionais ----------

/**
 * Mostra um drawable pelo nome (ex.: "logo_coop.png" -> R.drawable.logo_coop).
 * Se o arquivo não existir em res/drawable, não mostra nada (o app compila sem as imagens).
 */
@Composable
fun ImagemPorNome(
    nome: String?,
    descricao: String,
    modifier: Modifier = Modifier,
    escala: ContentScale = ContentScale.Fit
) {
    val contexto = LocalContext.current
    val id = remember(nome) {
        if (nome.isNullOrBlank()) 0
        else contexto.resources.getIdentifier(
            nome.substringBeforeLast('.').lowercase(), "drawable", contexto.packageName
        )
    }
    if (id != 0) Image(painterResource(id), descricao, modifier, contentScale = escala)
}
