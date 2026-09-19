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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

enum class HomeButtonAction(val label: String) {
    NONE("Ne rien faire"), OPEN_DRAWER("Ouvrir le tiroir d'applications"),
    OPEN_SETTINGS("Ouvrir les réglages Prime"), LAUNCH_APP("Lancer une application")
}
data class LaunchableApp(val label: String, val component: ComponentName, val icon: ImageBitmap) {
    val key get() = component.flattenToString()
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

@Composable
fun PrimeLauncherApp(pm: PackageManager, homePressSerial: Int) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("prime_settings", Context.MODE_PRIVATE) }
    var drawerOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var addToHome by remember { mutableStateOf(false) }\n    var addToDock by remember { mutableStateOf(false) }
    var homeAction by remember { mutableStateOf(runCatching {
        HomeButtonAction.valueOf(prefs.getString("home_button_action", HomeButtonAction.OPEN_DRAWER.name)!!)
    }.getOrDefault(HomeButtonAction.OPEN_DRAWER)) }
    var selectedPackage by remember { mutableStateOf(prefs.getString("home_button_package", null)) }
    var columns by remember { mutableIntStateOf(prefs.getInt("workspace_columns", 5).coerceIn(3, 16)) }
    var rows by remember { mutableIntStateOf(prefs.getInt("workspace_rows", 6).coerceIn(4, 20)) }
    var homeLabels by remember { mutableStateOf(prefs.getBoolean("home_labels", false)) }
    var drawerLabels by remember { mutableStateOf(prefs.getBoolean("drawer_labels", true)) }
    var subGrid by remember { mutableStateOf(prefs.getBoolean("subgrid_positioning", false)) }
    var dockEnabled by remember { mutableStateOf(prefs.getBoolean("dock_enabled", true)) }
    var dockLabels by remember { mutableStateOf(prefs.getBoolean("dock_labels", false)) }
    var dockBackground by remember { mutableStateOf(prefs.getBoolean("dock_background", true)) }
    var dockInfinite by remember { mutableStateOf(prefs.getBoolean("dock_infinite", false)) }
    var dockIcons by remember { mutableIntStateOf(prefs.getInt("dock_icons", 5).coerceIn(3, 12)) }
    var dockPages by remember { mutableIntStateOf(prefs.getInt("dock_pages", 1).coerceIn(1, 5)) }
    var dockScale by remember { mutableIntStateOf(prefs.getInt("dock_scale", 100).coerceIn(60, 140)) }
    var workspaceKeys by remember { mutableStateOf(prefs.getString("workspace_apps", "")!!.split("|").filter(String::isNotBlank)) }
    var dockKeys by remember { mutableStateOf(prefs.getString("dock_apps", "")!!.split("|").filter(String::isNotBlank)) }

    val apps = remember {
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(q, 0).map { info ->
            LaunchableApp(info.loadLabel(pm).toString(), ComponentName(info.activityInfo.packageName, info.activityInfo.name),
                info.loadIcon(pm).toBitmap(128, 128).asImageBitmap())
        }.sortedBy { it.label.lowercase() }
    }
    val appMap = remember(apps) { apps.associateBy { it.key } }
    fun launch(app: LaunchableApp) {
        runCatching { context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    fun saveWorkspace(v: List<String>) { workspaceKeys = v; prefs.edit().putString("workspace_apps", v.joinToString("|")).apply() }
    fun saveDock(v: List<String>) { dockKeys = v; prefs.edit().putString("dock_apps", v.joinToString("|")).apply() }

    LaunchedEffect(homePressSerial) {
        if (homePressSerial == 0) return@LaunchedEffect
        if (drawerOpen || settingsOpen || addToHome || addToDock) { drawerOpen = false; settingsOpen = false; addToHome = false; addToDock = false }
        else when (homeAction) {
            HomeButtonAction.NONE -> Unit
            HomeButtonAction.OPEN_DRAWER -> drawerOpen = true
            HomeButtonAction.OPEN_SETTINGS -> settingsOpen = true
            HomeButtonAction.LAUNCH_APP -> apps.firstOrNull { it.component.packageName == selectedPackage }?.let(::launch)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        when {
            addToHome -> AppPicker(apps, "Ajouter au bureau", { addToHome = false }) {
                if (it.key !in workspaceKeys) saveWorkspace(workspaceKeys + it.key); addToHome = false
            }
            addToDock -> AppPicker(apps, "Ajouter au dock", { addToDock = false }) {
                val capacity = dockIcons * dockPages
                if (it.key !in dockKeys && dockKeys.size < capacity) saveDock(dockKeys + it.key)
                addToDock = false
            }
            settingsOpen -> SettingsScreen(
                apps, homeAction, selectedPackage, columns, rows, homeLabels, drawerLabels, subGrid,
                dockEnabled, dockScale, dockLabels, dockBackground, dockIcons, dockPages, dockInfinite,
                onAction = { homeAction = it; prefs.edit().putString("home_button_action", it.name).apply() },
                onApp = { selectedPackage = it.component.packageName; prefs.edit().putString("home_button_package", selectedPackage).apply() },
                onGrid = { c,r -> columns=c; rows=r; prefs.edit().putInt("workspace_columns",c).putInt("workspace_rows",r).apply() },
                toggle = { key,value -> prefs.edit().putBoolean(key,value).apply()
                    when(key) { "home_labels"->homeLabels=value; "drawer_labels"->drawerLabels=value; "subgrid_positioning"->subGrid=value
                        "dock_enabled"->dockEnabled=value; "dock_labels"->dockLabels=value; "dock_background"->dockBackground=value; "dock_infinite"->dockInfinite=value } },
                setInt = { key,value -> prefs.edit().putInt(key,value).apply()
                    when(key) { "dock_scale"->dockScale=value; "dock_icons"->dockIcons=value; "dock_pages"->dockPages=value } },
                close = { settingsOpen=false })
            drawerOpen -> AppDrawer(apps, drawerLabels, { drawerOpen=false }, ::launch)
            else -> HomeScreen(workspaceKeys.mapNotNull(appMap::get), dockKeys.mapNotNull(appMap::get), columns, homeLabels,
                dockEnabled, dockLabels, dockBackground, dockScale, dockIcons, dockPages, dockInfinite, ::launch,
                remove = { saveWorkspace(workspaceKeys-it.key) }, removeDock = { saveDock(dockKeys-it.key) },
                openDrawer={drawerOpen=true}, openSettings={settingsOpen=true}, addApp={addToHome=true}, addDock={addToDock=true},\n                reorderDock = { from,to -> if(from in dockKeys.indices && to in dockKeys.indices) { val v=dockKeys.toMutableList(); val x=v.removeAt(from); v.add(to,x); saveDock(v) } })
        }
    }
}

@Composable
private fun HomeScreen(
    apps: List<LaunchableApp>, dockApps: List<LaunchableApp>, columns: Int, showLabels: Boolean,
    dockEnabled: Boolean, dockLabels: Boolean, dockBackground: Boolean, dockScale: Int,
    launch: (LaunchableApp)->Unit, remove:(LaunchableApp)->Unit, removeDock:(LaunchableApp)->Unit,
    openDrawer:()->Unit, openSettings:()->Unit, addApp:()->Unit, addDock:()->Unit, reorderDock:(Int,Int)->Unit
) {
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF050713), Color(0xFF102A70), Color(0xFF311060))))) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp), horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick=addApp){Text("+ Ajouter")}
                TextButton(onClick=openSettings){Text("Réglages")}
            }
            LazyVerticalGrid(GridCells.Fixed(columns), Modifier.weight(1f), contentPadding=PaddingValues(8.dp)) {
                items(apps,key={it.key}) { app -> AppIcon(app,showLabels,{launch(app)},{remove(app)}) }
            }
            if (dockEnabled) {
                Surface(Modifier.fillMaxWidth().padding(12.dp), shape=RoundedCornerShape(28.dp),
                    color=if(dockBackground) MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.70f) else Color.Transparent) {
                    var dockPage by remember { mutableIntStateOf(0) }
                    val pageCount = dockPages.coerceAtLeast(1)
                    val safePage = dockPage.coerceIn(0, pageCount - 1)
                    val pageApps = dockApps.drop(safePage * dockIcons).take(dockIcons)
                    Column(Modifier.fillMaxWidth().padding(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceEvenly, verticalAlignment=Alignment.CenterVertically) {
                            if (pageCount > 1) TextButton(onClick={ dockPage = if(safePage == 0) { if(dockInfinite) pageCount-1 else 0 } else safePage-1 }) { Text("‹") }
                            pageApps.forEachIndexed { localIndex, app ->
                                val globalIndex = safePage * dockIcons + localIndex
                                DockIcon(app,dockLabels,{launch(app)},{removeDock(app)},(52*dockScale/100).dp,
                                    moveLeft={ if(globalIndex>0) reorderDock(globalIndex,globalIndex-1) },
                                    moveRight={ if(globalIndex<dockApps.lastIndex) reorderDock(globalIndex,globalIndex+1) })
                            }
                            if(pageApps.size < dockIcons) TextButton(onClick=addDock){Text("+")}
                            FilledTonalButton(onClick=openDrawer){Text("Apps")}
                            if (pageCount > 1) TextButton(onClick={ dockPage = if(safePage == pageCount-1) { if(dockInfinite) 0 else safePage } else safePage+1 }) { Text("›") }
                        }
                        if(pageCount > 1) Text("${safePage+1} / $pageCount", Modifier.align(Alignment.CenterHorizontally), style=MaterialTheme.typography.labelSmall)
                    }
                }
            } else FilledTonalButton(onClick=openDrawer, Modifier.align(Alignment.CenterHorizontally).padding(12.dp)){Text("Applications")}
        }
    }
}

@Composable
private fun AppIcon(app:LaunchableApp, showLabel:Boolean, onClick:()->Unit, remove:()->Unit, size:androidx.compose.ui.unit.Dp=52.dp) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(Modifier.padding(4.dp).clickable(onClick=onClick), horizontalAlignment=Alignment.CenterHorizontally) {
            Image(app.icon,app.label,Modifier.size(size))
            if(showLabel) Text(app.label,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall)
            TextButton({menu=true},contentPadding=PaddingValues(0.dp),modifier=Modifier.height(20.dp)){Text("⋮")}
        }
        DropdownMenu(menu,{menu=false}) { DropdownMenuItem({Text("Retirer")},{menu=false;remove()}) }
    }
}

@Composable
private fun SettingsScreen(
    apps:List<LaunchableApp>, action:HomeButtonAction, selectedPackage:String?, columns:Int, rows:Int,
    homeLabels:Boolean, drawerLabels:Boolean, subGrid:Boolean, dockEnabled:Boolean, dockScale:Int, dockLabels:Boolean,
    dockBackground:Boolean, dockIcons:Int, dockPages:Int, dockInfinite:Boolean,
    onAction:(HomeButtonAction)->Unit, onApp:(LaunchableApp)->Unit, onGrid:(Int,Int)->Unit,
    toggle:(String,Boolean)->Unit, setInt:(String,Int)->Unit, close:()->Unit
) {
    var chooseApp by remember { mutableStateOf(false) }
    if(chooseApp){ AppPicker(apps,"Application pour la touche Home",{chooseApp=false}){onApp(it);chooseApp=false}; return }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick=close){Text("Retour")};Text("Réglages Prime",style=MaterialTheme.typography.headlineSmall)}
        Text("Bureau",style=MaterialTheme.typography.titleMedium)
        Text("Grille : $columns × $rows")
        Row { TextButton({onGrid((columns-1).coerceAtLeast(3),rows)}){Text("− Colonnes")}; TextButton({onGrid((columns+1).coerceAtMost(16),rows)}){Text("+ Colonnes")} }
        Row { TextButton({onGrid(columns,(rows-1).coerceAtLeast(4))}){Text("− Lignes")}; TextButton({onGrid(columns,(rows+1).coerceAtMost(20))}){Text("+ Lignes")} }
        SettingSwitch("Noms des applications sur le bureau",homeLabels){toggle("home_labels",it)}
        SettingSwitch("Placement entre les cases",subGrid){toggle("subgrid_positioning",it)}
        Text("Fond Prime par défaut actif. Le sélecteur de fond personnalisé arrive avec le moteur de widgets.",style=MaterialTheme.typography.bodySmall)
        HorizontalDivider(Modifier.padding(vertical=10.dp))
        Text("Tiroir d'applications",style=MaterialTheme.typography.titleMedium)
        SettingSwitch("Noms des applications",drawerLabels){toggle("drawer_labels",it)}
        HorizontalDivider(Modifier.padding(vertical=10.dp))
        Text("Dock",style=MaterialTheme.typography.titleMedium)
        SettingSwitch("Activer",dockEnabled){toggle("dock_enabled",it)}
        IntSetting("Taille des icônes","$dockScale%",dockScale,60,140,10){setInt("dock_scale",it)}
        SettingSwitch("Libellé",dockLabels){toggle("dock_labels",it)}
        SettingSwitch("Arrière-plan du dock",dockBackground){toggle("dock_background",it)}
        IntSetting("Icônes du dock","$dockIcons",dockIcons,3,12,1){setInt("dock_icons",it)}
        IntSetting("Pages du dock","$dockPages",dockPages,1,5,1){setInt("dock_pages",it)}
        SettingSwitch("Défilement infini",dockInfinite){toggle("dock_infinite",it)}
        HorizontalDivider(Modifier.padding(vertical=10.dp))
        Text("Touche Home",style=MaterialTheme.typography.titleMedium)
        HomeButtonAction.entries.forEach { o -> Row(Modifier.fillMaxWidth().clickable{onAction(o);if(o==HomeButtonAction.LAUNCH_APP)chooseApp=true}.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
            RadioButton(action==o,null);Text(o.label)} }
        if(action==HomeButtonAction.LAUNCH_APP) Button({chooseApp=true}){Text(apps.firstOrNull{it.component.packageName==selectedPackage}?.label?:"Choisir une application")}
    }
}

@Composable private fun SettingSwitch(label:String,checked:Boolean,change:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth().clickable{change(!checked)}.padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f));Switch(checked,change)}
}
@Composable private fun IntSetting(label:String,valueText:String,value:Int,min:Int,max:Int,step:Int,change:(Int)->Unit){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("$label : $valueText",Modifier.weight(1f));TextButton({change((value-step).coerceAtLeast(min))}){Text("−")};TextButton({change((value+step).coerceAtMost(max))}){Text("+")}}
}
@Composable private fun AppPicker(apps:List<LaunchableApp>,title:String,close:()->Unit,select:(LaunchableApp)->Unit){
    Column(Modifier.fillMaxSize().systemBarsPadding()){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){TextButton(onClick=close){Text("Retour")};Text(title,style=MaterialTheme.typography.titleLarge)}
        LazyVerticalGrid(GridCells.Adaptive(72.dp),contentPadding=PaddingValues(12.dp)){items(apps,key={it.key}){a->Column(Modifier.padding(8.dp).clickable{select(a)},horizontalAlignment=Alignment.CenterHorizontally){Image(a.icon,a.label,Modifier.size(52.dp));Text(a.label,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall)}}}}
}
@Composable private fun AppDrawer(apps:List<LaunchableApp>,showLabels:Boolean,close:()->Unit,launch:(LaunchableApp)->Unit){
    var search by remember{mutableStateOf("")}; val filtered=if(search.isBlank())apps else apps.filter{it.label.contains(search,true)}
    Column(Modifier.fillMaxSize().systemBarsPadding()){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){TextButton(onClick=close){Text("Accueil")};Text("Applications",style=MaterialTheme.typography.titleLarge)}
        OutlinedTextField(search,{search=it},label={Text("Rechercher")},modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp))
        LazyVerticalGrid(GridCells.Adaptive(72.dp),contentPadding=PaddingValues(12.dp)){items(filtered,key={it.key}){a->Column(Modifier.padding(8.dp).clickable{launch(a)},horizontalAlignment=Alignment.CenterHorizontally){Image(a.icon,a.label,Modifier.size(52.dp));if(showLabels)Text(a.label,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall)}}}}
}
