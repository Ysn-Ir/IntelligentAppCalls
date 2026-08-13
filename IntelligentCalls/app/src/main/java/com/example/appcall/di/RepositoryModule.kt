package com.example.appcall.di

import com.example.appcall.data.repository.VoipRepositoryImpl
import com.example.appcall.domain.repository.VoipRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindVoipRepository(
        voipRepositoryImpl: VoipRepositoryImpl
    ): VoipRepository
}
