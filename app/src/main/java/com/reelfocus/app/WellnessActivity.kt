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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import com.reelfocus.app.models.AppConfig
import com.reelfocus.app.models.OverlayPosition
import com.reelfocus.app.models.OverlayStyle
import com.reelfocus.app.utils.PreferencesHelper

private val bgLight=Color(0xFFF4F4F2); private val white=Color.White; private val ink=Color(0xFF111111)
private val muted=Color(0xFF777873); private val dark=Color(0xFF0E0F0C); private val panel=Color(0xFF1B1D17)
private val lime=Color(0xFFC6E36B); private val moss=Color(0xFF5F7D1C)
@Immutable private data class Challenge(val id:String,val title:String,val category:String,val tier:String,val days:Int,val summary:String,val action:String,val benefit:String,val limit:Int?=null,val shortDescription:String=summary)
private val catalog=listOf(
 Challenge("screen_5h","A five-hour phone day","Digital balance","Gentle start",1,"Keep total phone use to five hours or less today.","Set an intention, then check Android usage tonight.","Make room for the parts of your day that happen offline.",300,shortDescription="Keep your total phone use below five hours and review your screen time."),
 Challenge("morning_60","First hour, fully yours","Mindful mornings","Gentle start",3,"Spend the first hour after waking without your phone.","Leave your phone outside the bedroom. Begin with water, daylight or movement.","Start the day on your terms.",shortDescription="Give the first hour after waking to yourself instead of reaching for your phone."),
 Challenge("phone_meals","Meals without a screen","Presence","Gentle start",7,"Keep your phone away during one meal each day.","Put your phone out of reach before you sit down to eat.","Give food and company your full attention.",shortDescription="Leave your phone aside for one meal each day and give the moment your attention."),
 Challenge("notification_pause","Quiet the noise","Digital calm","Gentle start",1,"Turn off non-essential notifications for one day.","Keep calls and essential alerts; silence the rest.","Reduce interruptions.",shortDescription="Silence non-essential alerts for a day and notice what no longer interrupts you."),
 Challenge("social_uninstall","A week away from social feeds","Digital boundaries","Build a rhythm",7,"Take seven days away from the social apps you choose.","Uninstall chosen apps yourself and reflect in your daily check-in.","Create distance from habitual scrolling.",shortDescription="Step away from the social feeds you choose for seven full days, without deleting accounts."),
 Challenge("screen_3h","A three-hour phone day","Digital balance","Build a rhythm",7,"Aim for three hours or less of phone use each day.","Check your usage and plan two phone-free blocks.","Build a steadier relationship with your device.",180,shortDescription="Keep daily phone use below three hours and plan the moments you need it."),
 Challenge("no_reels","No short-form scroll","Digital boundaries","Build a rhythm",7,"Take seven days away from short-video feeds.","Remove shortcuts, sign out or uninstall chosen apps.","Break the automatic scroll loop.",shortDescription="Take a full week away from short-form video feeds and automatic scrolling."),
 Challenge("focus_blocks","Two phone-free focus blocks","Attention","Build a rhythm",5,"Create two 45-minute phone-free blocks daily.","Keep your phone in another room and choose one task.","Make space for deeper attention.",shortDescription="Protect two daily 45-minute blocks from notifications and phone distractions."),
 Challenge("sleep_boundary","A calmer final hour","Rest","Build a rhythm",7,"Keep the final hour before sleep screen-free.","Charge your phone away from your bed.","Give your mind a gentler transition.",shortDescription="Keep the final hour before bed screen-free to make the transition to sleep calmer."),
 Challenge("screen_2h","A two-hour digital reset","Digital boundaries","Deep reset",7,"Keep total phone use to two hours or less daily.","Plan essential use and review your total without judgment.","Use your phone with intention.",120,shortDescription="Keep total phone use below two hours daily and make a deliberate reset."),
 Challenge("social_14","Fourteen days off social media","Digital boundaries","Deep reset",14,"Take a two-week break from social apps you select.","Uninstall or log out; keep important contacts reachable.","Reset habits that pull your attention back.",shortDescription="Take a two-week break from social apps while keeping essential contacts reachable."),
 Challenge("offline_day","One full offline day","Presence","Deep reset",1,"Take one day away from non-essential phone use.","Tell important contacts how to reach you in an emergency.","Experience a day with fewer digital demands.",shortDescription="Spend one full day away from non-essential phone use and digital noise.")
)
@Immutable private data class Run(val id:String,val started:Long,val checks:Set<Long>,val paused:Boolean=false,val targetDays:Int=0)
private fun todayKey(t:Long=System.currentTimeMillis()):Long { val c=Calendar.getInstance();c.timeInMillis=t;c.set(11,0);c.set(12,0);c.set(13,0);c.set(14,0);return c.timeInMillis/86400000L }
private class Store(ctx:Context) {
 private val p=ctx.getSharedPreferences("wellness_challenges",0)
 fun active():Run? { return try { val raw=p.getString("active_challenge",null)?:return null;val j=JSONObject(raw);val a=j.optJSONArray("checkIns")?:JSONArray();Run(j.getString("id"),j.getLong("startedAt"),(0 until a.length()).map{a.getLong(it)}.toSet(),j.optBoolean("paused",false),j.optInt("targetDays",0)) }catch(_:Exception){null} }
 fun save(r:Run){val a=JSONArray();r.checks.sorted().forEach{a.put(it)};p.edit().putString("active_challenge",JSONObject().put("id",r.id).put("startedAt",r.started).put("checkIns",a).put("paused",r.paused).put("targetDays",r.targetDays).toString()).apply()}
 fun finish(result:String){val r=active()?:return;val h=JSONArray(p.getString("challenge_history","[]")?: "[]");h.put(JSONObject().put("id",r.id).put("startedAt",r.started).put("endedAt",System.currentTimeMillis()).put("targetDays",r.targetDays.takeIf{it>0}?:catalog.firstOrNull{it.id==r.id}?.days?:0).put("result",result).put("checkIns",r.checks.size));p.edit().putString("challenge_history",h.toString()).remove("active_challenge").apply()}
 fun history()=JSONArray(p.getString("challenge_history","[]")?: "[]")
}
class WellnessActivity:ComponentActivity(){
 var permissionRefresh by mutableIntStateOf(0)
 private var jankStats:androidx.metrics.performance.JankStats?=null
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);enableEdgeToEdge(statusBarStyle=SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT));androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,false);jankStats=androidx.metrics.performance.JankStats.createAndTrack(window){frameData->if(frameData.isJank)android.util.Log.w("ReelFocusJank","Janky frame: ${frameData.frameDurationUiNanos/1_000_000} ms")};setContent{AppTheme{WellnessApp(intent.getStringExtra("permission_to_explain"),intent.getBooleanExtra("open_focus_controls",false))}}}
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
private val tabs=listOf(Tab("home","Today",Icons.Rounded.Home),Tab("challenges","Challenges",Icons.Rounded.Explore),Tab("progress","Progress",Icons.Rounded.Insights),Tab("profile","Settings",Icons.Rounded.Settings))
@Composable private fun WellnessApp(initialPermission:String?=null,openFocusControls:Boolean=false){
 val context= LocalContext.current
 val permissionRefresh=(context as? WellnessActivity)?.permissionRefresh ?: 0
 val store= remember{Store(context)};val nav= rememberNavController()
 val disclosureStore=remember{PermissionDisclosureStore(context)}
 val permissionScope=rememberCoroutineScope()
 var active by remember{mutableStateOf(store.active())};var selected by remember{mutableStateOf<Challenge?>(null)};var refresh by remember{mutableIntStateOf(0)};var showDropConfirmation by remember{mutableStateOf(false)}
 var showFocusSheet by remember{mutableStateOf(openFocusControls)}
 var onboardingComplete by remember{mutableStateOf(context.getSharedPreferences("first_run",0).getBoolean("onboarding_complete",false))}
 var focusConfig by remember{mutableStateOf(PreferencesHelper(context).loadConfig())}
 var monitoringOn by remember{mutableStateOf(OverlayService.isRunning)}
 var selectedPermission by remember{mutableStateOf(initialPermission?.let{runCatching{PermissionKind.valueOf(it)}.getOrNull()})}
 var permissionPending by remember{mutableStateOf<PermissionKind?>(null)}
 var permissionDenied by remember{mutableStateOf<PermissionKind?>(null)}
 val permissionHaptics=LocalHapticFeedback.current
 var serviceStopped by remember{mutableStateOf(context.getSharedPreferences("service_status",0).getBoolean("monitoring_expected",false) && (!OverlayService.isRunning || !permissionGranted(context,PermissionKind.OVERLAY) || !permissionGranted(context,PermissionKind.USAGE)))}
 LaunchedEffect(permissionRefresh){
  monitoringOn=OverlayService.isRunning
  focusConfig=PreferencesHelper(context).loadConfig()
  serviceStopped=context.getSharedPreferences("service_status",0).getBoolean("monitoring_expected",false) && (!OverlayService.isRunning || !permissionGranted(context,PermissionKind.OVERLAY) || !permissionGranted(context,PermissionKind.USAGE))
 }
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
  val sheetShowing=selected!=null || selectedPermission!=null || showDropConfirmation || showFocusSheet
  Box(Modifier.fillMaxSize().background(if(isDark)Color(0xFF050505) else bgLight)){
  val reduceMotion=android.provider.Settings.Global.getFloat(context.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
 NavHost(nav,if(initialPermission!=null)"permissions" else if(onboardingComplete)"home" else "onboarding",Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
  enterTransition={if(reduceMotion) androidx.compose.animation.EnterTransition.None else fadeIn(tween(280))+slideInVertically(tween(280)){it/18}},
  exitTransition={if(reduceMotion) androidx.compose.animation.ExitTransition.None else fadeOut(tween(180))}
 ){
  composable("onboarding"){
   OnboardingScreen(onComplete={
    context.getSharedPreferences("first_run",0).edit().putBoolean("onboarding_complete",true).apply()
    onboardingComplete=true
    nav.navigate("home"){popUpTo("onboarding"){inclusive=true};launchSingleTop=true}
   })
  }
  composable("home"){
   Home(
    active=active,
    explore={nav.navigate("challenges")},
    open={selected=it},
    requestUsage={selectedPermission=PermissionKind.USAGE},
    usageDenied=permissionDenied==PermissionKind.USAGE,
    retryUsage={selectedPermission=PermissionKind.USAGE;permissionDenied=null},
    continueWithout={permissionDenied=null},
    serviceStopped=serviceStopped,
    dismissServiceStopped={context.getSharedPreferences("service_status",0).edit().putBoolean("monitoring_expected",false).apply();serviceStopped=false},
    check={
     val a=active
     val ch=catalog.firstOrNull{it.id==a?.id}
     if(a!=null && a.paused){ToastHost(context,"Resume your challenge to check in")}
     else if(a!=null && ch?.limit!=null){
      if(!hasUsagePermission(context)){selectedPermission=PermissionKind.USAGE}
      else{permissionScope.launch{
       val minutes=withContext(Dispatchers.IO){phoneMinutes(context)}
       if(minutes>ch.limit)ToastHost(context,"Today's phone-time limit has been exceeded")
       else{val updated=a.copy(checks=a.checks+todayKey());active=updated;store.save(updated);ToastHost(context,"Check-in saved")}
      }}
     }else if(a!=null){active=a.copy(checks=a.checks+todayKey());store.save(active!!);ToastHost(context,"Check-in saved")}
    },
    complete={store.finish("Completed");active=null;refresh++},
    drop={showDropConfirmation=true},
    pause={active?.let{val next=it.copy(paused=!it.paused);active=next;store.save(next)}},
    easier={active?.let{run->val base=run.targetDays.takeIf{it>0}?:catalog.firstOrNull{it.id==run.id}?.days?:1;val next=run.copy(targetDays=(base-2).coerceAtLeast(1));active=next;store.save(next);ToastHost(context,"Challenge target adjusted to ${next.targetDays} days")}},
    monitoringOn=monitoringOn,
    monitoredAppCount=focusConfig.monitoredApps.count{it.isEnabled},
    sessionLimitMinutes=focusConfig.defaultLimitValue,
    requiredPermissionsMissing=(!permissionGranted(context,PermissionKind.USAGE) || !permissionGranted(context,PermissionKind.OVERLAY)),
    missingRequiredPermissionCount=listOf(PermissionKind.USAGE,PermissionKind.OVERLAY).count{!permissionGranted(context,it)},
    onToggleMonitoring={
     if(monitoringOn || OverlayService.isRunning){
      context.startService(Intent(context,OverlayService::class.java).setAction(OverlayService.ACTION_STOP))
      monitoringOn=false
      serviceStopped=false
     }else{
      // Re-read configuration and every required capability at tap time.
      focusConfig=PreferencesHelper(context).loadConfig()
      val preflight=FocusStartPreflight.evaluate(context,focusConfig)
      when{
       StartRequirement.UsageAccess in preflight.missingRequirements -> selectedPermission=PermissionKind.USAGE
       StartRequirement.OverlayPermission in preflight.missingRequirements -> selectedPermission=PermissionKind.OVERLAY
       StartRequirement.NoAppsSelected in preflight.missingRequirements -> {showFocusSheet=true;ToastHost(context,"Choose at least one app to monitor")}
       StartRequirement.SelectedAppsUnavailable in preflight.missingRequirements -> {
        ToastHost(context,"A selected app is no longer installed. Review your app selection.")
        context.startActivity(Intent(context,AppSelectionActivity::class.java))
       }
       preflight.canStart -> {
        val intent=Intent(context,OverlayService::class.java).setAction(OverlayService.ACTION_START)
        try{
         if(android.os.Build.VERSION.SDK_INT>=26)context.startForegroundService(intent) else context.startService(intent)
         monitoringOn=true
         serviceStopped=false
         preflight.basicModeMessage()?.let{ToastHost(context,it)}
         if(!preflight.notificationsAvailable)ToastHost(context,"Notifications are off; monitoring can still run, but status alerts may be unavailable.")
        }catch(_:SecurityException){
         monitoringOn=false
         ToastHost(context,"Android blocked monitoring from starting. Open Reel Focus and try again.")
        }catch(e:android.app.ForegroundServiceStartNotAllowedException){
         monitoringOn=false
         ToastHost(context,"Android did not allow the background service to start. Open Reel Focus and try again.")
        }
       }
      }
     }
    },
    openFocus={showFocusSheet=true},
    openPermissions={nav.navigate("permissions")}
   )
  }
  composable("challenges"){Challenges(active){selected=it}}
  composable("progress"){Progress(active,store.history(),refresh){nav.navigate("challenges")}}
  composable("profile"){Profile(
   apps={context.startActivity(Intent(context,AppSelectionActivity::class.java))},
   permissions={nav.navigate("permissions")},
   notifications={context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))},
   about={context.startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://amargm.github.io/reel-focus-android/privacy")))}
  )}
   composable("permissions"){PermissionCenterScreen(permissionRefresh,permissionDenied,{if(!nav.popBackStack()) (context as? android.app.Activity)?.finish()},{selectedPermission=it})}
 }
 selected?.let{ChallengeSheet(it,active!=null,{selected=null},{val run=Run(it.id,System.currentTimeMillis(),emptySet());store.save(run);active=run;selected=null;nav.navigate("home"){popUpTo("home"){inclusive=true}}})}
 if(showFocusSheet){
  FocusSheet(
   config=focusConfig,
   monitoringOn=monitoringOn,
   monitoredAppCount=focusConfig.monitoredApps.count{it.isEnabled},
   onConfigChange={next->focusConfig=next;PreferencesHelper(context).saveConfig(next)},
   onDismiss={showFocusSheet=false},
   onChooseApps={showFocusSheet=false;context.startActivity(Intent(context,AppSelectionActivity::class.java))}
  )
 }
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

  AnimatedVisibility(visible=route!="onboarding" && !keyboardOpen && !sheetShowing,enter=fadeIn(tween(250))+slideInVertically(tween(250)){it},exit=fadeOut(tween(250))+slideOutVertically(tween(250)){it},modifier=Modifier.align(Alignment.BottomCenter)){
   BottomPill(route,isDark){dest->nav.navigate(dest){popUpTo(nav.graph.findStartDestination().id){saveState=true};launchSingleTop=true;restoreState=true}}
  }
 }
}

@Composable private fun FocusSheet(
 config:AppConfig,monitoringOn:Boolean,monitoredAppCount:Int,
 onConfigChange:(AppConfig)->Unit,onDismiss:()->Unit,onChooseApps:()->Unit
){
 val isDark=isSystemInDarkTheme()
 var limit by remember(config.defaultLimitValue){mutableFloatStateOf(config.defaultLimitValue.toFloat())}
 var sessions by remember(config.maxSessionsDaily){mutableFloatStateOf(config.maxSessionsDaily.toFloat())}
 var resetGap by remember(config.sessionResetGapMinutes){mutableFloatStateOf(config.sessionResetGapMinutes.toFloat())}
 AppBottomSheet(onDismiss=onDismiss,containerColor=if(isDark)panel else white,footer={
  PrimaryButton("Done",true,onDismiss)
 }){
  Column(verticalArrangement=Arrangement.spacedBy(14.dp)){
   Text("Focus controls",fontSize=23.sp,color=if(isDark)Color.White else ink)
   Text(if(monitoringOn)"Monitoring is on for your selected apps." else "Set up how Reel Focus helps you pause in selected apps.",fontSize=14.sp,color=muted)
   Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if(isDark)dark else bgLight).clickable{onChooseApps()}.padding(15.dp),verticalAlignment=Alignment.CenterVertically){
    Icon(Icons.Rounded.Apps,null,tint=if(isDark)lime else moss,modifier=Modifier.size(23.dp))
    Column(Modifier.weight(1f).padding(start=12.dp)){
     Text("Choose apps",fontSize=14.sp,color=if(isDark)Color.White else ink)
     Text("$monitoredAppCount ${if(monitoredAppCount==1)"app" else "apps"} selected",fontSize=12.sp,color=muted)
    }
    Icon(Icons.Rounded.ChevronRight,null,tint=muted)
   }
   Text("Session length",fontSize=15.sp,color=if(isDark)Color.White else ink)
   Text("${limit.toInt()} minutes per session",fontSize=13.sp,color=muted)
   Slider(value=limit,onValueChange={limit=it},valueRange=5f..60f,steps=54,onValueChangeFinished={onConfigChange(config.copy(defaultLimitValue=limit.toInt()))})
   Text("Sessions per day",fontSize=15.sp,color=if(isDark)Color.White else ink)
   Text("${sessions.toInt()} sessions",fontSize=13.sp,color=muted)
   Slider(value=sessions,onValueChange={sessions=it},valueRange=1f..20f,steps=18,onValueChangeFinished={onConfigChange(config.copy(maxSessionsDaily=sessions.toInt()))})
   Text("Time away between sessions",fontSize=15.sp,color=if(isDark)Color.White else ink)
   Text("${resetGap.toInt()} minutes",fontSize=13.sp,color=muted)
   Slider(value=resetGap,onValueChange={resetGap=it},valueRange=5f..120f,steps=22,onValueChangeFinished={onConfigChange(config.copy(sessionResetGapMinutes=resetGap.toInt()))})
   Divider(color=if(isDark)Color.White.copy(alpha=.12f) else Color(0xFFE5E6E0))
   Text("Floating timer style",fontSize=15.sp,color=if(isDark)Color.White else ink)
   Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
    listOf(OverlayStyle.TEXT,OverlayStyle.DONUT,OverlayStyle.SHRINKING_CIRCLE).forEach{style->
     FilterChip(selected=config.overlayStyle==style,onClick={onConfigChange(config.copy(overlayStyle=style))},label={Text(when(style){OverlayStyle.TEXT->"Text";OverlayStyle.DONUT->"Ring";else->"Circle"},fontSize=12.sp)})
    }
   }
   Text("Timer position",fontSize=15.sp,color=if(isDark)Color.White else ink)
   Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
    listOf(OverlayPosition.TOP_LEFT,OverlayPosition.TOP_RIGHT,OverlayPosition.CENTER_RIGHT,OverlayPosition.BOTTOM_LEFT,OverlayPosition.BOTTOM_RIGHT).chunked(3).forEach{group->
     Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
      group.forEach{position->
       FilterChip(selected=config.overlayPosition==position,onClick={onConfigChange(config.copy(overlayPosition=position))},label={Text(position.displayName,fontSize=11.sp)})
      }
     }
    }
   }
   Text("Changes are saved on this device.",fontSize=12.sp,color=muted)
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
 val permissionStates=remember(refresh){PermissionKind.values().associateWith{permissionGranted(context,it)}}
 val coreReady=permissionStates[PermissionKind.USAGE]==true && permissionStates[PermissionKind.OVERLAY]==true
 Column(Modifier.fillMaxSize().background(if(isDark)Color(0xFF050505) else bgLight)){
  Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),verticalAlignment=Alignment.CenterVertically){
   IconButton(onClick=onBack){Icon(Icons.Rounded.ArrowBack,"Back",tint=if(isDark)Color.White else ink)}
   Text("Permissions",fontSize=20.sp,color=if(isDark)Color.White else ink)
  }
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal=16.dp).padding(bottom=98.dp)){
   Text("Your choices, clearly explained.",fontSize=25.sp,fontWeight=FontWeight.Light,color=if(isDark)Color.White else ink)
   Text("Reel Focus only asks when a feature needs access. You can change these settings at any time.",fontSize=14.sp,color=muted,modifier=Modifier.padding(top=6.dp,bottom=16.dp))
   Column(Modifier.fillMaxWidth().padding(bottom=14.dp).clip(RoundedCornerShape(20.dp)).background(if(isDark)panel else white).padding(15.dp)){
    Text(if(coreReady)"Core monitoring is ready" else "Core access needs attention",fontSize=15.sp,color=if(isDark)Color.White else ink)
    Text(if(coreReady)"Required access is available. Notifications and enhanced detection remain optional." else "Usage access and floating timer access are required for monitoring. Notifications, reel detection and background operation are optional; basic mode works without reel detection.",fontSize=12.sp,color=muted,modifier=Modifier.padding(top=4.dp))
   }
   if(denied!=null) Row(Modifier.fillMaxWidth().padding(bottom=12.dp).clip(RoundedCornerShape(18.dp)).background(if(isDark)Color(0xFF342B1B) else Color(0xFFFFF2D8)).padding(14.dp),verticalAlignment=Alignment.CenterVertically){
    Column(Modifier.weight(1f)){Text("Permission still off",fontSize=14.sp,color=if(isDark)Color.White else ink);Text(if(denied==PermissionKind.NOTIFICATIONS)"You can enable notifications in Android app settings." else "Reel Focus can continue with limited functionality.",fontSize=12.sp,color=muted)}
    Text(if(denied==PermissionKind.NOTIFICATIONS)"Open settings" else "Try again",fontSize=12.sp,color=if(isDark)lime else moss,modifier=Modifier.clickable{onSelect(denied)}.padding(6.dp))
   }
   PermissionKind.values().forEach{kind->
    val granted=permissionStates[kind]==true
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


@Composable private fun OnboardingScreen(onComplete:()->Unit){
 val isDark=isSystemInDarkTheme()
 val pages=listOf(
  Triple("A little less scroll. A little more life.","Reel Focus helps you create breathing room around the apps that pull you into short-form feeds.","You choose the apps and the boundaries."),
  Triple("Your focus, your rules.","Set a session length and a gentle break between sessions. A small floating timer helps you stay aware while using selected apps.","Nothing starts monitoring until you choose to start it."),
  Triple("You stay in control.","Android permissions are explained when a feature needs them. You can explore challenges and daily phone-time information without enabling focus monitoring.","Usage access and overlay access power monitoring. Notifications keep an active service visible. Reel detection is optional.")
 )
 var page by remember{mutableIntStateOf(0)}
 val fg=if(isDark)Color.White else ink
 Column(Modifier.fillMaxSize().background(if(isDark)Color(0xFF050505) else bgLight).statusBarsPadding().navigationBarsPadding().padding(horizontal=22.dp, vertical=18.dp)){
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
   Text("REEL FOCUS",fontSize=12.sp,letterSpacing=2.sp,color=if(isDark)lime else moss)
   Spacer(Modifier.weight(1f))
   Text("Skip",Modifier.clickable{onComplete()}.padding(10.dp),fontSize=13.sp,color=muted)
  }
  Spacer(Modifier.weight(1f))
  Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(34.dp)).background(if(isDark)panel else white),contentAlignment=Alignment.Center){
   Box(Modifier.size(142.dp).background(Brush.radialGradient(listOf(lime.copy(alpha=.72f),lime.copy(alpha=.18f),Color.Transparent)),CircleShape))
   Icon(listOf(Icons.Rounded.Spa,Icons.Rounded.Tune,Icons.Rounded.Security)[page],null,tint=if(isDark)lime else moss,modifier=Modifier.size(54.dp))
  }
  Spacer(Modifier.height(34.dp))
  Text("0${page+1} / 03",fontSize=12.sp,color=muted)
  Spacer(Modifier.height(10.dp))
  Text(pages[page].first,fontSize=30.sp,lineHeight=36.sp,fontWeight=FontWeight.Light,color=fg)
  Spacer(Modifier.height(12.dp))
  Text(pages[page].second,fontSize=16.sp,lineHeight=24.sp,color=if(isDark)Color(0xFFE3E4DD) else Color(0xFF42433E))
  Spacer(Modifier.height(12.dp))
  Text(pages[page].third,fontSize=13.sp,lineHeight=20.sp,color=muted)
  Spacer(Modifier.weight(1f))
  Row(horizontalArrangement=Arrangement.spacedBy(7.dp),modifier=Modifier.padding(bottom=18.dp)){
   repeat(pages.size){i->Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if(i<=page)lime else if(isDark)Color(0xFF454740) else Color(0xFFD8D9D2)))}
  }
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){
   if(page>0) OutlinedButton(onClick={page--},modifier=Modifier.weight(1f).heightIn(min=54.dp),shape=RoundedCornerShape(40.dp)){Text("Back")}
   Button(onClick={if(page==pages.lastIndex)onComplete() else page++},modifier=Modifier.weight(1f).heightIn(min=54.dp),shape=RoundedCornerShape(40.dp),colors=ButtonDefaults.buttonColors(containerColor=if(isDark)lime else ink,contentColor=if(isDark)dark else Color.White)){
    Text(if(page==pages.lastIndex)"Continue to Today" else "Next")
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
 val fontScale=LocalDensity.current.fontScale
 Surface(Modifier.fillMaxWidth().padding(horizontal=16.dp).navigationBarsPadding().padding(bottom=14.dp).height(68.dp),shape=RoundedCornerShape(32.dp),color=Color(0xFF262A1F),border=BorderStroke(1.dp,Color(0xFF3A3E30)),shadowElevation=0.dp){
  Row(Modifier.fillMaxSize().padding(6.dp),verticalAlignment=Alignment.CenterVertically){
   tabs.forEach{t->val chosen=route==t.route
    Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(30.dp)).background(if(chosen)lime else Color.Transparent).semantics(mergeDescendants=true){role=Role.Tab;selected=chosen}.clickable(role=Role.Tab,onClick={onSelect(t.route)}).padding(horizontal=if(chosen)4.dp else 2.dp,vertical=4.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
     Icon(t.icon,t.title,tint=if(chosen)Color(0xFF202512) else Color(0xFFA9AA9F),modifier=Modifier.size(24.dp))
     if(chosen || fontScale<=1.3f){Spacer(Modifier.height(4.dp));Text(t.title,Modifier.graphicsLayer{if(chosen && fontScale>1.3f)scaleX=(1f/fontScale).coerceAtLeast(.5f)},fontSize=12.sp,color=if(chosen)Color(0xFF202512) else Color(0xFFA9AA9F),maxLines=1,softWrap=false)}
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
@Composable private fun Home(
 active:Run?,explore:()->Unit,open:(Challenge)->Unit,requestUsage:()->Unit,usageDenied:Boolean,
 retryUsage:()->Unit,continueWithout:()->Unit,serviceStopped:Boolean,dismissServiceStopped:()->Unit,
 check:()->Unit,complete:()->Unit,drop:()->Unit,pause:()->Unit,easier:()->Unit,monitoringOn:Boolean,monitoredAppCount:Int,
 sessionLimitMinutes:Int,requiredPermissionsMissing:Boolean,missingRequiredPermissionCount:Int,onToggleMonitoring:()->Unit,openFocus:()->Unit,openPermissions:()->Unit
){
 val isDark=isSystemInDarkTheme();val fg=if(isDark)Color.White else ink
 Column(Modifier.fillMaxSize().background(if(isDark)Color(0xFF050505) else bgLight)){
  Header("Today",isDark)
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(top=12.dp,bottom=98.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
   if(serviceStopped) Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp).clip(RoundedCornerShape(18.dp)).background(if(isDark)Color(0xFF342B1B) else Color(0xFFFFF2D8)).padding(14.dp),verticalAlignment=Alignment.CenterVertically){
    Icon(Icons.Rounded.PauseCircleOutline,null,tint=if(isDark)lime else Color(0xFF7A5A18))
    Column(Modifier.weight(1f).padding(start=10.dp)){Text("Monitoring needs attention",fontSize=14.sp,color=if(isDark)Color.White else ink);Text("Monitoring stopped or a required permission changed. Review access before starting again.",fontSize=12.sp,color=muted)}
    Text("Fix",fontSize=12.sp,color=if(isDark)lime else moss,modifier=Modifier.clickable{openPermissions()}.padding(6.dp))
   }
   FocusStatusCard(monitoringOn,monitoredAppCount,sessionLimitMinutes,requiredPermissionsMissing,missingRequiredPermissionCount,onToggleMonitoring,openFocus)
   RecoveryCard(isDark){requestUsage()}
   if(usageDenied) Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
    Text("Permission still off · Time-limit checks are unavailable.",Modifier.weight(1f),fontSize=13.sp,color=muted)
    Text("Try again",Modifier.clickable{retryUsage()}.padding(8.dp),fontSize=12.sp,color=if(isDark)lime else moss)
    Text("Continue without",Modifier.clickable{continueWithout()}.padding(8.dp),fontSize=12.sp,color=muted)
   }
   if(active!=null)ActiveCard(active,check,complete,drop,pause,easier)
   Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically){Text("Your challenges",fontSize=20.sp,color=fg);Spacer(Modifier.weight(1f));Text("See all",fontSize=14.sp,color=if(isDark)lime else moss,modifier=Modifier.clickable{explore()}.padding(8.dp))}
   ChallengeRail(catalog.take(5),active!=null,open)
  }
 }
}
@Composable private fun FocusStatusCard(
 monitoringOn:Boolean,monitoredAppCount:Int,sessionLimitMinutes:Int,requiredPermissionsMissing:Boolean,missingRequiredPermissionCount:Int,onToggle:()->Unit,onOpen:()->Unit
){
 val isDark=isSystemInDarkTheme()
 Column(Modifier.fillMaxWidth().padding(horizontal=16.dp).clip(RoundedCornerShape(28.dp))
  .background(if(isDark)panel else white).clickable{onOpen()}.padding(20.dp)
 ){
  Row(verticalAlignment=Alignment.CenterVertically){
   Column(Modifier.weight(1f)){
    Text("Focus status",fontSize=17.sp,color=if(isDark)Color.White else ink)
    Text(if(requiredPermissionsMissing)"Needs usage access and floating timer" else if(monitoringOn)"Your selected apps are being monitored" else "Monitoring is ready when you are",fontSize=12.sp,color=muted)
   }
   Surface(shape=RoundedCornerShape(20.dp),color=when{requiredPermissionsMissing->Color(0xFF3A3020);monitoringOn->lime.copy(alpha=.18f);isDark->Color(0xFF30312C);else->Color(0xFFECEDE8)}){
    Row(Modifier.padding(horizontal=10.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
     Box(Modifier.size(7.dp).clip(CircleShape).background(when{requiredPermissionsMissing->Color(0xFFE5B65B);monitoringOn->Color(0xFF78A83C);else->muted}))
     Spacer(Modifier.width(6.dp));Text(when{requiredPermissionsMissing->"Setup needed";monitoringOn->"On";else->"Off"},fontSize=12.sp,color=when{requiredPermissionsMissing->Color(0xFFFFD27A);monitoringOn->if(isDark)lime else moss;else->if(isDark)Color.White else ink})
    }
   }
  }
  Spacer(Modifier.height(14.dp))
  Row(verticalAlignment=Alignment.CenterVertically){
   Column(Modifier.weight(1f)){
    Text("$monitoredAppCount ${if(monitoredAppCount==1)"app" else "apps"} monitored",fontSize=14.sp,color=if(isDark)Color.White else ink)
    Text("Session limit · $sessionLimitMinutes min",fontSize=12.sp,color=muted)
   }
   Text("Manage",fontSize=13.sp,color=if(isDark)lime else moss)
  }
  Spacer(Modifier.height(12.dp))
  Button(onClick=onToggle,modifier=Modifier.fillMaxWidth().height(48.dp),shape=RoundedCornerShape(24.dp),
   colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF2A2D23),contentColor=lime)
  ){Text(if(requiredPermissionsMissing)"Finish setup · $missingRequiredPermissionCount left" else if(monitoringOn)"Pause focus" else "Start focus",fontSize=14.sp)}
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
 val ctx=LocalContext.current
 val usageAllowed=hasUsagePermission(ctx)
 val minutes by produceState(initialValue=0,usageAllowed){value=if(usageAllowed)withContext(Dispatchers.IO){phoneMinutes(ctx)}else 0}
 val cardSurface=if(isDark)Color(0xFF161714) else white
 Column(Modifier.fillMaxWidth().padding(horizontal=16.dp).clip(RoundedCornerShape(28.dp)).background(cardSurface).padding(20.dp)){
  if(usageAllowed){
   Text("Phone time today",fontSize=14.sp,color=if(isDark)Color(0xFFB3B4AD) else muted,modifier=Modifier.align(Alignment.CenterHorizontally))
   Spacer(Modifier.height(12.dp))
   Box(Modifier.size(96.dp).align(Alignment.CenterHorizontally),contentAlignment=Alignment.Center){
    Canvas(Modifier.fillMaxSize()){
     drawCircle(Color(0xFF34382B),style=Stroke(6.dp.toPx()))
     drawArc(lime,-90f,(minutes/300f).coerceIn(0f,1f)*360f,false,style=Stroke(6.dp.toPx(),cap=StrokeCap.Round))
    }
    Column(horizontalAlignment=Alignment.CenterHorizontally){
     val value=if(minutes>=60)"${minutes/60}h ${minutes%60}m" else "${minutes}m"
     Text(value,fontSize=15.sp,fontWeight=FontWeight.SemiBold,color=if(isDark)Color.White else ink)
     Text("of 5h",fontSize=11.sp,color=if(isDark)Color(0xFFB3B4AD) else muted)
    }
   }
  }else{
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
     Text("Connect usage access",fontSize=16.sp,fontWeight=FontWeight.Medium,color=if(isDark)Color.White else ink,maxLines=1,overflow=TextOverflow.Ellipsis)
     Text("See your phone time and check time-limit challenges.",fontSize=12.sp,color=if(isDark)Color(0xFFB3B4AD) else muted,maxLines=1,overflow=TextOverflow.Ellipsis)
     Button(onClick=onConnect,modifier=Modifier.height(48.dp).widthIn(min=120.dp),shape=RoundedCornerShape(24.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF2A2D23),contentColor=lime),contentPadding=PaddingValues(horizontal=18.dp)){Text("Connect")}
    }
    Spacer(Modifier.width(16.dp))
    Box(Modifier.size(72.dp),contentAlignment=Alignment.Center){
     Box(Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF2A2D23)),contentAlignment=Alignment.Center){
      Icon(Icons.Rounded.Visibility,"Usage access",tint=lime,modifier=Modifier.size(26.dp))
     }
    }
   }
  }
 }
}
@Composable private fun Sparkline(modifier:Modifier,color:Color){Canvas(modifier){val vals=listOf(.2f,.55f,.4f,.65f,.45f,.82f,.72f,.9f);val path=Path();vals.forEachIndexed{i,v->val x=size.width*i/(vals.size-1);val y=size.height*(1-v*.7f);if(i==0)path.moveTo(x,y)else path.cubicTo(x-size.width*.04f,y,x-size.width*.04f,y,x,y)};drawPath(path,color,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))}}
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun ChallengeRail(list:List<Challenge>,hasActive:Boolean,open:(Challenge)->Unit){
 BoxWithConstraints(Modifier.fillMaxWidth()){
 val cardWidth=(maxWidth-70.dp).coerceAtLeast(220.dp)
 val state=rememberLazyListState()
 LazyRow(state=state,flingBehavior=rememberSnapFlingBehavior(lazyListState=state),modifier=Modifier.clipToBounds(),contentPadding=PaddingValues(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)){items(list,key={it.id},contentType={"challenge"}){c->ArtCard(c,Modifier.width(cardWidth).height(272.dp),hasActive){open(c)}}}
 }
}
@Composable private fun ArtCard(c:Challenge,modifier:Modifier,hasActive:Boolean,onClick:()->Unit){
 val tint=challengeTint(c.tier)
 Column(modifier.clip(RoundedCornerShape(28.dp)).background(panel).clickable{onClick()}.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){
   Surface(shape=RoundedCornerShape(12.dp),color=tint.copy(alpha=.16f)){Text(c.tier,fontSize=11.sp,color=tint,modifier=Modifier.padding(horizontal=10.dp,vertical=5.dp))}
   Spacer(Modifier.weight(1f));Orb(c,Modifier.size(64.dp))
  }
  Text(pluralStringResource(R.plurals.day_count,c.days,c.days),fontSize=11.sp,color=Color(0xFFD0D2C8))
  Text(c.title,fontSize=19.sp,lineHeight=23.sp,color=Color.White,maxLines=2)
  Text(c.shortDescription,fontSize=12.sp,lineHeight=16.sp,color=Color(0xFFD0D2C8),maxLines=2)
  Spacer(Modifier.weight(1f))
  Surface(shape=RoundedCornerShape(24.dp),color=Color(0xFF2A2D23),modifier=Modifier.height(44.dp).clickable{onClick()}){Row(Modifier.fillMaxWidth().padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){Text(if(hasActive)"View challenge" else "Explore challenge",fontSize=13.sp,color=lime);Spacer(Modifier.width(6.dp));Icon(Icons.Rounded.ArrowForward,"Explore challenge",tint=lime,modifier=Modifier.size(16.dp))}}
 }
}
private fun challengeTint(tier:String)=when(tier){"Gentle start"->Color(0xFFC6E36B);"Build a rhythm"->Color(0xFFA78BFA);else->Color(0xFFF472B6)}
@Composable private fun Orb(c:Challenge,modifier:Modifier){
 val seed=c.id.hashCode();val tint=challengeTint(c.tier);val x=.25f+((seed ushr 3)%50)/100f;val y=.25f+((seed ushr 9)%50)/100f;val scale=.42f+((seed ushr 15)%35)/100f;val orbShape=when((seed and Int.MAX_VALUE)%3){0->CircleShape;1->RoundedCornerShape(40.dp);else->RoundedCornerShape(12.dp)}
 BoxWithConstraints(modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF25281F))){
  Box(Modifier.offset(x=maxWidth*x*.25f,y=maxHeight*y*.2f).size(maxWidth*scale).background(Brush.radialGradient(listOf(tint.copy(alpha=.95f),tint.copy(alpha=.34f),Color.Transparent)),orbShape))
  Box(Modifier.align(Alignment.Center).size(maxWidth*.35f).background(Brush.radialGradient(listOf(Color.White.copy(alpha=.38f),Color.Transparent)),CircleShape))
 }
}
@Composable private fun ProgressBar(value:Float){val v by animateFloatAsState(value.coerceIn(0f,1f),label="progress");Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color(0xFFE2E3DC))){Box(Modifier.fillMaxWidth().fillMaxHeight().graphicsLayer{scaleX=v;transformOrigin=TransformOrigin(0f,.5f)}.background(lime))}}
@Composable private fun ActiveCard(r:Run,check:()->Unit,complete:()->Unit,drop:()->Unit,pause:()->Unit,easier:()->Unit){
 val c=catalog.firstOrNull{it.id==r.id}?:return;val target=r.targetDays.takeIf{it>0}?:c.days;val day=(r.checks.size+1).coerceIn(1,target);val done=todayKey() in r.checks;val isDark=isSystemInDarkTheme()
 Column(Modifier.fillMaxWidth().padding(horizontal=16.dp).clip(RoundedCornerShape(28.dp)).background(if(isDark)panel else white).padding(20.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Text("Your active challenge",fontSize=12.sp,color=muted);Spacer(Modifier.weight(1f));Text(if(r.paused)"Paused" else "Active",fontSize=12.sp,color=if(isDark)lime else moss)}
  Spacer(Modifier.height(8.dp));Text(c.title,fontSize=22.sp,color=if(isDark)Color.White else ink);Spacer(Modifier.height(5.dp));Text("Day "+day+" of "+target+"  ·  "+pluralStringResource(R.plurals.check_in_count,r.checks.size,r.checks.size),fontSize=13.sp,color=muted)
  Spacer(Modifier.height(12.dp));ProgressBar(r.checks.size.toFloat()/target);Spacer(Modifier.height(12.dp));Text(c.action,fontSize=14.sp,color=if(isDark)Color.White else ink)
  Spacer(Modifier.height(14.dp));if(r.paused){Text("Paused · Your progress is saved. Resume whenever you're ready.",fontSize=13.sp,color=muted)}else if(done){OutlinedButton(onClick={},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(40.dp),border=BorderStroke(1.5.dp,lime),colors=ButtonDefaults.outlinedButtonColors(contentColor=if(isDark)Color.White else ink)){Icon(Icons.Rounded.CheckCircle,null,tint=if(isDark)lime else moss,modifier=Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Checked in today",fontSize=14.sp)}}else PrimaryButton("Finish today's session",true,check)
  if(r.checks.size>=target&&day>=target){Text("Complete challenge",Modifier.align(Alignment.CenterHorizontally).clickable{complete()}.padding(10.dp),color=if(isDark)lime else moss)}
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){
   Text(if(r.paused)"Resume" else "Pause",Modifier.clickable{pause()}.padding(10.dp),fontSize=12.sp,color=if(isDark)lime else moss)
   Text("Make it easier",Modifier.clickable{easier()}.padding(10.dp),fontSize=12.sp,color=muted)
   Text("Drop",Modifier.clickable{drop()}.padding(10.dp),fontSize=12.sp,color=muted)
  }
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
  Column(Modifier.fillMaxWidth().padding(horizontal=16.dp).padding(top=14.dp,bottom=10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
   Text("Choose a challenge",fontSize=27.sp,fontWeight=FontWeight.Light,color=if(isDark)Color.White else ink)
   Text("Pick one to start. Only one runs at a time.",fontSize=14.sp,color=muted)
  }
  LazyRow(contentPadding=PaddingValues(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth().padding(bottom=10.dp)){
   items(listOf("All","Gentle start","Build a rhythm","Deep reset"),key={it}){item->
    val chosen=tier==item
    Surface(onClick={tier=item},shape=RoundedCornerShape(22.dp),color=if(chosen)lime else Color.Transparent,border=if(chosen)null else BorderStroke(1.dp,Color(0xFF3A3E30)),modifier=Modifier.height(40.dp)){
     Row(Modifier.padding(horizontal=13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
      if(chosen)Icon(Icons.Rounded.Check,null,tint=dark,modifier=Modifier.size(15.dp))
      Text(item,fontSize=12.sp,color=if(chosen)dark else if(isDark)Color.White else ink)
     }
    }
   }
  }
  BoxWithConstraints(Modifier.fillMaxSize()){
   val columns=if(maxWidth>=600.dp)GridCells.Adaptive(minSize=160.dp) else GridCells.Fixed(2)
   LazyVerticalGrid(columns=columns,modifier=Modifier.fillMaxSize().navigationBarsPadding(),contentPadding=PaddingValues(start=16.dp,end=16.dp,top=4.dp,bottom=98.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    items(visibleChallenges,key={it.id},contentType={"challenge"}){c->
     val isActive=active?.id==c.id
     Column(Modifier.fillMaxWidth().heightIn(min=248.dp).clip(RoundedCornerShape(28.dp)).background(if(isDark)panel else white).clickable{open(c)}.padding(20.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
      Box(Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF25281F))){
       Orb(c,Modifier.fillMaxSize())
      }
      Surface(shape=RoundedCornerShape(14.dp),color=Color(0xFF25281F)){
       Row(Modifier.padding(horizontal=8.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
        Box(Modifier.size(6.dp).clip(CircleShape).background(challengeTint(c.tier)))
        Text(c.tier,fontSize=10.sp,color=challengeTint(c.tier),maxLines=1)
       }
      }
      Text(c.title,fontSize=16.sp,lineHeight=19.sp,color=if(isDark)Color.White else ink,maxLines=2,overflow=TextOverflow.Ellipsis)
      Text(c.shortDescription,fontSize=13.sp,lineHeight=17.sp,color=if(isDark)Color(0xFFD0D2C8) else Color(0xFF55574F),maxLines=3,overflow=TextOverflow.Ellipsis)
      Spacer(Modifier.weight(1f))
      Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
       Icon(Icons.Rounded.Schedule,null,tint=muted,modifier=Modifier.size(14.dp))
       Spacer(Modifier.width(5.dp))
       Text(pluralStringResource(R.plurals.day_count,c.days,c.days),fontSize=12.sp,color=muted)
       Spacer(Modifier.weight(1f))
       Icon(if(isActive)Icons.Rounded.CheckCircle else Icons.Rounded.ChevronRight,null,tint=if(isActive)lime else muted,modifier=Modifier.size(18.dp))
      }
     }
    }
    if(active!=null)item(key="active-challenge-hint",span={androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan)}){Text(if(active.paused)"Your challenge is paused. Resume it from Today when you're ready." else "One challenge at a time. Finish or drop your current challenge before starting another.",fontSize=12.sp,color=muted,modifier=Modifier.padding(top=4.dp))}
   }
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
     Spacer(Modifier.height(10.dp));Text("Challenge history",fontSize=19.sp,color=if(isDark)Color.White else ink)
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
      Column(Modifier.weight(1f).padding(start=12.dp)){Text(c?.title?:item.optString("id"),color=if(isDark)Color.White else ink);Text(pluralStringResource(R.plurals.check_in_count,item.optInt("checkIns"),item.optInt("checkIns"))+"  ·  "+(item.optInt("targetDays").takeIf{it>0}?:c?.days?:0).let{if(it>0)"$it-day target" else "Challenge"},fontSize=12.sp,color=muted);if(item.has("endedAt"))Text(SimpleDateFormat("d MMM yyyy",Locale.getDefault()).format(Date(item.optLong("endedAt"))),fontSize=11.sp,color=muted)}
      Text(item.optString("result"),fontSize=12.sp,color=muted)
     }
    }
   }
  }
 }
}
private fun countChecks(a:JSONArray):Int{var n=0;for(i in 0 until a.length())n+=a.optJSONObject(i)?.optInt("checkIns")?:0;return n}
@Composable private fun Metric(label:String,value:String,darkMode:Boolean,modifier:Modifier){Column(modifier.clip(RoundedCornerShape(24.dp)).background(if(darkMode)panel else white).padding(18.dp)){Text(value,fontSize=42.sp,fontWeight=FontWeight.Light,color=if(darkMode)Color.White else ink);Text(label,fontSize=13.sp,color=muted)}}
@Composable private fun Profile(apps:()->Unit,permissions:()->Unit,notifications:()->Unit,about:()->Unit){
 val isDark=isSystemInDarkTheme()
 Column(Modifier.fillMaxSize().background(if(isDark)Color(0xFF050505) else bgLight)){
  Header("Settings",isDark)
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp).padding(bottom=96.dp)){
   Text("Your preferences.",fontSize=28.sp,fontWeight=FontWeight.Light,color=if(isDark)Color.White else ink)
   Text("Manage app access and how Reel Focus fits your device.",fontSize=14.sp,color=muted,modifier=Modifier.padding(top=5.dp,bottom=16.dp))
   ProfileRow("Choose apps","Select the apps Reel Focus monitors",Icons.Rounded.Apps,isDark,apps)
   ProfileRow("Permissions","Review access and monitoring permissions",Icons.Rounded.Security,isDark,permissions)
   ProfileRow("Notifications","Manage Reel Focus notifications",Icons.Rounded.Notifications,isDark,notifications)
   ProfileRow("About","Privacy information and app details",Icons.Rounded.Info,isDark,about)
   Row(Modifier.fillMaxWidth().padding(top=8.dp).clip(RoundedCornerShape(22.dp)).background(if(isDark)panel else white).padding(16.dp),verticalAlignment=Alignment.CenterVertically){
    Icon(Icons.Rounded.Palette,null,tint=if(isDark)lime else moss,modifier=Modifier.size(24.dp))
    Column(Modifier.weight(1f).padding(start=14.dp)){Text("Appearance",color=if(isDark)Color.White else ink);Text("Follows your device theme",fontSize=12.sp,color=muted)}
   }
  }
 }
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
  Box(Modifier.fillMaxWidth().height(125.dp).clip(RoundedCornerShape(20.dp)).background(dark)){Orb(c,Modifier.fillMaxSize())}
  Spacer(Modifier.height(16.dp));Text(c.tier,fontSize=12.sp,color=muted);Text(c.title,fontSize=25.sp,color=if(isDark)Color.White else ink);Text(pluralStringResource(R.plurals.day_count,c.days,c.days)+"  ·  "+c.category,fontSize=13.sp,color=muted)
  Spacer(Modifier.height(14.dp));Text(c.summary,fontSize=15.sp,color=if(isDark)Color.White else ink);Spacer(Modifier.height(13.dp));Text("Your challenge",fontSize=13.sp,color=muted);Text(c.action,fontSize=14.sp,color=if(isDark)Color.White else ink);Spacer(Modifier.height(12.dp));Text("How progress is measured",fontSize=13.sp,color=muted)
  Text(if(c.limit!=null)"Android Usage Access checks your phone's recorded foreground-use time. Your daily check-in is available when recorded use is at or below ${c.limit/60} hour(s)." else "This challenge uses a daily check-in. Mark it complete after you have genuinely completed the challenge action.",fontSize=14.sp,color=if(isDark)Color.White else ink)
  if(c.limit!=null)Text("Usage Access is required for this time-limit check. Android may take a little time to update today's usage.",fontSize=12.sp,color=muted)
  Spacer(Modifier.height(12.dp));Text("Why it helps",fontSize=13.sp,color=muted);Text(c.benefit,fontSize=14.sp,color=if(isDark)Color.White else ink)
  if(hasActive)Text("Complete or drop your current challenge to start this one.",fontSize=12.sp,color=muted,modifier=Modifier.padding(top=8.dp))
 }
}