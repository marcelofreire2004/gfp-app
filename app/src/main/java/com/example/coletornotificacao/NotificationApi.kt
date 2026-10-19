package com.example.coletornotificacao
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface NotificationApi {

    @POST("notificacoes")
    suspend fun enviarNotificacao(

        @Body notificacao: NotificationRequest

    ): Response<Unit>
}