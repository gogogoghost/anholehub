package cc.jaxy.anlobehub.core.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.serverDataStore by preferencesDataStore(name = "server_prefs")

@Singleton
class ServerStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val baseUrlKey = stringPreferencesKey("base_url")
    private val lastEmailKey = stringPreferencesKey("last_email")

    val baseUrl: Flow<String?> = context.serverDataStore.data.map { it[baseUrlKey] }

    val lastEmail: Flow<String?> = context.serverDataStore.data.map { it[lastEmailKey] }

    suspend fun setBaseUrl(url: String) {
        context.serverDataStore.edit { it[baseUrlKey] = url }
    }

    suspend fun setLastEmail(email: String) {
        context.serverDataStore.edit { it[lastEmailKey] = email }
    }

    suspend fun clear() {
        context.serverDataStore.edit {
            it.remove(baseUrlKey)
            it.remove(lastEmailKey)
        }
    }
}
