package com.example.chatgpt

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.visualization.VisualizationContract
import com.example.visualization.VisualizationSnapshot
import com.example.visualization.VisualizationSpec
import com.example.visualization.VisualizationSession
import com.example.visualization.VisualizationSessionController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl

internal data class AccountChoice(val clientId: String, val label: String, val connected: Boolean)
internal data class ChatGptUiState(
    val ready: Boolean = false, val working: Boolean = false, val generating: Boolean = false,
    val accounts: List<AccountChoice> = emptyList(), val active: String? = null,
    val planEnabled: Boolean = false, val models: List<ChatGptModel> = emptyList(), val model: String? = null,
    val message: String? = null, val welcome: Boolean = false,
)

/** No account secret is exposed to Compose, the chart WebView, Railway, or SavedStateHandle. */
internal class ChatGptViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(ChatGptUiState())
    val state = _state.asStateFlow()
    private val network = ChatGptNetwork()
    private var storage: ChatGptStorage? = null
    private var connections = SavedConnections()
    private val mutex = Mutex()
    private var job: Job? = null
    private var generation: Job? = null
    private var operation = 0L
    private var bound: VisualizationSessionController? = null
    private var binding: Job? = null
    private var generationSession: VisualizationSession? = null
    fun bind(controller: VisualizationSessionController) {
        if (bound === controller) return
        cancelGeneration(); binding?.cancel(); bound = controller
        binding = viewModelScope.launch {
            controller.session.collect { current ->
                generationSession?.let { expected ->
                    if (current == null || current.id != expected.id || current.settingsRevision != expected.settingsRevision ||
                        current.snapshot != expected.snapshot || current.spec != expected.spec || current.refreshing || current.importing) cancelGeneration()
                }
            }
        }
    }
    init {
        if (com.example.BuildConfig.CHATGPT_ENABLED) viewModelScope.launch {
            mutex.withLock {
                try {
                    val store = withContext(Dispatchers.IO) { ChatGptStorage(application) }
                    storage = store
                    connections = try { withContext(Dispatchers.IO) { store.load() } }
                    catch (_: Exception) {
                        withContext(Dispatchers.IO) { store.clearCorruptState() }
                        _state.value = _state.value.copy(message = "The saved connection couldn’t be opened. Sign in again.")
                        SavedConnections()
                    }
                    publish()
                } catch (_: Exception) { _state.value = _state.value.copy(message = "Secure account storage is unavailable on this device.") }
            }
        }
    }
    private fun active() = connections.accounts.firstOrNull { it.registration.clientId == connections.active }
    private fun publish() {
        val selected = active()
        _state.value = _state.value.copy(ready = storage != null, active = connections.active,
            accounts = connections.accounts.map { AccountChoice(it.registration.clientId,
                "${it.registration.email ?: "ChatGPT account"} · ${it.registration.clientId.takeLast(6)}", it.credentials != null) },
            planEnabled = selected?.credentials?.planEnabled == true,
            welcome = selected?.credentials?.planEnabled == true && !selected.welcomed)
    }
    private suspend fun persist(next: SavedConnections) {
        withContext(Dispatchers.IO) { checkNotNull(storage).save(next) }
        connections = next
        publish()
    }
    private suspend fun saveAccount(account: SavedAccount, makeActive: Boolean = false) {
        val list = connections.accounts.filterNot { it.registration.clientId == account.registration.clientId } + account
        require(list.size <= 8)
        persist(connections.copy(accounts = list, active = if (makeActive) account.registration.clientId else connections.active))
    }
    fun signIn(clientId: String? = null, enablePlan: Boolean = false, openBrowser: (String) -> Unit) {
        if (!_state.value.ready || _state.value.working) return
        cancelGeneration()
        job = viewModelScope.launch {
            _state.value = _state.value.copy(working = true, message = null)
            try {
                mutex.withLock {
                    val previous = connections.accounts.firstOrNull { it.registration.clientId == clientId }
                    require(clientId == null || previous != null)
                    require(previous != null || connections.accounts.size < 8)
                    network.discovery()
                    withContext(Dispatchers.IO) { LoopbackCallback() }.use { listener ->
                        val attempt = AuthorizationAttempt(listener.redirect, checkNotNull(storage).hostId, previous?.registration, System.currentTimeMillis())
                        val url = "$ISSUER/api/accounts/authorize".toHttpUrl().newBuilder().apply {
                            attempt.parameters(enablePlan).forEach { (key, value) -> addQueryParameter(key, value) }
                        }.build().toString()
                        openBrowser(url)
                        _state.value = _state.value.copy(message = "Finish sign-in in your browser, then return here. Waiting up to 5 minutes.")
                        val (code, issuedClient) = listener.receive(attempt)
                        // Retain the issued registration if exchange fails; keep the active connection intact.
                        if (previous == null) saveAccount(SavedAccount(Registration(issuedClient)))
                        val tokens = network.exchange(code, issuedClient, attempt)
                        val identity = network.identity(tokens.requiredString("id_token"), issuedClient, attempt.nonce)
                        require(previous?.registration?.subject == null || previous.registration.subject == identity.subject)
                        val credentials = tokenCredentials(tokens, identity, null, System.currentTimeMillis())
                        currentCoroutineContext().ensureActive()
                        saveAccount(SavedAccount(identity, credentials, previous?.welcomed ?: false), makeActive = true)
                        _state.value = _state.value.copy(models = emptyList(), model = null)
                        if (credentials.planEnabled) loadModels(credentials)
                        else _state.value = _state.value.copy(message = "Signed in. ChatGPT plan usage wasn’t enabled. Choose Enable plan usage to authorize it.")
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                // Closing the loopback socket during cancellation can throw IOException.
                // Preserve the user's cancellation message instead of treating it as a sign-in failure.
                currentCoroutineContext().ensureActive()
                showFailure(failure, "Couldn’t finish ChatGPT sign-in. Your chart is unchanged. Try again.")
            }
            finally { _state.value = _state.value.copy(working = false) }
        }
    }
    fun cancelSignIn() {
        job?.cancel(); job = null
        _state.value = _state.value.copy(message = "Connection cancelled.")
    }
    fun switchAccount(clientId: String) {
        if (_state.value.working || clientId == connections.active) return
        cancelGeneration()
        job = viewModelScope.launch {
            _state.value = _state.value.copy(working = true, message = null, models = emptyList(), model = null)
            try { mutex.withLock {
                require(connections.accounts.any { it.registration.clientId == clientId })
                persist(connections.copy(active = clientId))
                active()?.credentials?.takeIf { it.planEnabled }?.let { loadModels(currentCredentials()) }
            } } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { showFailure(failure, "Couldn’t load this account. Try reconnecting.") }
            finally { _state.value = _state.value.copy(working = false) }
        }
    }
    fun refreshModels() {
        if (_state.value.working || !_state.value.planEnabled) return
        job = viewModelScope.launch {
            _state.value = _state.value.copy(working = true, message = null)
            try { mutex.withLock { loadModels(currentCredentials()) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { showFailure(failure, "Couldn’t load ChatGPT models. Try again when you’re online.") }
            finally { _state.value = _state.value.copy(working = false) }
        }
    }
    private suspend fun loadModels(credentials: Credentials) {
        val available = network.models(credentials)
        _state.value = _state.value.copy(models = available, model = available.first().id, message = null)
    }
    private suspend fun currentCredentials(): Credentials {
        val account = active() ?: throw ChatGptFailure("Choose a ChatGPT account first.")
        val current = account.credentials ?: throw ChatGptFailure("Sign in to reconnect this ChatGPT account.")
        require(current.planEnabled)
        if (current.expiresAt > System.currentTimeMillis() + 60_000) return current
        if (current.refresh == null) throw ChatGptFailure("Sign in again to renew this ChatGPT connection.")
        val body = try { network.refresh(current) } catch (failure: ChatGptFailure) {
            if (failure.code in setOf("invalid_grant", "invalid_refresh_token", "token_expired", "refresh_token_expired", "refresh_token_invalidated", "refresh_token_reused")) {
                saveAccount(account.copy(credentials = null))
                _state.value = _state.value.copy(models = emptyList(), model = null)
                throw ChatGptFailure("Your ChatGPT connection expired. Sign in again.")
            }
            throw failure
        }
        val identity = body.optionalString("id_token")?.let { network.identity(it, current.registration.clientId, null) } ?: current.registration
        require(identity.subject == current.registration.subject)
        val renewed = tokenCredentials(body, identity, current, System.currentTimeMillis())
        currentCoroutineContext().ensureActive()
        // Preserve a successfully rotated refresh token even if the user cancelled the chart request.
        withContext(kotlinx.coroutines.NonCancellable) { saveAccount(account.copy(registration = identity, credentials = renewed)) }
        if (!renewed.planEnabled) throw ChatGptFailure("ChatGPT plan permission is no longer enabled. Reconnect to enable it.")
        return renewed
    }
    fun chooseModel(id: String) {
        if (!_state.value.working && !_state.value.generating && _state.value.models.any { it.id == id }) _state.value = _state.value.copy(model = id)
    }
    fun dismissWelcome() {
        _state.value = _state.value.copy(welcome = false)
        viewModelScope.launch {
            try { mutex.withLock { active()?.let { saveAccount(it.copy(welcomed = true)) } } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _state.value = _state.value.copy(message = "Couldn’t save this preference. You may see the welcome again.") }
        }
    }
    fun generate(prompt: String, snapshot: VisualizationSnapshot, spec: VisualizationSpec, apply: (VisualizationSpec) -> Boolean) {
        if (_state.value.working || _state.value.generating || !_state.value.planEnabled || prompt.isBlank() || prompt.length > 1000) return
        val model = _state.value.model ?: return
        val expected = bound?.session?.value ?: return
        if (expected.snapshot != snapshot || expected.spec != spec || expected.refreshing || expected.importing) return
        generationSession = expected
        val sequence = ++operation
        generation = viewModelScope.launch {
            _state.value = _state.value.copy(generating = true, message = null)
            try {
                val text = mutex.withLock { network.generate(currentCredentials(), model, prompt, snapshot, spec) }
                val generated = withContext(Dispatchers.Default) { VisualizationContract.decodeSpec(text, snapshot) }
                currentCoroutineContext().ensureActive()
                if (sequence == operation) {
                    val accepted = apply(generated)
                    _state.value = _state.value.copy(message = if (accepted) "Chart updated using your ChatGPT plan." else "Your game or filters changed. Generate again for the current chart.")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (sequence == operation) showFailure(failure, "ChatGPT couldn’t create supported chart settings. Your previous chart is unchanged.") }
            finally { if (sequence == operation) { generationSession = null; _state.value = _state.value.copy(generating = false) } }
        }
    }
    fun cancelGeneration() {
        operation++; generation?.cancel(); generation = null; generationSession = null
        _state.value = _state.value.copy(generating = false)
    }
    fun signOut() {
        if (_state.value.working) return
        cancelGeneration()
        job = viewModelScope.launch {
            _state.value = _state.value.copy(working = true, message = null)
            try { mutex.withLock {
                val account = active() ?: return@withLock
                val confirmed = try { account.credentials?.let { network.revoke(it) } ?: true }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { false }
                saveAccount(account.copy(credentials = null))
                _state.value = _state.value.copy(models = emptyList(), model = null, message = if (confirmed) "Signed out of Miniscore’s ChatGPT connection." else "Signed out on this device. Remote disconnection wasn’t confirmed; disconnect Miniscore in ChatGPT settings.")
            } } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _state.value = _state.value.copy(message = "Couldn’t clear secure storage. Try signing out again.") }
            finally { _state.value = _state.value.copy(working = false) }
        }
    }
    private fun showFailure(failure: Exception, fallback: String) {
        // Do not copy provider diagnostics, callback URLs, tokens or exception messages into UI/logs.
        _state.value = _state.value.copy(message = (failure as? ChatGptFailure)?.userMessage ?: fallback)
    }
}
