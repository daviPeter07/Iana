# Vosk usa JNA, que acessa classes por reflexão: o R8 não pode renomear nem remover nada delas.
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-keep class org.vosk.** { *; }
-dontwarn java.awt.**
