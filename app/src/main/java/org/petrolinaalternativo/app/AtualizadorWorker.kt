package org.petrolinaalternativo.app

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Baixa horarios.json, feriados.json, empresas.json, tarifas.json do GitHub (repositório público, sem autenticação),
 * valida e grava em filesDir. Silencioso: sem notificação. Se algo falhar, mantém o anterior.
 */
class AtualizadorWorker(contexto: Context, parametros: WorkerParameters) :
    Worker(contexto, parametros) {

    override fun doWork(): Result {
        val tentarHorarios = atualizar(URL_HORARIOS, ARQUIVO_HORARIOS) { parseDados(it) }
        val tentarFeriados = atualizar(URL_FERIADOS, ARQUIVO_FERIADOS) { parseFeriados(it) }
        val tentarEmpresas = atualizar(URL_EMPRESAS, ARQUIVO_EMPRESAS) { parseEmpresas(it) }
        val tentarTarifas = atualizar(URL_TARIFAS, ARQUIVO_TARIFAS) { parseTarifas(it) }

        // Itinerários em HTML citados nas rotas (updates/itinerarios/<arquivo>.html)
        val tentarItinerarios = runCatching {
            carregarDados(applicationContext).rotas
                .mapNotNull { it.itinerarioHtml }
                .distinct()
                .map { nome ->
                    atualizar("$URL_BASE_ITINERARIOS$nome", "$PASTA_ITINERARIOS/$nome") {
                        require(it.isNotBlank()) { "HTML vazio" }
                    }
                }
                .any { it }
        }.getOrDefault(false)

        return if (tentarHorarios || tentarFeriados || tentarEmpresas || tentarTarifas || tentarItinerarios)
            Result.retry()
        else Result.success()
    }

    /** Retorna true se vale a pena tentar de novo (falha de rede ou erro 5xx). */
    private fun atualizar(url: String, arquivo: String, validar: (String) -> Unit): Boolean {
        var conexao: HttpURLConnection? = null
        return try {
            conexao = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                requestMethod = "GET"
            }
            val codigo = conexao.responseCode
            if (codigo != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "$url -> HTTP $codigo (esperado 200)")
                return codigo in 500..599
            }
            val texto = conexao.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (texto.length > TAMANHO_MAXIMO) return false

            validar(texto) // JSON parseável + campos obrigatórios; lança exceção se inválido

            // Grava em temporário e troca, para nunca deixar arquivo pela metade
            val destino = File(applicationContext.filesDir, arquivo)
            destino.parentFile?.mkdirs()
            val temporario = File(applicationContext.filesDir, "$arquivo.tmp")
            temporario.writeText(texto, Charsets.UTF_8)
            if (!temporario.renameTo(destino)) temporario.delete()
            Log.i(TAG, "$arquivo atualizado com sucesso")
            false
        } catch (e: IOException) {
            Log.w(TAG, "Falha de rede em $url: ${e.message}")
            true    // sem rede / falha de conexão
        } catch (e: Exception) {
            Log.w(TAG, "JSON inválido em $url (mantido o anterior): ${e.message}")
            false   // JSON inválido: mantém o anterior
        } finally {
            conexao?.disconnect()
        }
    }

    companion object {
        /** JSONs oficiais no GitHub (URL raw, repositório público, sem token). */
        const val URL_HORARIOS =
            "https://raw.githubusercontent.com/cleberson1/petrolina-alternativo/main/updates/horarios.json"
        const val URL_FERIADOS =
            "https://raw.githubusercontent.com/cleberson1/petrolina-alternativo/main/updates/feriados.json"
        const val URL_EMPRESAS =
            "https://raw.githubusercontent.com/cleberson1/petrolina-alternativo/main/updates/empresas.json"
        const val URL_TARIFAS =
            "https://raw.githubusercontent.com/cleberson1/petrolina-alternativo/main/updates/tarifas.json"
        const val URL_BASE_ITINERARIOS =
            "https://raw.githubusercontent.com/cleberson1/petrolina-alternativo/main/updates/itinerarios/"
        const val ARQUIVO_HORARIOS = "horarios_atualizado.json"
        const val ARQUIVO_FERIADOS = "feriados_atualizado.json"
        const val ARQUIVO_EMPRESAS = "empresas_atualizado.json"
        const val ARQUIVO_TARIFAS = "tarifas_atualizado.json"
        private const val TAMANHO_MAXIMO = 2_000_000
        private const val NOME_TRABALHO = "atualizar_horarios"
        private const val TAG = "AtualizadorWorker"
        const val NOME_ATUALIZAR_AGORA = "atualizar_agora"

        /** Id do pedido de atualização feito ao abrir o app (a tela observa só este). */
        @Volatile
        var pedidoAtualId: UUID? = null

        /** Baixa as atualizações já (uma vez, quando houver rede). Chamado ao abrir o app. */
        fun atualizarAgora(contexto: Context) {
            runCatching {
                val pedido = OneTimeWorkRequestBuilder<AtualizadorWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
                pedidoAtualId = pedido.id
                WorkManager.getInstance(contexto).enqueueUniqueWork(
                    // REPLACE: cada abertura faz uma tentativa nova (sem esperar o backoff de uma falha antiga)
                    NOME_ATUALIZAR_AGORA, ExistingWorkPolicy.REPLACE, pedido
                )
            }
        }

        /** Agenda a verificação periódica (1x por dia, só com rede). */
        fun agendar(contexto: Context) {
            runCatching {
                val pedido = PeriodicWorkRequestBuilder<AtualizadorWorker>(1, TimeUnit.DAYS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
                WorkManager.getInstance(contexto).enqueueUniquePeriodicWork(
                    NOME_TRABALHO, ExistingPeriodicWorkPolicy.KEEP, pedido
                )
            }
        }
    }
}
