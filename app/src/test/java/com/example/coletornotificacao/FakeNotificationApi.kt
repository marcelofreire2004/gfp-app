package com.example.coletornotificacao

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

/**
 * API simulada para testes do [NotificationSyncer]. Permite programar a
 * resposta (sucesso, erro HTTP ou exceção de rede) e registra as requisições
 * recebidas para verificação (ex.: preservação de createdAt e stableId).
 */
class FakeNotificationApi : NotificationApi {

    sealed class Behavior {
        data class Success(val code: Int = 201) : Behavior()
        data class HttpError(val code: Int = 500) : Behavior()
        data class NetworkFailure(val error: Exception) : Behavior()
    }

    var behavior: Behavior = Behavior.Success()
    val requests = mutableListOf<NotificationRequest>()

    override suspend fun enviarNotificacao(notificacao: NotificationRequest): Response<Unit> {
        requests.add(notificacao)
        return when (val b = behavior) {
            is Behavior.Success -> Response.success(b.code, Unit)
            is Behavior.HttpError ->
                Response.error(b.code, "".toResponseBody("text/plain".toMediaType()))
            is Behavior.NetworkFailure -> throw b.error
        }
    }
}
