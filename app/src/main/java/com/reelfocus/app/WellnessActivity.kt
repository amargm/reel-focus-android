package com.reelfocus.app

import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.provider.Settings
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

private val bgLight=Color(0xFFF4F4F2); private val white=Color.White; private val ink=Color(0xFF111111)
private val muted=Color(0xFF777873); private val dark=Color(0xFF0E0F0C); private val panel=Color(0xFF1B1D17)
private val lime=Color(0xFFC6E36B); private val moss=Color(0xFF5F7D1C)
@Immutable private data class Challenge(val id:String,val title:String,val category:String,val tier:String,val days:Int,val summary:String,val action:String,val benefit:String,val limit:Int?=null)
private val catalog=listOf(
 Challenge("screen_5h","A five-hour phone day","Digital balance","Gentle start",1,"Keep total phone use to five hours or less today.","Set an intention, then check Android usage tonight.","Make room for the parts of your day that happen offline.",300),
 Challenge("morning_60","First hour, fully yours","Mindful mornings","Gentle start",3,"Spend the first hour after waking without your phone.","Leave your phone outside the bedroom. Begin with water, daylight or movement.","Start the day on your terms."),
 Challenge("phone_meals","Meals without a screen","Presence","Gentle start",7,"Keep your phone away during one meal each day.","Put your phone out of reach before you sit down to eat.","Give food and company your full attention."),
 Challenge("notification_pause","Quiet the noise","Digital calm","Gentle start",1,"Turn off non-essential notifications for one day.","Keep calls and essential alerts; silence the rest.","Reduce interruptions."),
 Challenge("social_uninstall","A week away from social feeds","Digital boundaries","Build a rhythm",7,"Take seven days away from the social apps you choose.","Uninstall chosen apps yourself and reflect in your daily check-in.","Create distance from habitual scrolling."),
 Challenge("screen_3h","A three-hour phone day","Digital balance","Build a rhythm",7,"Aim for three hours or less of phone use each day.","Check your usage and plan two phone-free blocks.","Build a steadier relationship with your device.",180),
 Challenge("no_reels","No short-form scroll","Digital boundaries","Build a rhythm",7,"Take seven days away from short-video feeds.","Remove shortcuts, sign out or uninstall chosen apps.","Break the automatic scroll loop."),
 Challenge("focus_blocks","Two phone-free focus blocks","Attention","Build a rhythm",5,"Create two 45-minute phone-free blocks daily.","Keep your phone in another room and choose one task.","Make space for deeper attention."),
 Challenge("sleep_boundary","A calmer final hour","Rest","Build a rhythm",7,"Keep the final hour before sleep screen-free.","Charge your phone away from your bed.","Give your mind a gentler transition."),
 Challenge("screen_2h","A two-hour digital reset","Digital boundaries","Deep reset",7,"Keep total phone use to two hours or less daily.","Plan essential use and review your total without judgment.","Practise deliberate device use.",120),
 Challenge("social_14","Fourteen days off social media","Digital boundaries","Deep reset",14,"Take a two-week break from social apps you select.","Uninstall or log out; keep important contacts reachable.","Reset habits that pull your attention back."),
 Challenge("offline_day","One full offline day","Presence","Deep reset",1,"Take one day away from non-essential phone use.","Tell important contacts how to reach you in an emergency.","Experience a day with fewer digital demands.")
)
@Immutable private data class Run(val id:String,val started:Long,val checks:Set<Long>)
private fun todayKey(t:Long=System.currentTimeMillis()):Long { val c=Calendar.getInstance();c.timeInMillis=t;c.set(11,0);c.set(12,0);c.set(13,0);c.set(14,0);return c.timeInMillis/86400000L }
private class Store(ctx:Context) {
 private val p=ctx.getSharedPreferences("wellness_challenges",0)
 fun active():Run? { return try { val raw=p.getString("active_challenge",null)?:return null;val j=JSONObject(raw);val a=j.optJSONArray("checkIns")?:JSONArray();Run(j.getString("id"),j.getLong("startedAt"),(0 until a.length()).map{a.getLong(it)}.toSet()) }catch(_:Exception){null} }
 fun save(r:Run){val a=JSONArray();r.checks.sorted().forEach{a.put(it)};p.edit().putString("active_challenge",JSONObject().put("id",r.id).put("startedAt",r.started).put("checkIns",a).toString()).apply()}
 fun finish(result:String){val r=active()?:return;val h=JSONArray(p.getString("challenge_history","[]")?: "[]");h.put(JSONObject().put("id",r.id).put("endedAt",System.currentTimeMillis()).put("result",result).put("checkIns",r.checks.size));p.edit().putString("challenge_history",h.toString()).remove("active_challenge").apply()}
 fun history()=JSONArray(p.getString("challenge_history","[]")?: "[]")
}
class WellnessActivity:ComponentActivity(){
 var permissionRefresh by mutableIntStateOf(0)
 private var jankStats:androidx.metrics.performance.JankStats?=null
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);enableEdgeToEdge(statusBarStyle=SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT));androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,false);jankStats=androidx.metrics.performance.JankStats.createAndTrack(window){frameData->if(frameData.isJank)android.util.Log.w("ReelFocusJank","Janky frame: ${frameData.frameDurationUiNanos/1_000_000} ms")};setContent{AppTheme{WellnessApp(intent.getStringExtra("permission_to_explain"))}}}
 override fun onResume(){super.onResume();permissionRefresh++}
 override fun onDestroy(){jankStats?.isTrackingEnabled=false;jankStats=null;super.onDestroy()}
}
@Composable private fun AppTheme(content:@Composable ()->Unit){
 val isDark=isSystemInDarkTheme()
 val colors=if(isDark) darkColorScheme(primary=lime,background=Color(0xFF050505),surface=Color(0xFF161714),onSurface=Color(0xFFF2F2EE),onBackground=Color(0xFFF2F2EE),onSurfaceVariant=Color(0xFFB3B4AD))
 else lightColorScheme(primary=moss,background=bgLight,surface=white,onSurface=ink,onBackground=ink,onSurfaceVariant=muted)
 MaterialTheme(colorScheme=colors,typography=Typography(
  displayLarge=Typography().displayLarge.copy(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Light,fontSize=56.sp),
  headlineSmall=Typography().headlineSmall.copy(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Normal,fontSize=20.sp),
  bodyLarge=Typography().bodyLarge.copy(fontFamily=FontFamily.SansSerif,fontSize=16.sp),
  bodyMedium=Typography().bodyMedium.copy(fontFamily=FontFamily.SansSerif,fontSize=14.sp),
  labelMedium=Typography().labelMedium.copy(fontFamily=FontFamily.SansSerif,fontSize=12.sp)
 ),content=content)
}
private data class Tab(val route:String,val title:String,val icon:androidx.compose.ui.graphics.vector.ImageVector)
private val tabs=listOf(Tab("home","Home",Icons.Rounded.Home),Tab("challenges","Challenges",Icons.Rounded.Explore),Tab("progress","Progress",Icons.Rounded.Insights),Tab("profile","Profile",Icons.Rounded.Person))
@Composable private fun WellnessApp(initialPermission:String?=null){
 val context= LocalContext.current
 val permissionRefresh=(context as? WellnessActivity)?.permissionRefresh ?: 0
 val store= remember{Store(context)};val nav= rememberNavController()
 val disclosureStore=remember{PermissionDisclosureStore(context)}
 val permissionScope=rememberCoroutineScope()
 var active by remember{mutableStateOf(store.active())};var selected by remember{mutableStateOf<Challenge?>(null)};var refresh by remember{mutableIntStateOf(0)};var showDropConfirmation by remember{mutableStateOf(false)}
 var selectedPermission by remember{mutableStateOf(initialPermission?.let{runCatching{PermissionKind.valueOf(it)}.getOrNull()})}
 var permissionPending by remember{mutableStateOf<PermissionKind?>(null)}
 var permissionDenied by remember{mutableStateOf<PermissionKind?>(null)}
 val permissionHaptics=LocalHapticFeedback.current
 var serviceStopped by remember{mutableStateOf(context.getSharedPreferences("service_status",0).getBoolean("monitoring_expected",false) && (!OverlayService.isRunning || !permissionGranted(context,PermissionKind.OVERLAY) || !permissionGranted(context,PermissionKind.USAGE) || !permissionGranted(context,PermissionKind.NOTIFICATIONS)))}
 LaunchedEffect(permissionRefresh){serviceStopped=context.getSharedPreferences("service_status",0).getBoolean("monitoring_expected",false) && !OverlayService.isRunning}
 val notificationPermissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
  permissionPending=null
  if(granted){permissionDenied=null;permissionHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove);ToastHost(context,"Permission on")}
  else{permissionDenied=PermissionKind.NOTIFICATIONS;ToastHost(context,"Notifications remain off")}
 }
 LaunchedEffect(permissionRefresh){
  val pending=permissionPending
  if(pending!=null){
   permissionPending=null
   if(permissionGranted(context,pending)){
    permissionDenied=null
    permissionHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    ToastHost(context,"Permission on")
   }else permissionDenied=pending
  }
 }
 val back by nav.currentBackStackEntryAsState();val route=back?.destination?.route?:"home";val isDark=isSystemInDarkTheme()
 val keyboardOpen=WindowInsets.ime.getBottom(LocalDensity.current)>0
  val sheetShowing=selected!=null || selectedPermission!=null || showDropConfirmation
  Box(Modifier.fillMaxSize().background(if(isDark)Color(0xFF050505) else bgLight)){
  val reduceMotion=android.provider.Settings.Global.getFloat(context.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
 NavHost(nav,if(initialPermission!=null)"permissions" else "home",Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
  enterTransition={if(reduceMotion) androidx.compose.animation.EnterTransition.None else fadeIn(tween(280))+slideInVertically(tween(280)){it/18}},
  exitTransition={if(reduceMotion) androidx.compose.animation.ExitTransition.None else fadeOut(tween(180))}
 ){
  composable("home"){Home(active,{nav.navigate("challenges")},{selected=it},{selectedPermission=PermissionKind.USAGE},permissionDenied==PermissionKind.USAGE,{selectedPermission=PermissionKind.USAGE;permissionDenied=null},{permissionDenied=null},serviceStopped,{context.getSharedPreferences("service_status",0).edit().putBoolean("monitoring_expected",false).apply();serviceStopped=false},{val a=active
   val ch=catalog.firstOrNull{it.id==a?.id}
   if(a!=null && ch?.limit!=null){
    if(!hasUsagePermission(context)){selectedPermission=PermissionKind.USAGE}
    else{permissionScope.launch{
     val minutes=withContext(Dispatchers.IO){phoneMinutes(context)}
     if(minutes>ch.limit)ToastHost(context,"Today's phone-time limit has been exceeded")
     else{val updated=a.copy(checks=a.checks+todayKey());active=updated;store.save(updated);ToastHost(context,"Check-in saved")}
    }}
   }else if(a!=null){active=a.copy(checks=a.checks+todayKey());store.save(active!!);ToastHost(context,"Check-in saved")}
  },{store.finish("Completed");active=null;refresh++},{showDropConfirmation=true})}
  composable("challenges"){Challenges(active){selected=it}}
  composable("progress"){Progress(active,store.history(),refresh){nav.navigate("challenges")}}
  composable("profile"){Profile({context.startActivity(Intent(context,MainActivity::class.java))},{context.startActivity(Intent(context,AppSelectionActivity::class.java))},{context.startActivity(Intent(context,SettingsActivity::class.java))},{nav.navigate("permissions")})}
   composable("permissions"){PermissionCenterScreen(permissionRefresh,permissionDenied,{if(!nav.popBackStack()) (context as? android.app.Activity)?.finish()},{selectedPermission=it})}
 }
 selected?.let{ChallengeSheet(it,active!=null,{selected=null},{val run=Run(it.id,System.currentTimeMillis(),emptySet());store.save(run);active=run;selected=null;nav.navigate("home"){popUpTo("home"){inclusive=true}}})}
 selectedPermission?.let { kind ->
  PermissionDisclosureSheet(kind,
   onDismiss={selectedPermission=null},
   onNotNow={permissionScope.launch{disclosureStore.markDismissed(kind.name)};selectedPermission=null},
   onContinue={
    selectedPermission=null
    if(kind==PermissionKind.NOTIFICATIONS && android.os.Build.VERSION.SDK_INT>=33){
     val prefs=context.getSharedPreferences("permission_disclosures",0)
     val asked=prefs.getBoolean("notification_requested",false)
     val activity=context as? android.app.Activity
     if(asked && activity!=null && !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity,android.Manifest.permission.POST_NOTIFICATIONS)){
      permissionPending=kind
      context.startActivity(permissionSettingsIntent(context,kind))
     }else{
      prefs.edit().putBoolean("notification_requested",true).apply()
      permissionPending=kind
      notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
     }
    }else{
     permissionPending=kind
     try { context.startActivity(permissionSettingsIntent(context,kind)) }
     catch(_:Exception){ ToastHost(context,"Open Android Settings to change this permission");permissionPending=null }
    }
   }
  )
 } 
  if(showDropConfirmation){
   AppBottomSheet(onDismiss={showDropConfirmation=false},containerColor=if(isDark)panel else white,footer={
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
     TextButton(onClick={showDropConfirmation=false},modifier=Modifier.weight(1f).heightIn(min=52.dp)){Text("Keep challenge",color=if(isDark)Color.White else ink)}
     Button(onClick={store.finish("Dropped");active=null;refresh++;showDropConfirmation=false},modifier=Modifier.weight(1f).heightIn(min=52.dp),shape=RoundedCornerShape(40.dp),colors=ButtonDefaults.buttonColors(containerColor=if(isDark)lime else ink,contentColor=if(isDark)dark else Color.White)){Text("Drop challenge")}
    }
   }){
    Text("Drop this challenge?",fontSize=21.sp,color=if(isDark)Color.White else ink)
    Text("Your current progress will be saved in your history. You can then choose another challenge.",fontSize=15.sp,color=if(isDark)Color(0xFFE8E9E3) else ink)
   }
  }

  AnimatedVisibility(visible=!keyboardOpen && !sheetShowing,enter=fadeIn(tween(250))+slideInVertically(tween(250)){it},exit=fadeOut(tween(250))+slideOutVertically(tween(250)){it},modifier=Modifier.align(Alignment.BottomCenter)){
   BottomPill(route,isDark){dest->nav.navigate(dest){popUpTo(nav.graph.findStartDestination().id){saveState=true};launchSingleTop=true;restoreState=true}}
  }
 }
}

@Composable private fun PermissionDisclosureSheet(kind:PermissionKind,onDismiss:()->Unit,onNotNow:()->Unit,onContinue:()->Unit){
 val isDark=isSystemInDarkTheme()
 AppBottomSheet(onDismiss=onDismiss,containerColor=if(isDark)dark else white,footer={
  Button(onClick=onContinue,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(40.dp),colors=ButtonDefaults.buttonColors(containerColor=if(isDark)lime else ink,contentColor=if(isDark)dark else Color.White)){Text("Continue")}
  TextButton(onClick=onNotNow,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Not now",color=if(isDark)Color.White else ink)}
 }){
  Column(verticalArrangement=Arrangement.spacedBy(14.dp)){
   Box(Modifier.size(72.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFFE8F7B7),lime,Color(0xFF829F38)))),contentAlignment=Alignment.Center){Icon(kind.icon,kind.title,tint=Color(0xFF202512),modifier=Modifier.size(30.dp))}
   Text(kind.title,fontSize=20.sp,fontWeight=FontWeight.Normal,color=if(isDark)Color.White else ink)
   Text(kind.why,fontSize=16.sp,color=if(isDark)Color(0xFFE8E9E3) else Color(0xFF22231F))
   Text("What you'll do",fontSize=14.sp,color=if(isDark)Color(0xFFB8B9B1) else muted)
   Text(kind.steps,fontSize=14.sp,color=if(isDark)Color.White else Color(0xFF22231F),lineHeight=21.sp)
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Icon(Icons.Rounded.Lock,"Privacy",tint=if(isDark)Color(0xFFB8B9B1) else muted,modifier=Modifier.size(16.dp));Text(kind.privacy,fontSize=12.sp,color=if(isDark)Color(0xFFB8B9B1) else muted)}
   if(kind==PermissionKind.USAGE || kind==PermissionKind.ACCESSIBILITY) Text("Android will show its own access warning. Review it before you continue.",fontSize=12.sp,color=if(isDark)Color(0xFFB8B9B1) else muted)
  }
 }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AppBottomSheet(onDismiss:()->Unit,containerColor:Color,footer:@Composable ColumnScope.()->Unit,content:@Composable ColumnScope.()->Unit){
 val state=rememberModalBottomSheetState(skipPartiallyExpanded=true)
 ModalBottomSheet(onDismissRequest=onDismiss,sheetState=state,containerColor=containerColor,scrimColor=Color.Black.copy(alpha=.40f),shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp),dragHandle={BottomSheetDefaults.DragHandle()},windowInsets=WindowInsets(0)){
  BoxWithConstraints(Modifier.fillMaxWidth()){
   Column(Modifier.fillMaxWidth().heightIn(max=maxHeight*.92f)){
    Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).padding(20.dp),content=content)
    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal=20.dp,vertical=12.dp),content=footer)
   }
  }
 }
}

private enum class PermissionKind(
 val title:String,val why:String,val steps:String,val privacy:String,val icon:androidx.compose.ui.graphics.vector.ImageVector
){
 OVERLAY("Display over other apps","Reel Focus uses a small floating timer over selected apps so you can see your session time without leaving them.","1. Tap Continue\n2. Choose Reel Focus\n3. Turn on Allow display over other apps","The overlay displays your timer. It does not read or record screen content.",Icons.Rounded.Layers),
 USAGE("Allow app usage access","Reel Focus checks which app is open so it can track time in the apps you choose and verify screen-time challenges.","1. Tap Continue\n2. Find Reel Focus in Usage access\n3. Turn on Permit usage access","Usage information is processed on your device and is not sent to Reel Focus servers.",Icons.Rounded.Visibility),
 ACCESSIBILITY("Enable reel detection","Reel Focus uses the Accessibility Service to identify reel-viewing screens in supported apps and apply your chosen boundaries.","1. Tap Continue\n2. Select Reel Focus\n3. Turn on the service and review Android's warning","Accessibility data is inspected on-device only to identify supported reel screens. Screen text is not saved or sent to a server.",Icons.Rounded.Accessibility),
 NOTIFICATIONS("Allow notifications","Reel Focus needs a notification to show when its monitoring foreground service is active and how to pause or resume it.","1. Tap Continue\n2. Choose Allow when Android asks","The notification shows monitoring status and controls. It does not contain your browsing or message content.",Icons.Rounded.Notifications),
 BATTERY("Allow background operation","Some Android devices restrict background activity aggressively. Exemption can help an active monitoring session continue reliably.","1. Tap Continue\n2. Find Reel Focus in the list\n3. Allow unrestricted battery use if you choose","This setting affects background operation. It does not grant access to personal content.",Icons.Rounded.BatterySaver)
}
private fun permissionGranted(context:Context,kind:PermissionKind):Boolean=try{
 when(kind){
  PermissionKind.OVERLAY -> android.os.Build.VERSION.SDK_INT<23 || Settings.canDrawOverlays(context)
  PermissionKind.USAGE -> hasUsagePermission(context)
  PermissionKind.ACCESSIBILITY -> {
   val enabled=Settings.Secure.getString(context.contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
   enabled.split(':').any{it.equals("${context.packageName}/${ReelDetectionAccessibilityService::class.java.name}",true)}
  }
  PermissionKind.NOTIFICATIONS -> android.os.Build.VERSION.SDK_INT<33 || androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
  PermissionKind.BATTERY -> android.os.Build.VERSION.SDK_INT<23 || (context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(context.packageName)
 }
}catch(_:Exception){false}
private fun permissionSettingsIntent(context:Context,kind:PermissionKind):Intent=when(kind){
 PermissionKind.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:${context.packageName}"))
 PermissionKind.USAGE -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
 PermissionKind.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
 PermissionKind.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName)
 PermissionKind.BATTERY -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
@Composable private fun PermissionCenterScreen(refresh:Int,denied:PermissionKind?,onBack:()->Unit,onSelect:(PermissionKind)->Unit){
 val context=LocalContext.current
 val isDark=isSystemInDarkTheme()
 Column(Modifier.fillMaxSize().background(if(isDark)Color(0xFF050505) else bgLight)){
  Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),verticalAlignment=Alignment.CenterVertically){
   IconButton(onClick=onBack){Icon(Icons.Rounded.ArrowBack,"Back",tint=if(isDark)Color.White else ink)}
   Text("Permissions",fontSize=20.sp,color=if(isDark)Color.White else ink)
  }
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal=16.dp).padding(bottom=96.dp)){
   Text("Your choices, clearly explained.",fontSize=25.sp,fontWeight=FontWeight.Light,color=if(isDark)Color.White else ink)
   Text("Reel Focus only asks when a feature needs access. You can change these settings at any time.",fontSize=14.sp,color=muted,modifier=Modifier.padding(top=6.dp,bottom=16.dp))
   if(denied!=null) Row(Modifier.fillMaxWidth().padding(bottom=12.dp).clip(RoundedCornerShape(18.dp)).background(if(isDark)Color(0xFF342B1B) else Color(0xFFFFF2D8)).padding(14.dp),verticalAlignment=Alignment.CenterVertically){
    Column(Modifier.weight(1f)){Text("Permission still off",fontSize=14.sp,color=if(isDark)Color.White else ink);Text(if(denied==PermissionKind.NOTIFICATIONS)"You can enable notifications in Android app settings." else "Reel Focus can continue with limited functionality.",fontSize=12.sp,color=muted)}
    Text(if(denied==PermissionKind.NOTIFICATIONS)"Open settings" else "Try again",fontSize=12.sp,color=if(isDark)lime else moss,modifier=Modifier.clickable{onSelect(denied)}.padding(6.dp))
   }
   PermissionKind.values().forEach{kind->
    val granted=permissionGranted(context,kind)
    Row(Modifier.fillMaxWidth().padding(bottom=9.dp).heightIn(min=56.dp).clip(RoundedCornerShape(20.dp)).background(if(isDark)panel else white).clickable{onSelect(kind)}.padding(horizontal=15.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
     Icon(kind.icon,null,tint=if(isDark)lime else moss,modifier=Modifier.size(23.dp))
     Column(Modifier.weight(1f).padding(start=12.dp)){
      Text(kind.title,fontSize=14.sp,color=if(isDark)Color.White else ink)
      Text(if(granted)"On" else "Off",fontSize=12.sp,color=if(granted)Color(0xFF5F9B42) else muted)
     }
     AnimatedVisibility(granted,enter=fadeIn(tween(200)),exit=fadeOut(tween(150))){
      Icon(Icons.Rounded.CheckCircle,"Permission on",tint=if(isDark)lime else moss,modifier=Modifier.size(20.dp))
     }
     Icon(Icons.Rounded.ChevronRight,null,tint=muted)
    }
   }
  }
 }
}

private fun ToastHost(ctx:Context,s:String){android.widget.Toast.makeText(ctx,s,android.widget.Toast.LENGTH_SHORT).show()}
private fun hasUsagePermission(ctx:Context):Boolean=try{
 val ops=ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
 @Suppress("DEPRECATION")
 val mode=if(android.os.Build.VERSION.SDK_INT>=29)ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),ctx.packageName) else ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),ctx.packageName)
 mode==AppOpsManager.MODE_ALLOWED
}catch(_:Exception){false}
private fun phoneMinutes(ctx:Context):Int{
 if(!hasUsagePermission(ctx))return 0
 val c=Calendar.getInstance();c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0)
 val manager=ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
 return (manager.queryAndAggregateUsageStats(c.timeInMillis,System.currentTimeMillis()).values.sumOf{it.totalTimeInForeground}/60000L).toInt()
}
@Composable private fun BottomPill(route:String,isDark:Boolean,onSelect:(String)->Unit){
 Surface(Modifier.fillMaxWidth().padding(horizontal=16.dp).navigationBarsPadding().padding(bottom=14.dp).height(64.dp),shape=RoundedCornerShape(32.dp),color=if(isDark)panel else dark,shadowElevation=0.dp){
  Row(Modifier.fillMaxSize().padding(6.dp),verticalAlignment=Alignment.CenterVertically){
  tabs.forEach{t->val chosen=route==t.route
   Row(Modifier.weight(1f).heightIn(min=48.dp).clip(RoundedCornerShape(30.dp)).background(if(chosen)lime else Color.Transparent).clickable{onSelect(t.route)}.padding(horizontal=2.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){
    Icon(t.icon,t.title,tint=if(chosen)Color(0xFF202512) else Color(0xFF9A9B92),modifier=Modifier.size(17.dp))
    Spacer(Modifier.width(3.dp));Text(t.title,fontSize=11.sp,color=if(chosen)Color(0xFF202512) else Color(0xFF9A9B92),maxLines=1)
   }
  }
 }
}
 }
@Composable private fun Header(title:String,isDark:Boolean){
 Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top=12.dp).padding(horizontal=16.dp).zIndex(1f).clip(RoundedCornerShape(32.dp)).background(if(isDark)panel else white).padding(horizontal=15.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
  Box(Modifier.size(32.dp).clip(CircleShape).background(if(isDark)lime else Color(0xFFE9EEDB)),contentAlignment=Alignment.Center){Icon(Icons.Rounded.Spa,"Wellness",tint=dark,modifier=Modifier.size(20.dp))}
  Text(title,Modifier.weight(1f),fontSize=17.sp,color=if(isDark)Color.White else ink,textAlign=TextAlign.Center)
  Icon(Icons.Rounded.MoreHoriz,"More options",tint=if(isDark)Color.White else ink,modifier=Modifier.size(24.dp))
 }
}
@Composable private fun Home(active:Run?,explore:()->Unit,open:(Challenge)->Unit,requestUsage:()->Unit,usageDenied:Boolean,retryUsage:()->Unit,continueWithout:()->Unit,serviceStopped:Boolean,dismissServiceStopped:()->Unit,check:()->Unit,complete:()->Unit,drop:()->Unit){
 val isDark=isSystemInDarkTheme();val fg=if(isDark)Color.White else ink
 Column(Modifier.fillMaxSize().background(if(isDark)Color(0xFF050505) else bgLight)){
  Header("Today",isDark)
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom=96.dp)){
   if(serviceStopped) Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp).clip(RoundedCornerShape(18.dp)).background(if(isDark)Color(0xFF342B1B) else Color(0xFFFFF2D8)).padding(14.dp),verticalAlignment=Alignment.CenterVertically){
    Icon(Icons.Rounded.PauseCircleOutline,null,tint=if(isDark)lime else Color(0xFF7A5A18))
    Column(Modifier.weight(1f).padding(start=10.dp)){Text("Monitoring needs attention",fontSize=14.sp,color=if(isDark)Color.White else ink);Text("Monitoring stopped or a required permission changed. Review access before starting again.",fontSize=12.sp,color=muted)}
    Text("Dismiss",fontSize=12.sp,color=if(isDark)lime else moss,modifier=Modifier.clickable{dismissServiceStopped()}.padding(6.dp))
   }
   DateStrip(isDark);Spacer(Modifier.height(12.dp));RecoveryCard(isDark){requestUsage()}
   if(usageDenied) Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
    Text("Permission still off · Time-limit checks are unavailable.",Modifier.weight(1f),fontSize=13.sp,color=muted)
    Text("Try again",Modifier.clickable{retryUsage()}.padding(8.dp),fontSize=12.sp,color=if(isDark)lime else moss)
    Text("Continue without",Modifier.clickable{continueWithout()}.padding(8.dp),fontSize=12.sp,color=muted)
   }
   Spacer(Modifier.height(20.dp))
   if(active!=null)ActiveCard(active,check,complete,drop)
   else Column(Modifier.padding(horizontal=20.dp)){Text("A little more life.",fontSize=28.sp,fontWeight=FontWeight.Light,color=fg);Text("A little less scroll.",fontSize=28.sp,fontWeight=FontWeight.Light,color=fg)}
   Spacer(Modifier.height(20.dp))
   Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically){Text("Your challenges",fontSize=20.sp,color=fg);Spacer(Modifier.weight(1f));Text("See all",fontSize=14.sp,color=if(isDark)lime else moss,modifier=Modifier.clickable{explore()})}
   Spacer(Modifier.height(12.dp));ChallengeRail(catalog.take(5),active!=null,open);Spacer(Modifier.height(20.dp));Tasks(isDark);Spacer(Modifier.height(24.dp))
  }
 }
}
@Composable private fun DateStrip(isDark:Boolean){
 Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=5.dp),horizontalArrangement=Arrangement.spacedBy(9.dp)){
  (0..6).forEach{i->val d=Calendar.getInstance().apply{add(Calendar.DAY_OF_YEAR,i-2)};val sel=i==2
   Column(Modifier.width(47.dp).clip(RoundedCornerShape(18.dp)).background(if(sel)lime else if(isDark)panel else white).padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally){
    Text(SimpleDateFormat("EEE",Locale.getDefault()).format(d.time).take(2),fontSize=11.sp,color=if(sel)dark else muted)
    Text(d.get(Calendar.DAY_OF_MONTH).toString(),fontSize=21.sp,fontWeight=FontWeight.Light,color=if(sel)dark else if(isDark)Color.White else ink)
    Row(horizontalArrangement=Arrangement.spacedBy(2.dp)){listOf(if(i%3==0)Color(0xFF8B5CF6) else Color.Transparent,if(i%2==0)Color(0xFFF472B6) else Color.Transparent,if(i==2)Color(0xFF84CC16) else Color.Transparent).forEach{Box(Modifier.size(4.dp).clip(CircleShape).background(it))}}
   }
  }
 }
}
@Composable private fun RecoveryCard(isDark:Boolean,onConnect:()->Unit){
 Row(Modifier.fillMaxWidth().padding(horizontal=16.dp).clip(RoundedCornerShape(28.dp)).background(if(isDark)Color(0xFF161714) else white).padding(20.dp),verticalAlignment=Alignment.CenterVertically){
  Column(Modifier.weight(1f)){Text("Phone time today",fontSize=14.sp,color=muted)
   val ctx=LocalContext.current
   val usageAllowed=hasUsagePermission(ctx)
   val minutes by produceState(initialValue=0,usageAllowed){value=if(usageAllowed)withContext(Dispatchers.IO){phoneMinutes(ctx)}else 0}
   if(usageAllowed){
    val value=if(minutes>=60)(minutes/60).toString()+"h "+(minutes%60).toString()+"m" else minutes.toString()+"m"
    Text(value,fontSize=42.sp,fontWeight=FontWeight.Light,color=if(isDark)Color.White else ink)
    Text("Recorded foreground app use",fontSize=12.sp,color=muted)
   }else{
    Text("Connect usage access",fontSize=21.sp,fontWeight=FontWeight.Light,color=if(isDark)Color.White else ink)
    Text("See your daily phone time and verify time-limit challenges.",fontSize=12.sp,color=muted)
    Text("Connect",fontSize=13.sp,color=if(isDark)lime else moss,modifier=Modifier.clickable{onConnect()}.padding(top=8.dp))
   }}
  Canvas(Modifier.size(104.dp)){drawCircle(lime.copy(alpha=.08f));drawCircle(lime.copy(alpha=.15f),radius=size.minDimension*.38f);drawArc(lime,-90f,309f,false,style=Stroke(5.dp.toPx(),cap=StrokeCap.Round));drawCircle(if(isDark)Color.White else ink,7.dp.toPx());drawCircle(lime,3.dp.toPx())}
 }
}
@Composable private fun Sparkline(modifier:Modifier,color:Color){Canvas(modifier){val vals=listOf(.2f,.55f,.4f,.65f,.45f,.82f,.72f,.9f);val path=Path();vals.forEachIndexed{i,v->val x=size.width*i/(vals.size-1);val y=size.height*(1-v*.7f);if(i==0)path.moveTo(x,y)else path.cubicTo(x-size.width*.04f,y,x-size.width*.04f,y,x,y)};drawPath(path,color,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))}}
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun ChallengeRail(list:List<Challenge>,hasActive:Boolean,open:(Challenge)->Unit){
 BoxWithConstraints(Modifier.fillMaxWidth()){
 val cardWidth=(maxWidth-54.dp).coerceIn(210.dp,320.dp)
 val state=rememberLazyListState()
 LazyRow(state=state,flingBehavior=rememberSnapFlingBehavior(lazyListState=state),modifier=Modifier.clipToBounds(),contentPadding=PaddingValues(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)){items(list,key={it.id},contentType={"challenge"}){c->ArtCard(c,Modifier.width(cardWidth).height(212.dp),hasActive){open(c)}}}
}
}
@Composable private fun ArtCard(c:Challenge,modifier:Modifier,hasActive:Boolean,onClick:()->Unit){
 Box(modifier.clip(RoundedCornerShape(28.dp)).background(panel).clickable{onClick()}){
  Orb(Modifier.align(Alignment.BottomEnd).size(190.dp))
  Column(Modifier.fillMaxSize().padding(20.dp)){
   Row{Text(c.tier,fontSize=11.sp,color=Color(0xFFB8B9B0));Spacer(Modifier.weight(1f));Text(pluralStringResource(R.plurals.day_count,c.days,c.days),fontSize=11.sp,color=Color(0xFFB8B9B0))}
   Spacer(Modifier.height(13.dp));Text(c.title,fontSize=21.sp,lineHeight=25.sp,color=Color.White,maxLines=2)
   Spacer(Modifier.weight(1f));Text(if(hasActive)"View challenge" else "Explore challenge",fontSize=12.sp,color=Color(0xFFD6D8CF))
   Spacer(Modifier.height(10.dp));Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha=.25f)));Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha=.15f)),contentAlignment=Alignment.Center){Icon(Icons.Rounded.ChevronRight,null,tint=Color.White)}}
  }
 }
}
@Composable private fun Orb(modifier:Modifier){Box(modifier.background(Brush.radialGradient(listOf(Color(0xFFD9FF72).copy(alpha=.78f),Color(0xFF9CCB2B).copy(alpha=.38f),Color.Transparent)))){Box(Modifier.fillMaxSize(.65f).align(Alignment.Center).background(Brush.radialGradient(listOf(Color(0xFFF0FFAE).copy(alpha=.62f),Color.Transparent)),CircleShape))}}
@Composable private fun ProgressBar(value:Float){val v by animateFloatAsState(value.coerceIn(0f,1f),label="progress");Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color(0xFFE2E3DC))){Box(Modifier.fillMaxWidth().fillMaxHeight().graphicsLayer{scaleX=v;transformOrigin=TransformOrigin(0f,.5f)}.background(lime))}}
@Composable private fun ActiveCard(r:Run,check:()->Unit,complete:()->Unit,drop:()->Unit){
 val c=catalog.firstOrNull{it.id==r.id}?:return;val day=((todayKey()-todayKey(r.started)).toInt()+1).coerceIn(1,c.days);val done=todayKey() in r.checks;val isDark=isSystemInDarkTheme()
 Column(Modifier.fillMaxWidth().padding(horizontal=16.dp).clip(RoundedCornerShape(28.dp)).background(if(isDark)panel else white).padding(20.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Text("Your active challenge",fontSize=12.sp,color=muted);Spacer(Modifier.weight(1f));Text("Active",fontSize=12.sp,color=if(isDark)lime else moss)}
  Spacer(Modifier.height(8.dp));Text(c.title,fontSize=22.sp,color=if(isDark)Color.White else ink);Spacer(Modifier.height(5.dp));Text("Day "+day+" of "+pluralStringResource(R.plurals.day_count,c.days,c.days)+"  ·  "+pluralStringResource(R.plurals.check_in_count,r.checks.size,r.checks.size),fontSize=13.sp,color=muted)
  Spacer(Modifier.height(12.dp));ProgressBar(r.checks.size.toFloat()/c.days);Spacer(Modifier.height(12.dp));Text(c.action,fontSize=14.sp,color=if(isDark)Color.White else ink)
  Spacer(Modifier.height(14.dp));if(done){OutlinedButton(onClick={},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(40.dp),border=BorderStroke(1.5.dp,lime),colors=ButtonDefaults.outlinedButtonColors(contentColor=if(isDark)Color.White else ink)){Icon(Icons.Rounded.CheckCircle,null,tint=if(isDark)lime else moss,modifier=Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Checked in today",fontSize=14.sp)}}else PrimaryButton("Finish today's session",true,check)
  if(r.checks.size>=c.days&&day>=c.days){Text("Complete challenge",Modifier.align(Alignment.CenterHorizontally).clickable{complete()}.padding(10.dp),color=if(isDark)lime else moss)}
  Text("Drop challenge",Modifier.align(Alignment.CenterHorizontally).clickable{drop()}.padding(10.dp),fontSize=12.sp,color=muted)
 }
}
@Composable private fun Tasks(isDark:Boolean){
 var done by remember{mutableStateOf(setOf<Int>())};val h= LocalHapticFeedback.current
 Column(Modifier.fillMaxWidth().padding(horizontal=16.dp).clip(RoundedCornerShape(28.dp)).background(if(isDark)panel else white).padding(18.dp)){
  Text("Today's small choices",fontSize=19.sp,color=if(isDark)Color.White else ink)
  listOf("Take a screen-free break","Spend a moment outdoors","Keep your phone away at a meal").forEachIndexed{i,s->
   Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable{done=if(i in done)done-i else done+i;h.performHapticFeedback(HapticFeedbackType.TextHandleMove)}.padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically){
    Box(Modifier.size(26.dp).border(1.dp,if(i in done)lime else Color(0xFFB8B9B1),CircleShape).background(if(i in done)lime else Color.Transparent,CircleShape),contentAlignment=Alignment.Center){if(i in done)Icon(Icons.Rounded.Check,"Completed",tint=dark)}
    Text(s,Modifier.padding(start=12.dp).weight(1f),fontSize=14.sp,color=if(i in done)muted else if(isDark)Color.White else ink,textDecoration=if(i in done)TextDecoration.LineThrough else TextDecoration.None)
   }
  }
 }
}
@Composable private fun Challenges(active:Run?,open:(Challenge)->Unit){
 var tier by remember{mutableStateOf("All")}
 val isDark=isSystemInDarkTheme()
 val visibleChallenges=remember(tier){catalog.filter{tier=="All"||it.tier==tier}}
 Column(Modifier.fillMaxSize().background(if(isDark)Color(0xFF050505) else bgLight)){
  Header("Challenges",isDark)
  LazyColumn(
   modifier=Modifier.fillMaxSize().navigationBarsPadding(),
   contentPadding=PaddingValues(start=16.dp,end=16.dp,top=12.dp,bottom=96.dp),
   verticalArrangement=Arrangement.spacedBy(11.dp)
  ){
   item(key="challenge-intro",contentType="header"){
    Column{
     Text("Choose your next practice",fontSize=28.sp,fontWeight=FontWeight.Light,color=if(isDark)Color.White else ink)
     Text("Every challenge is open to you. Start anywhere.",fontSize=14.sp,color=muted)
    }
   }
   item(key="challenge-filters",contentType="filters"){
    Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
     listOf("All","Gentle start","Build a rhythm","Deep reset").forEach{FilterChip(tier==it,{tier=it},{Text(it,fontSize=11.sp)},shape=RoundedCornerShape(13.dp))}
    }
   }
   items(visibleChallenges,key={it.id},contentType={"challenge"}){c->
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp)).background(if(isDark)panel else white).clickable{open(c)}.padding(15.dp),verticalAlignment=Alignment.CenterVertically){
     Box(Modifier.size(72.dp).clip(RoundedCornerShape(19.dp)).background(dark)){Orb(Modifier.fillMaxSize())}
     Column(Modifier.weight(1f).padding(start=13.dp)){Text(c.tier,fontSize=11.sp,color=muted);Text(c.title,fontSize=16.sp,color=if(isDark)Color.White else ink,maxLines=2);Text(pluralStringResource(R.plurals.day_count,c.days,c.days)+"  ·  "+c.category,fontSize=12.sp,color=muted)}
     Icon(Icons.Rounded.ChevronRight,null,tint=muted)
    }
   }
   if(active!=null)item(key="active-challenge-hint",contentType="hint"){Text("Finish or drop your active challenge before starting another.",fontSize=12.sp,color=muted)}
  }
 }
}
@Composable private fun Progress(active:Run?,history:JSONArray,refresh:Int,explore:()->Unit){
 val isDark=isSystemInDarkTheme()
 val historyIndices=remember(history.length(),refresh){(history.length()-1 downTo 0).toList()}
 Column(Modifier.fillMaxSize().background(if(isDark)Color(0xFF050505) else bgLight)){
  Header("Progress",isDark)
  LazyColumn(modifier=Modifier.fillMaxSize().navigationBarsPadding(),contentPadding=PaddingValues(start=16.dp,end=16.dp,top=16.dp,bottom=96.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   item(key="progress-summary",contentType="summary"){
    Column{
     Text("Your journey, at your pace.",fontSize=28.sp,fontWeight=FontWeight.Light,color=if(isDark)Color.White else ink)
     Spacer(Modifier.height(16.dp))
     Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Metric("Challenges",history.length().toString(),isDark,Modifier.weight(1f));Metric("Check-ins",countChecks(history).toString(),isDark,Modifier.weight(1f))}
     Spacer(Modifier.height(10.dp));Text("Recent practice",fontSize=19.sp,color=if(isDark)Color.White else ink)
    }
   }
   if(historyIndices.isEmpty()){
    item(key="progress-empty",contentType="empty"){
     Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(if(isDark)panel else white).padding(20.dp)){
      Text("Your progress starts with one choice.",fontSize=17.sp,color=if(isDark)Color.White else ink)
      Text("Completed and dropped challenges will appear here.",fontSize=13.sp,color=muted)
      Spacer(Modifier.height(14.dp));PrimaryButton("Start your first challenge",true,explore)
     }
    }
   }else{
    items(historyIndices,key={index->"history-$index"},contentType={"history"}){index->
     val item=history.getJSONObject(index)
     val c=catalog.firstOrNull{it.id==item.optString("id")}
     Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically){
      Icon(if(item.optString("result")=="Completed")Icons.Rounded.CheckCircle else Icons.Rounded.RemoveCircle,null,tint=if(item.optString("result")=="Completed")moss else muted,modifier=Modifier.size(28.dp))
      Column(Modifier.weight(1f).padding(start=12.dp)){Text(c?.title?:item.optString("id"),color=if(isDark)Color.White else ink);Text(pluralStringResource(R.plurals.check_in_count,item.optInt("checkIns"),item.optInt("checkIns")),fontSize=12.sp,color=muted)}
      Text(item.optString("result"),fontSize=12.sp,color=muted)
     }
    }
   }
  }
 }
}
private fun countChecks(a:JSONArray):Int{var n=0;for(i in 0 until a.length())n+=a.optJSONObject(i)?.optInt("checkIns")?:0;return n}
@Composable private fun Metric(label:String,value:String,darkMode:Boolean,modifier:Modifier){Column(modifier.clip(RoundedCornerShape(24.dp)).background(if(darkMode)panel else white).padding(18.dp)){Text(value,fontSize=42.sp,fontWeight=FontWeight.Light,color=if(darkMode)Color.White else ink);Text(label,fontSize=13.sp,color=muted)}}
@Composable private fun Profile(focus:()->Unit,apps:()->Unit,settings:()->Unit,permissions:()->Unit){
 val isDark=isSystemInDarkTheme();Column(Modifier.fillMaxSize().background(if(isDark)Color(0xFF050505) else bgLight)){Header("Profile",isDark);Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp).padding(bottom=96.dp)){
  Text("Your space.",fontSize=28.sp,fontWeight=FontWeight.Light,color=if(isDark)Color.White else ink);Spacer(Modifier.height(16.dp))
  ProfileRow("Focus controls","Set up your mindful app timer",Icons.Rounded.CenterFocusStrong,isDark,focus)
  ProfileRow("Choose apps","Select apps that deserve a pause",Icons.Rounded.Apps,isDark,apps)
  ProfileRow("Permissions","Review access and monitoring permissions",Icons.Rounded.Security,isDark,permissions)
  ProfileRow("App settings","Preferences and history",Icons.Rounded.Settings,isDark,settings)
 }}
}
@Composable private fun ProfileRow(title:String,sub:String,icon:androidx.compose.ui.graphics.vector.ImageVector,darkMode:Boolean,click:()->Unit){
 Row(Modifier.fillMaxWidth().padding(bottom=10.dp).clip(RoundedCornerShape(22.dp)).background(if(darkMode)panel else white).clickable{click()}.padding(16.dp),verticalAlignment=Alignment.CenterVertically){
  Icon(icon,null,tint=if(darkMode)lime else moss,modifier=Modifier.size(24.dp));Column(Modifier.weight(1f).padding(start=14.dp)){Text(title,color=if(darkMode)Color.White else ink);Text(sub,fontSize=12.sp,color=muted)};Icon(Icons.Rounded.ChevronRight,null,tint=muted)
 }
}
@Composable private fun PrimaryButton(label:String,enabled:Boolean=true,click:()->Unit){
 Button(onClick=click,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(40.dp),colors=ButtonDefaults.buttonColors(containerColor=ink,contentColor=Color.White,disabledContainerColor=Color(0xFFE1E2DB),disabledContentColor=muted)){Text(label,fontSize=14.sp)}
}
@Composable private fun ChallengeSheet(c:Challenge,hasActive:Boolean,dismiss:()->Unit,start:()->Unit){
 val isDark=isSystemInDarkTheme()
 AppBottomSheet(onDismiss=dismiss,containerColor=if(isDark)panel else white,footer={PrimaryButton(if(hasActive)"One active challenge at a time" else "Start challenge",!hasActive,start)}){
  Box(Modifier.fillMaxWidth().height(125.dp).clip(RoundedCornerShape(24.dp)).background(dark)){Orb(Modifier.fillMaxSize())}
  Spacer(Modifier.height(16.dp));Text(c.tier,fontSize=12.sp,color=muted);Text(c.title,fontSize=25.sp,color=if(isDark)Color.White else ink);Text(pluralStringResource(R.plurals.day_count,c.days,c.days)+"  ·  "+c.category,fontSize=13.sp,color=muted)
  Spacer(Modifier.height(14.dp));Text(c.summary,fontSize=15.sp,color=if(isDark)Color.White else ink);Spacer(Modifier.height(13.dp));Text("Your practice",fontSize=13.sp,color=muted);Text(c.action,fontSize=14.sp,color=if(isDark)Color.White else ink);Spacer(Modifier.height(12.dp));Text("Why it helps",fontSize=13.sp,color=muted);Text(c.benefit,fontSize=14.sp,color=if(isDark)Color.White else ink)
  if(hasActive)Text("Complete or drop your current challenge to start this one.",fontSize=12.sp,color=muted,modifier=Modifier.padding(top=8.dp))
 }
}