package com.marceloferlan.stagemobile.data

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await

/**
 * Repositório de autenticação — interface única com o Firebase Auth.
 * Não contém lógica de UI; apenas operações de auth com Result<FirebaseUser>.
 */
class AuthRepository {

    private val auth: FirebaseAuth = Firebase.auth

    /** Usuário atualmente autenticado. Null se não logado ou após "Limpar Dados". */
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    /**
     * Autentica com e-mail e senha existentes.
     * @return Result.success com FirebaseUser ou Result.failure com mensagem de erro amigável.
     */
    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: return Result.failure(Exception("Usuário não encontrado."))
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(mapAuthException(e))
        }
    }

    /**
     * Cria uma conta nova com e-mail e senha.
     * Além de criar a conta, insere o Primeiro Nome no perfil global do Firebase e dispara a verificação de e-mail limitando o acesso.
     */
    suspend fun createAccountWithEmail(firstName: String, email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: return Result.failure(Exception("Falha ao criar conta."))
            
            // Set First Name in Profile
            val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(firstName.trim())
                .build()
            user.updateProfile(profileUpdates).await()
            
            // Trigger Email Verification natively
            user.sendEmailVerification().await()
            
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(mapAuthException(e))
        }
    }

    /**
     * Autentica via Google usando o idToken recebido do Credential Manager.
     */
    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user ?: return Result.failure(Exception("Falha na autenticação com Google."))
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(mapAuthException(e))
        }
    }

    /**
     * Encerra a sessão do usuário.
     * O Firebase apaga o token local — na próxima abertura o app mostrará o LoginScreen.
     */
    fun signOut() {
        auth.signOut()
    }

    /**
     * Traduz exceções do Firebase Auth em mensagens amigáveis em português.
     */
    private fun mapAuthException(e: Exception): Exception {
        if (e is FirebaseAuthUserCollisionException) {
            return Exception("Este e-mail já está cadastrado. Que tal apenas Entrar?")
        }
        if (e is FirebaseAuthInvalidCredentialsException) {
             return Exception("Credenciais inválidas. Verifique seu e-mail e senha.")
        }
        
        val message = e.message?.lowercase() ?: ""
        return when {
            message.contains("invalid_login_credentials") ||
            message.contains("invalid-credential") -> Exception("E-mail ou senha incorretos.")
            message.contains("email_exists") ||
            message.contains("email-already-in-use") ||
            message.contains("already in use") -> Exception("Este e-mail já está cadastrado.")
            message.contains("weak_password") ||
            message.contains("weak-password") -> Exception("Senha muito fraca. Use pelo menos 6 caracteres.")
            message.contains("invalid_email") ||
            message.contains("invalid-email") -> Exception("Formato de e-mail inválido.")
            message.contains("user_not_found") ||
            message.contains("user-not-found") -> Exception("Nenhuma conta encontrada com este e-mail.")
            message.contains("network_error") ||
            message.contains("network-request-failed") -> Exception("Sem conexão com a internet.")
            message.contains("too_many_requests") -> Exception("Muitas tentativas. Aguarde alguns minutos.")
            else -> Exception("Erro de autenticação. Tente novamente.")
        }
    }
}
