package com.reelfocus.app

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val BG=0xFF090B09.toInt()
private const val PANEL=0xFF171B17.toInt()
private const val INK=0xFFF3F3EC.toInt()
private const val MUTED=0xFFA6AEA4.toInt()
private const val LIME=0xFFC8F36B.toInt()
private const val DARK=0xFF1A2115.toInt()
private const val SOFT=0xFFEDEEE5.toInt()

private data class Challenge(val id:String,val title:String,val category:String,val level:String,val days:Int,val summary:String,val action:String,val benefit:String,val limit:Int?=null)
private object ChallengeCatalog {
 val all=listOf(
  Challenge("screen_5h","A 5-hour phone day","Digital balance","Ease In",1,"Keep total phone use to five hours or less today.","Set a daily intention, then check your phone usage total tonight.","Make room for the parts of your day that happen offline.",300),
  Challenge("morning_60","First hour, fully yours","Mindful mornings","Ease In",3,"Spend the first 60 minutes after waking without your phone.","Leave your phone outside the bedroom. Begin with water, daylight or movement.","Start the day on your terms."),
  Challenge("phone_meals","Meals without a screen","Presence","Ease In",7,"Keep your phone away during one meal each day.","Put your phone out of reach before you sit down to eat.","Give food and company your full attention."),
  Challenge("notification_pause","Quiet the noise","Digital calm","Ease In",1,"Turn off non-essential notifications for one day.","Keep calls and essential alerts; silence the rest in notification settings.","Reduce interruptions and reclaim your attention."),
  Challenge("social_uninstall","A week away from social feeds","Digital boundaries","Build Rhythm",7,"Take seven days away from the social apps you choose.","Select the apps that pull you into scrolling. Uninstall them yourself and reflect in your daily check-in.","Create distance from habitual scrolling without shame."),
  Challenge("screen_3h","Three-hour phone window","Digital balance","Build Rhythm",7,"Aim for three hours or less of total phone use each day.","Check your daily screen-time report and plan two phone-free blocks.","Build a steadier relationship with your device.",180),
  Challenge("no_reels","No short-form scroll","Digital boundaries","Build Rhythm",7,"Take seven days away from infinite short-video feeds.","Remove shortcuts, sign out or uninstall the apps that host short-form feeds.","Break the automatic open-and-scroll loop."),
  Challenge("focus_blocks","Two phone-free focus blocks","Attention","Build Rhythm",5,"Create two 45-minute phone-free blocks every day.","Keep your phone in another room and choose one task for each block.","Make space for deeper attention."),
  Challenge("sleep_boundary","A calmer final hour","Rest","Build Rhythm",7,"Keep the final 60 minutes before sleep screen-free.","Charge your phone away from your bed and choose a non-screen wind-down.","Give your mind a gentler transition into rest."),
  Challenge("screen_2h","Two-hour digital reset","Digital balance","Deep Reset",7,"Keep total phone use to two hours or less each day.","Plan essential use in advance and review your daily total without judgment.","Practise intentional, deliberate device use.",120),
  Challenge("social_14","Fourteen days off social media","Digital boundaries","Deep Reset",14,"Take a two-week break from the social apps you select.","Uninstall or log out of chosen apps. Keep important contacts reachable through another channel.","Reset the habits that keep pulling your attention back."),
  Challenge("offline_day","One full offline day","Presence","Deep Reset",1,"Take one day away from non-essential phone use.","Tell important contacts how to reach you in an emergency, then put your phone away.","Experience a day with fewer digital demands.")
 )
}
private data class ActiveRun(val id:String,val startedAt:Long,val checkIns:Set<Long>)
private class ChallengeStore(context:Context) {
 private val p=context.getSharedPreferences("wellness_challenges",Context.MODE_PRIVATE)
 fun active():ActiveRun?=try { val j=JSONObject(p.getString("active_challenge",null)?:return null); val a=j.optJSONArray("checkIns")?:JSONArray(); ActiveRun(j.getString("id"),j.getLong("startedAt"),(0 until a.length()).map{a.getLong(it)}.toSet()) } catch(_:Exception){null}
 fun start(c:Challenge){save(ActiveRun(c.id,System.currentTimeMillis(),emptySet()))}
 fun checkIn(r:ActiveRun){save(r.copy(checkIns=r.checkIns+dayKey()))}
 fun drop(){finish("Dropped")}
 fun complete(){finish("Completed")}
 private fun finish(result:String){val r=active()?:return; val a=history(); a.put(JSONObject().put("id",r.id).put("startedAt",r.startedAt).put("endedAt",System.currentTimeMillis()).put("result",result).put("checkIns",r.checkIns.size)); p.edit().putString("challenge_history",a.toString()).remove("active_challenge").apply()}
 fun history()=JSONArray(p.getString("challenge_history","[]")?:"[]")
 private fun save(r:ActiveRun){val a=JSONArray();r.checkIns.sorted().forEach{a.put(it)};p.edit().putString("active_challenge",JSONObject().put("id",r.id).put("startedAt",r.startedAt).put("checkIns",a).toString()).apply()}
}
class WellnessActivity:AppCompatActivity(){
 private lateinit var store:ChallengeStore
 private lateinit var body:LinearLayout
 private var tab="Today"
 private var level="All"
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);store=ChallengeStore(this);window.statusBarColor=BG;window.navigationBarColor=BG;render()}
 override fun onResume(){super.onResume();if(::store.isInitialized)render()}
 private fun render(){
  val shell=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(BG)}
  val scroll=ScrollView(this).apply{isFillViewport=true;clipToPadding=false}
  body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(17),dp(20),dp(22));setBackgroundColor(BG)}
  scroll.addView(body);shell.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));shell.addView(bottomNav(),LinearLayout.LayoutParams(-1,dp(70)));setContentView(shell)
  when(tab){"Today"->today();"Explore"->explore();else->journal()}
 }
 private fun today(){
  row(body){label(this,"REEL FOCUS",12,LIME,true,1f);label(this,SimpleDateFormat("EEE, d MMM",Locale.getDefault()).format(Date()).uppercase(),10,MUTED,true)}
  space(body,22);label(body,"A little more life.\nA little less scroll.",30,INK,true);space(body,7);label(body,"Your wellbeing, one small choice at a time.",13,MUTED);space(body,21)
  val r=store.active();if(r==null)welcome() else activeCard(r)
  space(body,23);row(body){label(this,"Picked for you",19,INK,true,1f);label(this,"EXPLORE →",10,LIME,true).setOnClickListener{tab="Explore";render()}}
  space(body,12);rail(ChallengeCatalog.all.take(5),r!=null);space(body,22);usageCard()
  space(body,20);label(body,"A note to yourself",16,INK,true);space(body,7);label(body,"Progress is not a perfect streak. It is noticing what matters and choosing again.",13,MUTED)
 }
 private fun welcome(){
  val c=card(PANEL,27);body.addView(c,match());val b=column(c,20)
  label(b,"YOUR NEXT RESET",10,LIME,true);label(b,"What would you like\nto make room for?",23,INK,true)
  label(b,"Choose a challenge that feels useful today. No levels to unlock and no completion history required.",13,MUTED)
  button(b,"FIND A CHALLENGE  →",LIME,DARK){tab="Explore";render()}
 }
 private fun activeCard(r:ActiveRun){
  val x=ChallengeCatalog.all.firstOrNull{it.id==r.id}?:return
  val c=card(SOFT,26);body.addView(c,match());val b=column(c,18)
  row(b){label(this,"YOUR CURRENT PRACTICE",10,0xFF59634F.toInt(),true,1f);label(this,"● ACTIVE",9,0xFF5D7B31.toInt(),true)}
  label(b,x.title,22,DARK,true);label(b,x.days.toString()+" days  ·  "+x.category,11,0xFF687160.toInt())
  val day=dayKey();val todayDone=day in r.checkIns
  val dayNum=((day-r.startedAt/86400000L).toInt()+1).coerceIn(1,x.days)
  row(b){label(this,"DAY "+dayNum+" OF "+x.days,10,DARK,true,1f);label(this,r.checkIns.size.toString()+" check-ins",10,0xFF687160.toInt())}
  progress(b,r.checkIns.size.toFloat()/x.days)
  if(x.limit!=null)label(b,if(hasUsageAccess())"TODAY · "+fmt(usageMinutes())+" / "+fmt(x.limit) else "SCREEN-TIME CHECK · USAGE ACCESS NEEDED",10,0xFF65705E.toInt())
  label(b,x.action,12,0xFF4F5949.toInt())
  button(b,if(todayDone)"TODAY'S CHECK-IN SAVED ✓" else "CHECK IN TODAY",if(todayDone)0xFFD4DACB.toInt() else LIME,DARK,!todayDone){checkIn(r,x)}
  button(b,"VIEW CHALLENGE DETAILS",0xFFDDE1D6.toInt(),DARK){details(x)}
  if(r.checkIns.size>=x.days&&dayNum>=x.days)button(b,"COMPLETE CHALLENGE",DARK,INK){store.complete();render()}
  label(b,"DROP CHALLENGE",10,0xFF687160.toInt(),true).apply{gravity=Gravity.CENTER;setPadding(0,dp(8),0,0);setOnClickListener{confirmDrop()}}
 }
 private fun checkIn(r:ActiveRun,x:Challenge){
  if(x.limit!=null){
   if(!hasUsageAccess()){AlertDialog.Builder(this).setTitle("Allow usage access").setMessage("Android's Usage Access permission is needed to verify this screen-time target.").setNegativeButton("Later",null).setPositiveButton("Open settings"){_,_->startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))}.show();return}
   val used=usageMinutes();if(used>x.limit){AlertDialog.Builder(this).setTitle("Not quite today").setMessage("Android has recorded "+fmt(used)+" of phone use today. The target is "+fmt(x.limit)+". You can reflect and try again tomorrow.").setPositiveButton("Got it",null).show();return}
  }
  store.checkIn(r);render()
 }
 private fun explore(){
  top("THE CHALLENGE LIBRARY","Pick what feels right.")
  space(body,7);label(body,"Every challenge is open to you. Start anywhere, regardless of what you have completed before.",13,MUTED);space(body,17)
  row(body,6){listOf("All","Ease In","Build Rhythm","Deep Reset").forEach{v->val t=TextView(this@WellnessActivity).apply{text=v;textSize=10f;gravity=Gravity.CENTER;setPadding(dp(11),dp(10),dp(11),dp(10));setTextColor(if(level==v)DARK else MUTED);background=rounded(if(level==v)LIME else PANEL,24);setOnClickListener{level=v;render()}};addView(t,LinearLayout.LayoutParams(-2,-2))}}
  space(body,20);val active=store.active()
  ChallengeCatalog.all.filter{level=="All"||it.level==level}.groupBy{it.category}.forEach{(cat,items)->label(body,cat.uppercase(),10,LIME,true);space(body,9);rail(items,active!=null);space(body,21)}
  if(active!=null)label(body,"Complete or drop your active challenge before starting another.",11,MUTED)
 }
 private fun journal(){
  top("YOUR JOURNAL","Every effort counts.");space(body,7);label(body,"A record of the challenges you chose to take on.",13,MUTED);space(body,19)
  val h=store.history()
  if(h.length()==0){val c=card(PANEL,24);body.addView(c,match());val b=column(c,18);label(b,"Your story starts here.",21,INK,true);label(b,"Completed and dropped challenges will appear here. There is no score and no judgement.",13,MUTED);button(b,"BROWSE CHALLENGES →",LIME,DARK){tab="Explore";render()}}
  else for(i in h.length()-1 downTo 0){val item=h.getJSONObject(i);val x=ChallengeCatalog.all.firstOrNull{it.id==item.optString("id")};val c=card(PANEL,20);body.addView(c,match().apply{bottomMargin=dp(10)});val b=column(c,16);row(b){label(this,item.optString("result").uppercase(),9,if(item.optString("result")=="Completed")LIME else MUTED,true,1f);label(this,SimpleDateFormat("d MMM yyyy",Locale.getDefault()).format(Date(item.optLong("endedAt"))),10,MUTED)};label(b,x?.title?:item.optString("id"),17,INK,true);label(b,item.optInt("checkIns").toString()+" check-ins",11,MUTED)}
 }
 private fun rail(items:List<Challenge>,locked:Boolean){
  val horizontal=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;clipToPadding=false}
  val cards=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  items.forEach{x->val c=card(PANEL,23);cards.addView(c,LinearLayout.LayoutParams(dp(258),dp(214)).apply{rightMargin=dp(10)});val b=column(c,16)
   row(b){label(this,x.level.uppercase(),9,LIME,true,1f);label(this,x.days.toString()+"D",10,MUTED,true)}
   label(b,x.title,18,INK,true);label(b,x.summary,12,MUTED)
   b.addView(View(this),LinearLayout.LayoutParams(1,0,1f))
   button(b,if(locked)"VIEW DETAILS →" else "EXPLORE →",if(locked)0xFF252C25.toInt() else LIME,if(locked)INK else DARK){details(x)}
   c.setOnClickListener{details(x)}
  }
  horizontal.addView(cards);body.addView(horizontal,match())
 }
 private fun details(x:Challenge){
  val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(22),dp(20),dp(22),dp(8))}
  label(b,x.level.uppercase(),10,0xFF718D42.toInt(),true);space(b,9);label(b,x.title,24,DARK,true);label(b,x.days.toString()+" days  ·  "+x.category,12,0xFF65705E.toInt());space(b,15)
  label(b,x.summary,15,DARK);space(b,14);label(b,"YOUR PRACTICE",10,0xFF718D42.toInt(),true);space(b,5);label(b,x.action,13,0xFF4F5949.toInt());space(b,13);label(b,"WHY IT HELPS",10,0xFF718D42.toInt(),true);space(b,5);label(b,x.benefit,13,0xFF4F5949.toInt())
  val canStart=store.active()==null
  val dialog=AlertDialog.Builder(this).setView(b).setNegativeButton("Close",null).setPositiveButton(if(canStart)"Start challenge" else "One active challenge"){_,_->if(store.active()==null){store.start(x);tab="Today";render()}}.create()
  dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=canStart
 }
 private fun usageCard(){
  val c=card(PANEL,22);body.addView(c,match());val b=column(c,16)
  row(b){label(this,"TODAY'S PHONE TIME",10,MUTED,true,1f);label(this,if(hasUsageAccess())"ANDROID DATA" else "OPTIONAL",9,LIME,true)}
  if(hasUsageAccess()){label(b,fmt(usageMinutes()),28,INK,true);label(b,"Total foreground app usage recorded today.",11,MUTED)}
  else{label(b,"Know your baseline.",18,INK,true);label(b,"Connect Android Usage Access to see daily phone time and verify time-limit challenges.",12,MUTED);button(b,"CONNECT USAGE ACCESS",0xFF252C25.toInt(),INK){startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))}}
 }
 private fun hasUsageAccess():Boolean=try{val ops=getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager;@Suppress("DEPRECATION") val mode=if(android.os.Build.VERSION.SDK_INT>=29)ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),packageName) else ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),packageName);mode==AppOpsManager.MODE_ALLOWED}catch(_:Exception){false}
 private fun usageMinutes():Int{if(!hasUsageAccess())return 0;val c=Calendar.getInstance();c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);val m=getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager;return (m.queryAndAggregateUsageStats(c.timeInMillis,System.currentTimeMillis()).values.sumOf{it.totalTimeInForeground}/60000L).toInt()}
 private fun confirmDrop(){AlertDialog.Builder(this).setTitle("Drop this challenge?").setMessage("This run will be saved in your journal as dropped. You can choose another challenge immediately.").setNegativeButton("Keep going",null).setPositiveButton("Drop challenge"){_,_->store.drop();render()}.show()}
 private fun bottomNav():LinearLayout{val nav=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER;setPadding(dp(12),dp(7),dp(12),dp(9));setBackgroundColor(0xFF111411.toInt())};listOf("Today" to "⌂","Explore" to "✳","Journal" to "◷").forEach{(name,glyph)->val active=tab==name;val item=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;addView(TextView(this@WellnessActivity).apply{text=glyph;textSize=20f;gravity=Gravity.CENTER;setTextColor(if(active)LIME else MUTED)},LinearLayout.LayoutParams(-1,dp(27)));addView(TextView(this@WellnessActivity).apply{text=name;textSize=10f;gravity=Gravity.CENTER;setTextColor(if(active)INK else MUTED)});setOnClickListener{tab=name;render()}};nav.addView(item,LinearLayout.LayoutParams(0,-1,1f))};return nav}
 private fun top(kicker:String,title:String){row(body){label(this,"REEL FOCUS",11,LIME,true,1f);label(this,kicker,9,MUTED,true)};space(body,22);label(body,title,28,INK,true)}
 private fun card(color:Int,radius:Int)=MaterialCardView(this).apply{setCardBackgroundColor(color);this.radius=dp(radius).toFloat();cardElevation=0f;strokeWidth=dp(1);strokeColor=if(color==PANEL)0xFF252C25.toInt() else color;preventCornerOverlap=true}
 private fun column(c:MaterialCardView,p:Int)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(p),dp(p),dp(p),dp(p));c.addView(this)}
 private fun label(p:LinearLayout,s:String,size:Int,color:Int,bold:Boolean=false)=TextView(this).apply{text=s;textSize=size.toFloat();setTextColor(color);typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL);includeFontPadding=true}.also{p.addView(it,match())}
 private fun label(p:LinearLayout,s:String,size:Int,color:Int,bold:Boolean,weight:Float)=TextView(this).apply{text=s;textSize=size.toFloat();setTextColor(color);typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)}.also{p.addView(it,LinearLayout.LayoutParams(0,-2,weight))}
 private fun row(p:LinearLayout,gap:Int=8,block:LinearLayout.()->Unit){val r=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL};p.addView(r,match());r.block()}
 private fun button(p:LinearLayout,text:String,bg:Int,fg:Int,enabled:Boolean=true,action:()->Unit){val v=MaterialButton(this).apply{this.text=text;textSize=10f;setTextColor(fg);isEnabled=enabled;backgroundTintList=android.content.res.ColorStateList.valueOf(bg);cornerRadius=dp(22);minHeight=dp(43);insetTop=0;insetBottom=0;setOnClickListener{action()}};p.addView(v,match())}
 private fun progress(p:LinearLayout,value:Float){val f=FrameLayout(this).apply{background=rounded(0xFFD4DACB.toInt(),8)};val w=(resources.displayMetrics.widthPixels-dp(80)).coerceAtLeast(dp(30));f.addView(View(this).apply{background=rounded(0xFF789844.toInt(),8)},FrameLayout.LayoutParams((w*value.coerceIn(0f,1f)).toInt(),dp(6)));p.addView(f,LinearLayout.LayoutParams(-1,dp(6)))}
 private fun rounded(color:Int,radius:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat()}
 private fun space(p:LinearLayout,h:Int){p.addView(View(this),LinearLayout.LayoutParams(1,dp(h)))}
 private fun match()=LinearLayout.LayoutParams(-1,-2)
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun dayKey():Long{val c=Calendar.getInstance();c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.timeInMillis/86400000L}
 private fun fmt(m:Int)=if(m>=60)(m/60).toString()+"h "+(m%60)+"m" else m.toString()+"m"
}
