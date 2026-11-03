package org.petrolinaalternativo.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

// ---------- Modelos ----------

/**
 * Feriado recorrente (vale todo ano). Ou tem [mesDia] fixo ("MM-dd"), ou [diasAposPascoa]
 * (feriado móvel, calculado a partir da Páscoa do ano). [parcial] = vale só para parte da
 * população (ex.: rede de ensino); feriados parciais NÃO mudam a tabela de horários.
 */
data class Feriado(
    val nome: String,
    val mesDia: String?,
    val diasAposPascoa: Int?,
    val parcial: Boolean
)
data class Pessoa(val nome: String, val telefone: String)

data class Empresa(
    val nome: String,
    val sigla: String?,
    val logo: String?,
    val telefone: String,
    val presidente: Pessoa?,
    val vicePresidente: Pessoa?,
    val diretor: Pessoa?,
    val secretario: Pessoa?
)

data class Horarios(
    val uteis: List<String>,
    val sabado: List<String>,
    val domingo: List<String>,
    val feriado: List<String>
)

data class Rota(
    val origem: String,
    val destino: String,
    val empresa: String?,   // sigla da empresa
    val saida: String?,     // ponto de saída (se ausente, usa a origem)
    val observacao: String?,
    val itinerario: String?,
    val itinerarioHtml: String?,   // nome do arquivo .html em updates/itinerarios/
    val horarios: Horarios
)

data class Dados(
    val atualizadoEm: String,
    val locais: List<String>,
    val feriados: List<Feriado>,
    val empresas: List<Empresa>,
    val rotas: List<Rota>,
    val tarifas: Tarifas? = null
)

/** Portaria ou decreto que fixa as tarifas, com link opcional para o texto da lei. */
data class Legislacao(val tipo: String, val numero: String?, val descricao: String?, val url: String?)

/** Planilha de tarifas: colunas e linhas livres (definidas no tarifas.json). */
data class Tarifas(
    val atualizadoEm: String,
    val legislacao: Legislacao?,
    val colunas: List<String>,
    val linhas: List<List<String>>
)

enum class TipoDia { UTIL, SABADO, DOMINGO, FERIADO }

// ---------- Carregamento ----------

/** Lê o arquivo baixado (filesDir); se não existir ou for inválido, usa o embutido (assets). */
private fun <T> lerComFallback(
    contexto: Context, arquivoAtualizado: String, asset: String, parse: (String) -> T
): T {
    val atualizado = File(contexto.filesDir, arquivoAtualizado)
    if (atualizado.exists()) {
        runCatching { return parse(atualizado.readText(Charsets.UTF_8)) }
    }
    val texto = contexto.assets.open(asset).bufferedReader(Charsets.UTF_8).use { it.readText() }
    return parse(texto)
}

/** Carrega horários, feriados e empresas (arquivos separados), cada um com seu fallback. */
fun carregarDados(contexto: Context): Dados {
    val dados = lerComFallback(contexto, AtualizadorWorker.ARQUIVO_HORARIOS, "horarios.json") {
        parseDados(it)
    }
    val feriados = runCatching {
        lerComFallback(contexto, AtualizadorWorker.ARQUIVO_FERIADOS, "feriados.json") {
            parseFeriados(it)
        }
    }.getOrDefault(emptyList())
    val empresas = runCatching {
        lerComFallback(contexto, AtualizadorWorker.ARQUIVO_EMPRESAS, "empresas.json") {
            parseEmpresas(it)
        }
    }.getOrDefault(emptyList())
    val tarifas = runCatching {
        lerComFallback(contexto, AtualizadorWorker.ARQUIVO_TARIFAS, "tarifas.json") {
            parseTarifas(it)
        }
    }.getOrNull()
    return dados.copy(feriados = feriados, empresas = empresas, tarifas = tarifas)
}

// ---------- Parse (org.json nativo) ----------

private fun JSONObject.textoOuNulo(chave: String): String? =
    if (isNull(chave)) null else optString(chave).trim().ifEmpty { null }

private fun JSONArray?.paraListaDeTexto(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { getString(it) }

/** Cargo ausente ou com nome em branco = null (não aparece no app). */
private fun JSONObject.pessoa(chave: String): Pessoa? =
    optJSONObject(chave)?.let {
        val nome = it.optString("nome").trim()
        if (nome.isEmpty()) null else Pessoa(nome, it.optString("telefone").trim())
    }

/** Lança exceção se o JSON for inválido ou faltar campo obrigatório. */
fun parseDados(texto: String): Dados {
    val raiz = JSONObject(texto.removePrefix("\uFEFF").trim())
    val atualizadoEm = raiz.getString("atualizado_em")
    val locais = raiz.getJSONArray("locais").paraListaDeTexto()

    val rotasJson = raiz.getJSONArray("rotas")
    val rotas = (0 until rotasJson.length()).map {
        val o = rotasJson.getJSONObject(it)
        val h = o.getJSONObject("horarios")
        Rota(
            origem = o.getString("origem"),
            destino = o.getString("destino"),
            empresa = o.textoOuNulo("empresa"),
            saida = o.textoOuNulo("saida"),
            observacao = o.textoOuNulo("observacao"),
            itinerario = o.textoOuNulo("itinerario"),
            // Só aceita nome simples de arquivo .html (sem pastas), por segurança
            itinerarioHtml = o.textoOuNulo("itinerario_html")
                ?.takeIf { Regex("[A-Za-z0-9_-]+\\.html").matches(it) },
            horarios = Horarios(
                uteis = h.optJSONArray("uteis").paraListaDeTexto(),
                sabado = h.optJSONArray("sabado").paraListaDeTexto(),
                domingo = h.optJSONArray("domingo").paraListaDeTexto(),
                feriado = h.optJSONArray("feriado").paraListaDeTexto()
            )
        )
    }
    require(locais.isNotEmpty() && rotas.isNotEmpty()) { "JSON sem locais ou rotas" }
    return Dados(atualizadoEm, locais, emptyList(), emptyList(), rotas)
}

// ---------- Regras de negócio ----------

/** Empresas (arquivo empresas.json): lê "empresas". */
fun parseEmpresas(texto: String): List<Empresa> {
    val arr = JSONObject(texto.removePrefix("\uFEFF").trim()).getJSONArray("empresas")
    return (0 until arr.length()).map {
        val o = arr.getJSONObject(it)
        Empresa(
            nome = o.getString("nome"),
            sigla = o.textoOuNulo("sigla"),
            logo = o.textoOuNulo("logo"),
            telefone = o.optString("telefone"),
            presidente = o.pessoa("presidente"),
            vicePresidente = o.pessoa("vice_presidente"),
            diretor = o.pessoa("diretor"),
            secretario = o.pessoa("secretario")
        )
    }
}

/** Tarifas (arquivo tarifas.json): "colunas", "linhas" e, opcional, "legislacao". */
fun parseTarifas(texto: String): Tarifas {
    val raiz = JSONObject(texto.removePrefix("\uFEFF").trim())
    val colunas = raiz.getJSONArray("colunas").paraListaDeTexto()
    require(colunas.isNotEmpty()) { "tarifas.json sem colunas" }
    val linhasJson = raiz.getJSONArray("linhas")
    val linhas = (0 until linhasJson.length()).map { i ->
        val linha = linhasJson.getJSONArray(i)
        // Ajusta cada linha ao número de colunas (completa com vazio ou corta o excesso)
        List(colunas.size) { c -> linha.optString(c, "") }
    }
    val legislacao = raiz.optJSONObject("legislacao")?.let { o ->
        Legislacao(
            tipo = o.getString("tipo"),
            numero = o.textoOuNulo("numero"),
            descricao = o.textoOuNulo("descricao"),
            // Só aceita link http/https
            url = o.textoOuNulo("url")?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        )
    }
    return Tarifas(raiz.optString("atualizado_em"), legislacao, colunas, linhas)
}

/** Feriados (arquivo feriados.json): lê "feriados" e valida cada entrada. */
fun parseFeriados(texto: String): List<Feriado> {
    val arr = JSONObject(texto.removePrefix("\uFEFF").trim()).getJSONArray("feriados")
    return (0 until arr.length()).map {
        val o = arr.getJSONObject(it)
        val mesDia = o.textoOuNulo("dia_mes")
        val dias = if (o.has("dias_apos_pascoa") && !o.isNull("dias_apos_pascoa"))
            o.getInt("dias_apos_pascoa") else null
        require(mesDia != null || dias != null) { "Feriado sem dia_mes nem dias_apos_pascoa" }
        if (mesDia != null) require(Regex("\\d{2}-\\d{2}").matches(mesDia)) { "dia_mes inválido: $mesDia" }
        Feriado(o.getString("nome"), mesDia, dias, o.optBoolean("parcial", false))
    }
}

/** Data da Páscoa (calendário gregoriano, algoritmo de Meeus/Jones/Butcher). */
fun dataDaPascoa(ano: Int): LocalDate {
    val a = ano % 19
    val b = ano / 100
    val c = ano % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val mes = (h + l - 7 * m + 114) / 31
    val dia = (h + l - 7 * m + 114) % 31 + 1
    return LocalDate.of(ano, mes, dia)
}

/** Feriado (não parcial) que cai nessa data, ou null. Vale para qualquer ano. */
fun feriadoDe(data: LocalDate, feriados: List<Feriado>): Feriado? {
    val mesDia = data.monthValue.toString().padStart(2, '0') + "-" +
        data.dayOfMonth.toString().padStart(2, '0')
    val pascoa = dataDaPascoa(data.year)
    return feriados.firstOrNull { f ->
        !f.parcial && (
            f.mesDia == mesDia ||
            (f.diasAposPascoa != null && data == pascoa.plusDays(f.diasAposPascoa.toLong()))
        )
    }
}

/** Ordem: sábado > domingo > feriado (em dia útil) > dia útil. */
fun tipoDoDia(data: LocalDate, feriados: List<Feriado>): TipoDia = when {
    data.dayOfWeek == DayOfWeek.SATURDAY -> TipoDia.SABADO
    data.dayOfWeek == DayOfWeek.SUNDAY -> TipoDia.DOMINGO
    feriadoDe(data, feriados) != null -> TipoDia.FERIADO
    else -> TipoDia.UTIL
}

fun rotuloDia(data: LocalDate, feriados: List<Feriado>): String =
    when (tipoDoDia(data, feriados)) {
        TipoDia.SABADO -> "Sábado"
        TipoDia.DOMINGO -> "Domingo"
        TipoDia.FERIADO -> "Feriado: ${feriadoDe(data, feriados)?.nome}"
        TipoDia.UTIL -> "Dia útil"
    }

fun horariosPara(rota: Rota, data: LocalDate, feriados: List<Feriado>): List<String> =
    when (tipoDoDia(data, feriados)) {
        TipoDia.SABADO -> rota.horarios.sabado
        TipoDia.DOMINGO -> rota.horarios.domingo
        TipoDia.FERIADO -> rota.horarios.feriado
        TipoDia.UTIL -> rota.horarios.uteis
    }

/** Primeiro horário >= agora; null se o último já passou. */
fun proximoHorario(horarios: List<String>, agora: LocalTime): String? =
    horarios.firstOrNull { h ->
        runCatching { !LocalTime.parse(h).isBefore(agora) }.getOrDefault(false)
    }

/** Texto do itinerário, ou null se a rota não tiver (o botão não aparece). */
fun itinerarioDe(rota: Rota): String? = rota.itinerario?.takeIf { it.isNotBlank() }

/** Uma linha da tabela de horários: horário | saída | empresa. */
data class LinhaHorario(val horario: String, val saida: String, val empresa: String)

/** Todas as rotas (de empresas diferentes) entre uma origem e um destino. */
fun rotasDe(dados: Dados, origem: String, destino: String): List<Rota> =
    dados.rotas.filter { it.origem == origem && it.destino == destino }

/** Junta os horários de hoje de todas as rotas, ordenados por horário. */
fun linhasHorarios(rotas: List<Rota>, data: LocalDate, feriados: List<Feriado>): List<LinhaHorario> =
    rotas.flatMap { r ->
        horariosPara(r, data, feriados).map { LinhaHorario(it, r.saida ?: r.origem, r.empresa ?: "—") }
    }.sortedBy { runCatching { LocalTime.parse(it.horario) }.getOrDefault(LocalTime.MAX) }

const val PASTA_ITINERARIOS = "itinerarios"

/**
 * HTML do itinerário da rota: usa o arquivo baixado (filesDir/itinerarios); se não houver,
 * o embutido (assets/itinerarios). Retorna null se a rota não tem HTML ou o arquivo não existe.
 */
fun htmlDoItinerario(contexto: Context, rota: Rota): String? {
    val nome = rota.itinerarioHtml ?: return null
    val baixado = runCatching {
        File(File(contexto.filesDir, PASTA_ITINERARIOS), nome)
            .takeIf { it.exists() }?.readText(Charsets.UTF_8)
    }.getOrNull()
    if (!baixado.isNullOrBlank()) return baixado
    return runCatching {
        contexto.assets.open("$PASTA_ITINERARIOS/$nome").bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
