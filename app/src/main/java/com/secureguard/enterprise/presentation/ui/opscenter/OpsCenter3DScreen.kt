package com.secureguard.enterprise.presentation.ui.opscenter
import android.annotation.SuppressLint
import android.webkit.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import org.json.JSONObject
private const val OPS_ORIGIN="https://ops.secureguard.local/"
@SuppressLint("SetJavaScriptEnabled")
@Composable fun OpsCenter3DScreen(navController:NavHostController,viewModel:OpsCenterViewModel=hiltViewModel()){
 val snapshot by viewModel.snapshot.collectAsState()
 AndroidView(Modifier.fillMaxSize(),factory={context->WebView(context).apply{
  settings.javaScriptEnabled=true
  settings.domStorageEnabled=false
  webViewClient=object:WebViewClient(){override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse?=if(request.url.toString().startsWith(OPS_ORIGIN))super.shouldInterceptRequest(view,request)else null}
  addJavascriptInterface(object{@JavascriptInterface fun requestAction(action:String,assetId:String?){ /* dispatch via real action layer */ }},"SecureGuardNative")
  loadUrl("file:///android_asset/console3d/index.html")
 }},update={webView->
  val json=JSONObject().apply{
   put("alarms",snapshot.alarms);put("detections",snapshot.detections);put("agentOnline",snapshot.agentOnline);put("queuedActions",snapshot.queuedActions)
   put("assets",snapshot.assets.map{a->JSONObject().apply{put("id",a.id);put("name",a.name);put("online",a.online);put("rssi",a.rssi);put("latitude",a.latitude);put("longitude",a.longitude);put("lastSeenEpochMs",a.lastSeenEpochMs)}})
  }
  val payload=JSONObject.quote(json.toString())
  webView.evaluateJavascript("window.SecureGuardOps?.setNativeSnapshot("+payload+")",null)
 })
}