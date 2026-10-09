package cc.jaxy.anlobehub.app

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import cc.jaxy.anlobehub.core.common.model.PICKED_MODEL_KEY
import cc.jaxy.anlobehub.core.common.model.formatPickedModel
import cc.jaxy.anlobehub.core.common.model.parsePickedModel
import cc.jaxy.anlobehub.core.data.chat.AIModel
import cc.jaxy.anlobehub.core.designsystem.component.LoadingBox
import cc.jaxy.anlobehub.feature.agents.AgentManageSheet
import cc.jaxy.anlobehub.feature.agents.AgentManageViewModel
import cc.jaxy.anlobehub.feature.agents.AgentsScreen
import cc.jaxy.anlobehub.feature.agents.AgentsViewModel
import cc.jaxy.anlobehub.feature.auth.ServerScreen
import cc.jaxy.anlobehub.feature.chat.ChatScreen
import cc.jaxy.anlobehub.feature.chat.ChatViewModel
import cc.jaxy.anlobehub.feature.models.ModelsScreen
import cc.jaxy.anlobehub.feature.settings.KnowledgeBasesScreen
import cc.jaxy.anlobehub.feature.settings.ProviderDetailScreen
import cc.jaxy.anlobehub.feature.settings.ProviderDetailViewModel
import cc.jaxy.anlobehub.feature.settings.ProviderModelsScreen
import cc.jaxy.anlobehub.feature.settings.ProvidersScreen
import cc.jaxy.anlobehub.feature.settings.SettingsScreen
import cc.jaxy.anlobehub.feature.settings.SettingsViewModel
import kotlinx.serialization.Serializable

@Serializable
data object ServerRoute

@Serializable
data class LoginRoute(val baseUrl: String)

@Serializable
data object AgentsRoute

@Serializable
data class ChatRoute(
    val agentId: String,
    val agentTitle: String? = null,
    val topicId: String? = null,
)

@Serializable
data object SettingsRoute

@Serializable
data object KnowledgeBasesRoute

@Serializable
data object ProvidersRoute

@Serializable
data class ProviderModelsRoute(val providerId: String, val providerName: String? = null)

@Serializable
data class ProviderDetailRoute(val providerId: String, val providerName: String? = null)


@Serializable
data class ModelPickerRoute(
    val selectedModelId: String? = null,
    val selectedProviderId: String? = null,
    val filterProviderId: String? = null,
)

@Composable
fun AnlobehubNavHost() {
    val navController = rememberNavController()
    // Themed backdrop: prevents white flash behind slide/fade transitions.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
    // Startup gate: runs once, in parallel with the first screen. Resolves
    // the start destination from local state (no network on the path):
    // no server -> Server, otherwise Agents (auth refreshes in background
    // and redirects to Login only when the session is actually invalid).
    val startupViewModel: StartupViewModel = hiltViewModel()
    val startDestination by startupViewModel.startDestination.collectAsStateWithLifecycle()
    val authRedirect by startupViewModel.authRedirect.collectAsStateWithLifecycle()
    // Consume one-shot auth redirects (session expired mid-flight).
    LaunchedEffect(authRedirect) {
        val target = authRedirect ?: return@LaunchedEffect
        startupViewModel.consumeRedirect()
        when (target) {
            is StartupViewModel.Redirect.Server ->
                navController.navigate(ServerRoute) {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                }
            is StartupViewModel.Redirect.Login ->
                navController.navigate(LoginRoute(target.baseUrl)) {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                }
        }
    }
    val resolvedStart = startDestination
    if (resolvedStart == null) {
        // Local DataStore read only (~ms); Splash still covers this frame.
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingBox()
        }
        return
    }
    NavHost(
        navController = navController,
        startDestination = resolvedStart,
        enterTransition = { slideInHorizontally(initialOffsetX = { it / 4 }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)) },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)) },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)) },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it / 4 }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)) },
    ) {
        composable<ServerRoute> {
            ServerScreen(onServerConfirmed = { baseUrl ->
                navController.navigate(LoginRoute(baseUrl))
            })
        }
        composable<LoginRoute> { entry ->
            val route = entry.toRoute<LoginRoute>()
            cc.jaxy.anlobehub.feature.auth.LoginScreen(
                baseUrl = route.baseUrl,
                onLoggedIn = {
                    navController.navigate(AgentsRoute) {
                        popUpTo(ServerRoute) { inclusive = true }
                    }
                },
            )
        }
        composable<AgentsRoute> { entry ->
            val agentsViewModel: AgentsViewModel = hiltViewModel(entry)
            val manageViewModel: AgentManageViewModel = hiltViewModel(entry)
            var managingAgentId by rememberSaveable { mutableStateOf<String?>(null) }
            var pickingForManage by rememberSaveable { mutableStateOf(false) }
            val pickedRaw by entry.savedStateHandle.getStateFlow<String?>(PICKED_MODEL_KEY, null)
                .collectAsStateWithLifecycle()
            LaunchedEffect(pickedRaw) {
                parsePickedModel(pickedRaw)?.let { picked ->
                    val model = AIModel(
                        id = picked.modelId,
                        displayName = picked.displayName,
                        providerId = picked.providerId,
                    )
                    if (pickingForManage) {
                        manageViewModel.setModel(model)
                    } else {
                        agentsViewModel.setCreateModel(model)
                    }
                    pickingForManage = false
                    entry.savedStateHandle[PICKED_MODEL_KEY] = null
                }
            }
            AgentsScreen(
                onAgentClick = { agentId, agentTitle ->
                    navController.navigate(ChatRoute(agentId = agentId, agentTitle = agentTitle))
                },
                onSettingsClick = { navController.navigate(SettingsRoute) },
                onManageClick = { agentId -> managingAgentId = agentId },
                onOpenModelPicker = { navController.navigate(ModelPickerRoute()) },
            )
            managingAgentId?.let { agentId ->
                AgentManageSheet(
                    agentId = agentId,
                    onDismiss = { managingAgentId = null },
                    onDeleted = { managingAgentId = null },
                    onOpenModelPicker = { current ->
                        pickingForManage = true
                        navController.navigate(
                            ModelPickerRoute(
                                selectedModelId = current?.id,
                                selectedProviderId = current?.providerId,
                            ),
                        )
                    },
                    viewModel = manageViewModel,
                )
            }
        }
        composable<ChatRoute> { entry ->
            val chatViewModel: ChatViewModel = hiltViewModel(entry)
            val pickedRaw by entry.savedStateHandle.getStateFlow<String?>(PICKED_MODEL_KEY, null)
                .collectAsStateWithLifecycle()
            LaunchedEffect(pickedRaw) {
                parsePickedModel(pickedRaw)?.let { picked ->
                    val match = chatViewModel.uiState.value.models.firstOrNull {
                        it.id == picked.modelId && (picked.providerId == null || it.providerId == picked.providerId)
                    } ?: AIModel(
                        id = picked.modelId,
                        displayName = picked.displayName,
                        providerId = picked.providerId,
                    )
                    chatViewModel.selectModel(match)
                    entry.savedStateHandle[PICKED_MODEL_KEY] = null
                }
            }
            ChatScreen(
                onOpenModelPicker = { current ->
                    navController.navigate(
                        ModelPickerRoute(
                            selectedModelId = current?.id,
                            selectedProviderId = current?.providerId,
                        ),
                    )
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<SettingsRoute> { entry ->
            val settingsViewModel: SettingsViewModel = hiltViewModel(entry)
            LaunchedEffect(settingsViewModel) {
                settingsViewModel.signedOutEvent.collect {
                    val baseUrl = settingsViewModel.uiState.value.baseUrl
                    if (baseUrl.isNullOrBlank()) {
                        navController.navigate(ServerRoute) {
                            popUpTo(SettingsRoute) { inclusive = true }
                        }
                    } else {
                        navController.navigate(LoginRoute(baseUrl)) {
                            popUpTo(SettingsRoute) { inclusive = true }
                        }
                    }
                }
            }
            LaunchedEffect(settingsViewModel) {
                settingsViewModel.switchServerEvent.collect {
                    navController.navigate(ServerRoute) {
                        popUpTo(SettingsRoute) { inclusive = true }
                    }
                }
            }
            SettingsScreen(
                onKnowledgeBasesClick = { navController.navigate(KnowledgeBasesRoute) },
                onProvidersClick = { navController.navigate(ProvidersRoute) },
            )
        }
        composable<KnowledgeBasesRoute> {
            KnowledgeBasesScreen(onBack = { navController.popBackStack() })
        }
        composable<ProvidersRoute> {
            ProvidersScreen(
                onBack = { navController.popBackStack() },
                onProviderClick = { id, name ->
                    navController.navigate(ProviderModelsRoute(providerId = id, providerName = name))
                },
                onConfigClick = { id, name ->
                    navController.navigate(ProviderDetailRoute(providerId = id, providerName = name))
                },
            )
        }
        composable<ProviderDetailRoute> { entry ->
            val route = entry.toRoute<ProviderDetailRoute>()
            val detailViewModel: ProviderDetailViewModel = hiltViewModel(entry)
            val pickedRaw by entry.savedStateHandle.getStateFlow<String?>(PICKED_MODEL_KEY, null)
                .collectAsStateWithLifecycle()
            LaunchedEffect(pickedRaw) {
                // Only consume picks aimed at this screen (provider-scoped).
                parsePickedModel(pickedRaw)?.let { picked ->
                    if (picked.providerId == route.providerId) {
                        detailViewModel.setCheckModel(picked.modelId)
                        entry.savedStateHandle[PICKED_MODEL_KEY] = null
                    }
                }
            }
            ProviderDetailScreen(
                providerId = route.providerId,
                providerName = route.providerName,
                onBack = { navController.popBackStack() },
                onModelsClick = { id, name ->
                    navController.navigate(ProviderModelsRoute(providerId = id, providerName = name))
                },
                onPickCheckModel = {
                    navController.navigate(
                        ModelPickerRoute(filterProviderId = route.providerId),
                    )
                },
                viewModel = detailViewModel,
            )
        }
        composable<ProviderModelsRoute> { entry ->
            val route = entry.toRoute<ProviderModelsRoute>()
            ProviderModelsScreen(
                providerId = route.providerId,
                providerName = route.providerName,
                onBack = { navController.popBackStack() },
            )
        }
        composable<ModelPickerRoute> { entry ->
            val route = entry.toRoute<ModelPickerRoute>()
            ModelsScreen(
                selectedModelId = route.selectedModelId,
                selectedProviderId = route.selectedProviderId,
                filterProviderId = route.filterProviderId,
                onPick = { model ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        PICKED_MODEL_KEY,
                        formatPickedModel(model.providerId, model.id, model.displayName),
                    )
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
    }
}
