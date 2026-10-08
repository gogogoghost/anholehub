package cc.jaxy.anlobehub.app

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import cc.jaxy.anlobehub.feature.agents.AgentsScreen
import cc.jaxy.anlobehub.feature.agents.AgentsViewModel
import cc.jaxy.anlobehub.feature.auth.ServerScreen
import cc.jaxy.anlobehub.feature.chat.ChatScreen
import cc.jaxy.anlobehub.feature.chat.ChatViewModel
import cc.jaxy.anlobehub.feature.models.ModelsScreen
import cc.jaxy.anlobehub.feature.settings.KnowledgeBasesScreen
import cc.jaxy.anlobehub.feature.settings.SettingsScreen
import cc.jaxy.anlobehub.feature.settings.SettingsViewModel
import kotlinx.serialization.Serializable

@Serializable
data object StartupRoute

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
data class ModelPickerRoute(
    val selectedModelId: String? = null,
    val selectedProviderId: String? = null,
)

@Composable
fun AnlobehubNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = StartupRoute,
        enterTransition = { slideInHorizontally(initialOffsetX = { it / 4 }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)) },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)) },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)) },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it / 4 }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)) },
    ) {
        composable<StartupRoute> {
            val viewModel: StartupViewModel = hiltViewModel()
            val destination by viewModel.destination.collectAsStateWithLifecycle()
            LaunchedEffect(destination) {
                when (val d = destination) {
                    is StartupViewModel.Destination.Loading -> Unit
                    is StartupViewModel.Destination.Server -> {
                        navController.navigate(ServerRoute) {
                            popUpTo(StartupRoute) { inclusive = true }
                        }
                    }
                    is StartupViewModel.Destination.Login -> {
                        navController.navigate(LoginRoute(d.baseUrl)) {
                            popUpTo(StartupRoute) { inclusive = true }
                        }
                    }
                    is StartupViewModel.Destination.Home -> {
                        navController.navigate(AgentsRoute) {
                            popUpTo(StartupRoute) { inclusive = true }
                        }
                    }
                }
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingBox()
            }
        }
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
            val pickedRaw by entry.savedStateHandle.getStateFlow<String?>(PICKED_MODEL_KEY, null)
                .collectAsStateWithLifecycle()
            LaunchedEffect(pickedRaw) {
                parsePickedModel(pickedRaw)?.let { picked ->
                    agentsViewModel.setCreateModel(
                        AIModel(
                            id = picked.modelId,
                            displayName = picked.displayName,
                            providerId = picked.providerId,
                        ),
                    )
                    entry.savedStateHandle[PICKED_MODEL_KEY] = null
                }
            }
            var managingAgentId by remember { mutableStateOf<String?>(null) }
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
            )
        }
        composable<KnowledgeBasesRoute> {
            KnowledgeBasesScreen(onBack = { navController.popBackStack() })
        }
        composable<ModelPickerRoute> { entry ->
            val route = entry.toRoute<ModelPickerRoute>()
            ModelsScreen(
                selectedModelId = route.selectedModelId,
                selectedProviderId = route.selectedProviderId,
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
