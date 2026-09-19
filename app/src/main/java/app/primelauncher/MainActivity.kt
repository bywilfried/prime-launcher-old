package app.primelauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

enum class HomeButtonAction(val label: String) {
    NONE("Ne rien faire"), OPEN_DRAWER("Ouvrir le tiroir d'applications"),
    OPEN_SETTINGS("Ouvrir les réglages Prime"), LAUNCH_APP("Lancer une application")
}

class MainActivity : ComponentActivity() {
    private var homePressSerial by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PrimeLauncherApp(packageManager, homePressSerial) }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePressSerial++
    }
}

data class LaunchableApp(val label: String, val component: ComponentName, val icon: ImageBitmap) {
    val key get() = component.flattenToString()
}

@Composable
fun PrimeLauncherApp(pm: PackageManager, homePressSerial: Int) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("prime_settings", Context.MODE_PRIVATE) }
    var drawerOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var addToHome by remember { mutableStateOf(false) }
    var homeAction by remember {
        mutableStateOf(runCatching { HomeButtonAction.valueOf(prefs.getString("home_button_action", HomeButtonAction.OPEN_DRAWER.name)!!) }
            .getOrDefault(HomeButtonAction.OPEN_DRAWER))
    }
    var selectedPackage by remember { mutableStateOf(prefs.getString("home_button_package", null)) }
    var columns by remember { mutableIntStateOf(prefs.getInt("workspace_columns", 5).coerceIn(3, 8)) }
    var rows by remember { mutableIntStateOf(prefs.getInt("workspace_rows", 6).coerceIn(4, 10)) }
    var workspaceKeys by remember {
        mutableStateOf(prefs.getString("workspace_apps", "")!!.split("|").filter { it.isNotBlank() })
    }

    val apps = remember {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(query, 0).map { info ->
            LaunchableApp(info.loadLabel(pm).toString(), ComponentName(info.activityInfo.packageName, info.activityInfo.name),
                info.loadIcon(pm).toBitmap(128, 128).asImageBitmap())
        }.sortedBy { it.label.lowercase() }
    }
    val appMap = remember(apps) { apps.associateBy { it.key } }
    val workspaceApps = workspaceKeys.mapNotNull(appMap::get)

    fun launch(app: LaunchableApp) {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(i) }
    }
    fun persistWorkspace(keys: List<String>) {
        workspaceKeys = keys
        prefs.edit().putString("workspace_apps", keys.joinToString("|")).apply()
    }

    LaunchedEffect(homePressSerial) {
        if (homePressSerial == 0) return@LaunchedEffect
        if (drawerOpen || settingsOpen || addToHome) {
            drawerOpen = false; settingsOpen = false; addToHome = false
        } else when (homeAction) {
            HomeButtonAction.NONE -> Unit
            HomeButtonAction.OPEN_DRAWER -> drawerOpen = true
            HomeButtonAction.OPEN_SETTINGS -> settingsOpen = true
            HomeButtonAction.LAUNCH_APP -> apps.firstOrNull { it.component.packageName == selectedPackage }?.let(::launch)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF101114)) {
            when {
                addToHome -> AppPicker(apps, "Ajouter au bureau", { addToHome = false }) { app ->
                    if (app.key !in workspaceKeys && workspaceKeys.size < columns * rows) persistWorkspace(workspaceKeys + app.key)
                    addToHome = false
                }
                settingsOpen -> SettingsScreen(apps, homeAction, selectedPackage, columns, rows,
                    onAction = { homeAction = it; prefs.edit().putString("home_button_action", it.name).apply() },
                    onApp = { selectedPackage = it.component.packageName; prefs.edit().putString("home_button_package", selectedPackage).apply() },
                    onGrid = { c, r -> columns = c; rows = r; prefs.edit().putInt("workspace_columns", c).putInt("workspace_rows", r).apply() },
                    close = { settingsOpen = false })
                drawerOpen -> AppDrawer(apps, { drawerOpen = false }, ::launch)
                else -> HomeScreen(workspaceApps, columns, ::launch,
                    remove = { persistWorkspace(workspaceKeys - it.key) },
                    openDrawer = { drawerOpen = true }, openSettings = { settingsOpen = true }, addApp = { addToHome = true })
            }
        }
    }
}

@Composable
private fun HomeScreen(
    apps: List<LaunchableApp>, columns: Int, launch: (LaunchableApp) -> Unit, remove: (LaunchableApp) -> Unit,
    openDrawer: () -> Unit, openSettings: () -> Unit, addApp: () -> Unit
) {
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = addApp) { Text("+ Ajouter") }
            TextButton(onClick = openSettings) { Text("Réglages") }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns), modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(apps, key = { it.key }) { app ->
                AppIcon(app, onClick = { launch(app) }, onLongClickFallback = { remove(app) })
            }
        }
        Surface(
            Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f)
        ) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
                FilledTonalButton(onClick = openDrawer) { Text("Applications") }
            }
        }
    }
}

@Composable
private fun AppIcon(app: LaunchableApp, onClick: () -> Unit, onLongClickFallback: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier.padding(6.dp).clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(app.icon, app.label, Modifier.size(52.dp))
            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
            TextButton(onClick = { menu = true }, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(24.dp)) { Text("⋮") }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Retirer du bureau") }, onClick = { menu = false; onLongClickFallback() })
        }
    }
}

@Composable
private fun SettingsScreen(
    apps: List<LaunchableApp>, action: HomeButtonAction, selectedPackage: String?, columns: Int, rows: Int,
    onAction: (HomeButtonAction) -> Unit, onApp: (LaunchableApp) -> Unit, onGrid: (Int, Int) -> Unit, close: () -> Unit
) {
    var chooseApp by remember { mutableStateOf(false) }
    if (chooseApp) {
        AppPicker(apps, "Application pour la touche Home", { chooseApp = false }) { onApp(it); chooseApp = false }
        return
    }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = close) { Text("Retour") }
            Text("Réglages Prime", style = MaterialTheme.typography.headlineSmall)
        }
        Text("Bureau", style = MaterialTheme.typography.titleMedium)
        Text("Grille : $columns × $rows")
        Row {
            TextButton(onClick = { onGrid((columns - 1).coerceAtLeast(3), rows) }) { Text("− Colonnes") }
            TextButton(onClick = { onGrid((columns + 1).coerceAtMost(8), rows) }) { Text("+ Colonnes") }
        }
        Row {
            TextButton(onClick = { onGrid(columns, (rows - 1).coerceAtLeast(4)) }) { Text("− Lignes") }
            TextButton(onClick = { onGrid(columns, (rows + 1).coerceAtMost(10)) }) { Text("+ Lignes") }
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text("Touche Home", style = MaterialTheme.typography.titleMedium)
        HomeButtonAction.entries.forEach { option ->
            Row(Modifier.fillMaxWidth().clickable { onAction(option); if (option == HomeButtonAction.LAUNCH_APP) chooseApp = true }
                .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = action == option, onClick = null)
                Text(option.label)
            }
        }
        if (action == HomeButtonAction.LAUNCH_APP) {
            val name = apps.firstOrNull { it.component.packageName == selectedPackage }?.label ?: "Choisir une application"
            Button(onClick = { chooseApp = true }) { Text(name) }
        }
    }
}

@Composable
private fun AppPicker(apps: List<LaunchableApp>, title: String, close: () -> Unit, select: (LaunchableApp) -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = close) { Text("Retour") }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        LazyVerticalGrid(columns = GridCells.Adaptive(72.dp), contentPadding = PaddingValues(12.dp)) {
            items(apps, key = { it.key }) { app ->
                Column(Modifier.padding(8.dp).clickable { select(app) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(app.icon, app.label, Modifier.size(52.dp))
                    Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun AppDrawer(apps: List<LaunchableApp>, close: () -> Unit, launch: (LaunchableApp) -> Unit) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(apps, search) { if (search.isBlank()) apps else apps.filter { it.label.contains(search, true) } }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = close) { Text("Accueil") }
            Text("Applications", style = MaterialTheme.typography.titleLarge)
        }
        OutlinedTextField(search, { search = it }, label = { Text("Rechercher") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        LazyVerticalGrid(columns = GridCells.Adaptive(72.dp), contentPadding = PaddingValues(12.dp)) {
            items(filtered, key = { it.key }) { app ->
                Column(Modifier.padding(8.dp).clickable { launch(app) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(app.icon, app.label, Modifier.size(52.dp))
                    Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
