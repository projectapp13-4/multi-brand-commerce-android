-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# Keep independently owned typed routes serialized by Kotlin serialization.
-keepclassmembers class com.gurbakir.mobile.navigation.** {
    *** Companion;
}
