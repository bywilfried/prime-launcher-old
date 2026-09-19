package app.primelauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

enum class HomeButtonAction(val label: String) {
    NONE("Ne rien faire"),
    OPEN_DRAWER("Ouvrir le tiroir d'applications"),
    OPEN_SETTINGS("Ouvrir les réglages Prime"),
    LAUNCH_APP("Lancer une application")
}

class MainActivity : ComponentActivity() {
    private var homePressSerial by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PrimeLauncherApp(packageManager, homePressSerial) }
    }

    // Android brings an existing HOME activity to the foreground when HOME is pressed.
    // With singleTask, onNewIntent lets Prime distinguish that event while already alive.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePressSerial++
    }
}

data class LaunchableApp(val label: String, val component: ComponentName, val icon: ImageBitmap)

@Composable
fun PrimeLauncherApp(pm: PackageManager, homePressSerial: Int) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("prime_settings", Context.MODE_PRIVATE) }
    var drawerOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var homeAction by remember {
        mutableStateOf(runCatching {
            HomeButtonAction.valueOf(prefs.getString("home_button_action", HomeButtonAction.OPEN_DRAWER.name)!!)
        }.getOrDefault(HomeButtonAction.OPEN_DRAWER))
    }
    var selectedPackage by remember { mutableStateOf(prefs.getString("home_button_package", null)) }

    val apps = remember {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(query, 0).map { info ->
            LaunchableApp(
                info.loadLabel(pm).toString(),
                ComponentName(info.activityInfo.packageName, info.activityInfo.name),
                info.loadIcon(pm).toBitmap(128, 128).asImageBitmap()
            )
        }.sortedBy { it.label.lowercase() }
    }

    fun launch(app: LaunchableApp) {
        val launchIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(launchIntent) }
    }

    LaunchedEffect(homePressSerial) {
        if (homePressSerial == 0) return@LaunchedEffect
        // First HOME closes overlays; pressing HOME again on the main home executes the configured action.
        if (drawerOpen || settingsOpen) {
            drawerOpen = false
            settingsOpen = false
        } else {
            when (homeAction) {
                HomeButtonAction.NONE -> Unit
                HomeButtonAction.OPEN_DRAWER -> drawerOpen = true
                HomeButtonAction.OPEN_SETTINGS -> settingsOpen = true
                HomeButtonAction.LAUNCH_APP -> apps.firstOrNull { it.component.packageName == selectedPackage }?.let(::launch)
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
            when {
                settingsOpen -> HomeButtonSettings(
                    apps = apps,
                    action = homeAction,
                    selectedPackage = selectedPackage,
                    onAction = {
                        homeAction = it
                        prefs.edit().putString("home_button_action", it.name).apply()
                    },
                    onApp = {
                        selectedPackage = it.component.packageName
                        prefs.edit().putString("home_button_package", selectedPackage).apply()
                    },
                    close = { settingsOpen = false }
                )
                drawerOpen -> AppDrawer(apps, { drawerOpen = false }, ::launch)
                else -> HomeScreen(
                    openDrawer = { drawerOpen = true },
                    openSettings = { settingsOpen = true }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(openDrawer: () -> Unit, openSettings: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Text("Prime Launcher", Modifier.align(Alignment.TopStart).padding(24.dp), style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = openSettings, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) { Text("Réglages") }
        FilledTonalButton(onClick = openDrawer, modifier = Modifier.align(Alignment.BottomCenter).padding(28.dp)) {
            Text("Applications")
        }
    }
}

@Composable
private fun HomeButtonSettings(
    apps: List<LaunchableApp>,
    action: HomeButtonAction,
    selectedPackage: String?,
    onAction: (HomeButtonAction) -> Unit,
    onApp: (LaunchableApp) -> Unit,
    close: () -> Unit
) {
    var chooseApp by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = close) { Text("Retour") }
            Text("Touche Home", style = MaterialTheme.typography.headlineSmall)
        }
        Text("Action lorsque vous appuyez sur Home alors que Prime affiche déjà la page d'accueil principale.")
        Spacer(Modifier.height(16.dp))
        HomeButtonAction.entries.forEach { option ->
            Row(
                Modifier.fillMaxWidth().clickable {
                    onAction(option)
                    if (option == HomeButtonAction.LAUNCH_APP) chooseApp = true
                }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = action == option, onClick = null)
                Spacer(Modifier.width(8.dp))
                Text(option.label)
            }
        }
        if (action == HomeButtonAction.LAUNCH_APP) {
            val name = apps.firstOrNull { it.component.packageName == selectedPackage }?.label ?: "Choisir une application"
            Button(onClick = { chooseApp = true }) { Text(name) }
        }
        if (chooseApp) {
            Spacer(Modifier.height(12.dp))
            Text("Choisir l'application", style = MaterialTheme.typography.titleMedium)
            LazyVerticalGrid(columns = GridCells.Adaptive(72.dp), modifier = Modifier.weight(1f)) {
                items(apps, key = { it.component.flattenToString() }) { app ->
                    Column(
                        Modifier.padding(8.dp).clickable { onApp(app); chooseApp = false },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(app.icon, app.label, Modifier.size(44.dp))
                        Text(app.label, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppDrawer(apps: List<LaunchableApp>, close: () -> Unit, launch: (LaunchableApp) -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = close) { Text("Accueil") }
            Spacer(Modifier.width(12.dp))
            Text("Toutes les applications", style = MaterialTheme.typography.titleLarge)
        }
        LazyVerticalGrid(columns = GridCells.Adaptive(72.dp), contentPadding = PaddingValues(12.dp)) {
            items(apps, key = { it.component.flattenToString() }) { app ->
                Column(Modifier.padding(8.dp).clickable { launch(app) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(app.icon, app.label, Modifier.size(52.dp))
                    Text(app.label, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
