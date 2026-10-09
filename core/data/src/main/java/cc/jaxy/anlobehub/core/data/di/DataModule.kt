package cc.jaxy.anlobehub.core.data.di

import cc.jaxy.anlobehub.core.data.agent.AgentRepository
import cc.jaxy.anlobehub.core.data.agent.AgentRepositoryImpl
import cc.jaxy.anlobehub.core.data.chat.MessageRepository
import cc.jaxy.anlobehub.core.data.chat.MessageRepositoryImpl
import cc.jaxy.anlobehub.core.data.chat.ModelRepository
import cc.jaxy.anlobehub.core.data.chat.ModelRepositoryImpl
import cc.jaxy.anlobehub.core.data.provider.ProviderRepository
import cc.jaxy.anlobehub.core.data.provider.ProviderRepositoryImpl
import cc.jaxy.anlobehub.core.data.chat.TopicRepository
import cc.jaxy.anlobehub.core.data.chat.TopicRepositoryImpl
import cc.jaxy.anlobehub.core.data.discovery.DiscoveryRepository
import cc.jaxy.anlobehub.core.data.discovery.DiscoveryRepositoryImpl
import cc.jaxy.anlobehub.core.data.files.FileRepository
import cc.jaxy.anlobehub.core.data.files.FileRepositoryImpl
import cc.jaxy.anlobehub.core.data.user.UserRepository
import cc.jaxy.anlobehub.core.data.user.UserRepositoryImpl
import cc.jaxy.anlobehub.core.data.session.AuthRepository
import cc.jaxy.anlobehub.core.data.session.AuthRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    abstract fun bindAgentRepository(impl: AgentRepositoryImpl): AgentRepository

    @Binds
    abstract fun bindTopicRepository(impl: TopicRepositoryImpl): TopicRepository

    @Binds
    abstract fun bindMessageRepository(impl: MessageRepositoryImpl): MessageRepository

    @Binds
    abstract fun bindModelRepository(impl: ModelRepositoryImpl): ModelRepository

    @Binds
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    abstract fun bindFileRepository(impl: FileRepositoryImpl): FileRepository

    @Binds
    abstract fun bindDiscoveryRepository(impl: DiscoveryRepositoryImpl): DiscoveryRepository

    @Binds
    abstract fun bindProviderRepository(impl: ProviderRepositoryImpl): ProviderRepository
}
