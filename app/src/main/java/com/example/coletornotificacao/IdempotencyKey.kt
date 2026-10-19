package com.example.coletornotificacao

import java.security.MessageDigest

/**
 * Geração de um identificador estável para cada notificação capturada.
 *
 * O identificador é um hash SHA-256 derivado de:
 *   packageName + title + message + createdAt (horário ORIGINAL da captura)
 *
 * Propriedades:
 *  - Estável: a mesma notificação sempre gera o mesmo id, mesmo após
 *    reinício do app ou do dispositivo. Isso permite reenvio sem mudar a
 *    chave e serve de base para idempotência no cliente.
 *  - Não usa o horário atual: usa o `createdAt` original da notificação,
 *    exatamente para evitar que reenvios gerem chaves diferentes.
 *
 * Limitação: por si só este id NÃO garante ausência de duplicidade no
 * servidor. O backend atual (ver documentação) não aplica idempotência;
 * cabe a ele passar a desduplicar por esta chave. Ver docs/api.md.
 */
object IdempotencyKey {

    fun generate(
        packageName: String,
        title: String,
        message: String,
        createdAt: Long
    ): String {
        val raw = "$packageName|$title|$message|$createdAt"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
