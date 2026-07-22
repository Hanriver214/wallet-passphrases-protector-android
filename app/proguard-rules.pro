# Add project specific ProGuard rules here.
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.view.View

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep JavaScript Interface methods
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep WebView JS bridge classes
-keep class com.walletprotector.android.MainActivity$SaveBridge { *; }
-keep class com.walletprotector.android.MainActivity$LoaderBridge { *; }

# Suppress R8 warnings for missing javax.lang.model classes
-dontwarn javax.lang.model.element.ModuleElement$DirectiveVisitor
-dontwarn java.lang.invoke.StringConcatFactory
