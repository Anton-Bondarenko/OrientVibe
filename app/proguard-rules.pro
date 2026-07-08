# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepclasseswithmembernames class * {
    native <methods>;
}
-keepclassmembers public class * extends android.view.View {
    void set*(***);
    *** get*();
}
