package com.droidcon.inappupdates

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.droidcon.inappupdates.ui.theme.InAppUpdatesTheme
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.get
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var remoteConfig: FirebaseRemoteConfig
    private lateinit var appUpdateManager: AppUpdateManager
    private lateinit var updateStore: UpdateStore
    private var updateLastCancelled: Long = 1
    private val immediateUpdateResultLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result: ActivityResult ->
        if (result.resultCode != Activity.RESULT_OK) {
            checkForUpdates()
        }
    }
    private val flexibleUpdateResultLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_CANCELED) {
            CoroutineScope(Dispatchers.IO).launch {
                updateStore.setUpdateCancelled()
            }
        } else {
            checkForUpdates()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InAppUpdatesTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting("Android")
                }
            }
        }
        appUpdateManager = AppUpdateManagerFactory.create(applicationContext)
        updateStore = UpdateStore(applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            updateStore.getUpdateLastCancelled.collect { value ->
                updateLastCancelled = value
            }
        }
        getRemoteConfigThenCheckForUpdates()
    }

    override fun onResume() {
        super.onResume()
        val appUpdateManager = AppUpdateManagerFactory.create(applicationContext)
        appUpdateManager
            .appUpdateInfo
            .addOnSuccessListener { appUpdateInfo ->
                when {
                    appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> checkForUpdates()
                    appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED -> appUpdateManager.completeUpdate()
                }
            }
    }

    private fun getRemoteConfigThenCheckForUpdates() {
        remoteConfig = Firebase.remoteConfig
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener(this) {
                checkForUpdates()
            }
    }

    private fun checkForUpdates() {
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                handleUpdateAvailable(appUpdateInfo)
            }
        }
    }

    private fun handleUpdateAvailable(appUpdateInfo: AppUpdateInfo) {
        if (isImmediateUpdateAllowed(appUpdateInfo)) {
            appUpdateManager.startUpdateFlowForResult(appUpdateInfo, immediateUpdateResultLauncher, AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build())
        } else if (isFlexibleUpdateAllowed(appUpdateInfo)) {
            appUpdateManager.startUpdateFlowForResult(appUpdateInfo, flexibleUpdateResultLauncher, AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build())
        }
    }

    private fun isImmediateUpdateAllowed(appUpdateInfo: AppUpdateInfo): Boolean {
        return appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                && (appUpdateInfo.updatePriority() in 4..5 || (remoteConfig[APP_VERSION].asDouble() > BuildConfig.VERSION_CODE && remoteConfig[FORCE_UPDATE].asBoolean()))
    }

    private fun isFlexibleUpdateAllowed(appUpdateInfo: AppUpdateInfo): Boolean {
        return appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                && updateLastCancelled < System.currentTimeMillis()
                && (appUpdateInfo.updatePriority() in 2..3 || remoteConfig[APP_VERSION].asDouble() > BuildConfig.VERSION_CODE)
    }

    companion object {
        private const val APP_VERSION = "app_version"
        private const val FORCE_UPDATE = "force_update"
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    InAppUpdatesTheme {
        Greeting("Android")
    }
}