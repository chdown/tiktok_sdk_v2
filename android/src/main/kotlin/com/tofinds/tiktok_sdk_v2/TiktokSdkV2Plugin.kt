package com.tofinds.tiktok_sdk_v2

import android.app.Activity
import android.content.Intent
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import com.tiktok.open.sdk.auth.AuthApi
import com.tiktok.open.sdk.auth.AuthRequest
import com.tiktok.open.sdk.auth.utils.PKCEUtils

/** FlutterTiktokSdkPlugin */
class TiktokSdkV2Plugin: FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.NewIntentListener {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  private var authApi: AuthApi? = null

  var activity: Activity? = null
  private var activityPluginBinding: ActivityPluginBinding? = null
  private var loginResult: Result? = null

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "com.tofinds.tiktok_sdk_v2")
    channel.setMethodCallHandler(this)
  }

  private var clientKey: String? = null
  private var codeVerifier: String = ""
  private var redirectUrl: String = ""

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    println(call.method);
    when (call.method) {
      "setup" -> {
        val activity = activity
        if  (activity == null) {
          result.error(
                  "no_activity_found",
                  "There is no valid Activity found to present TikTok SDK Login screen.",
                  null
          )
          return
        }

        clientKey = call.argument<String?>("clientKey")
        authApi = AuthApi(activity = activity)
        result.success(null)
      }
      "login" -> {
        val scope = call.argument<String>("scope")
        val state = call.argument<String>("state")
        redirectUrl = call.argument<String>("redirectUri") ?: ""
        var browserAuthEnabled = call.argument<Boolean>("browserAuthEnabled")


        codeVerifier = PKCEUtils.generateCodeVerifier()

        val request = AuthRequest(
          clientKey = clientKey ?: "",
          scope = scope ?: "",
          redirectUri = redirectUrl,
          state = state,
          codeVerifier = codeVerifier,
        )
       val authType = if (browserAuthEnabled == true) {
         AuthApi.AuthMethod.ChromeTab
       } else {
         AuthApi.AuthMethod.TikTokApp
       }
        // var authType = AuthApi.AuthMethod.ChromeTab
        authApi?.authorize(request, authType)
        loginResult = result
      }
      else -> result.notImplemented()
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    bindActivityBinding(binding)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    unbindActivityBinding()
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    bindActivityBinding(binding)
  }

  override fun onDetachedFromActivity() {
    unbindActivityBinding()
  }

  private fun bindActivityBinding(binding: ActivityPluginBinding) {
    activity = binding.activity
    activityPluginBinding = binding
    binding.addOnNewIntentListener(this);
  }

  private fun unbindActivityBinding() {
    activityPluginBinding?.removeOnNewIntentListener(this)
    activity = null
    activityPluginBinding = null
  }

  override fun onNewIntent(intent: Intent): Boolean {
    authApi?.getAuthResponseFromIntent(intent, redirectUrl = redirectUrl)?.let {
      val authCode = it.authCode
      if (authCode.isNotEmpty()) {
        var resultMap = mapOf(
          "authCode" to authCode,
          "state" to it.state,
          "grantedPermissions" to it.grantedPermissions,
          "codeVerifier" to codeVerifier
        )
        loginResult?.success(resultMap)
      } else {
        // Returns an error if authentication fails
        loginResult?.error(
          it.errorCode.toString(),
          it.errorMsg,
          null,
        )
      }
    }
    return true

  }
}
