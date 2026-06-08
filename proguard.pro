# Не обфусцируем — только удаляем мёртвый код
-dontobfuscate
-dontoptimize

# Lombok compileOnly — его аннотации остаются в байткоде, но JAR не в classpath
-dontwarn lombok.**

# Точка входа приложения
-keep public class org.duollectis.mapart.tools.app.MTBootstrap {
    public static void main(java.lang.String[]);
}

# Picocli читает поля и методы через рефлексию по аннотациям
-keepclassmembers class * {
    @picocli.CommandLine$Option *;
    @picocli.CommandLine$Parameters *;
}
-keep @picocli.CommandLine$Command class * { *; }

# Gson читает поля через рефлексию
-keepclassmembers class org.duollectis.mapart.tools.** {
    @com.google.gson.annotations.SerializedName *;
}

# Swing создаёт компоненты через рефлексию (UI делегаты, рендереры)
-keep class * extends javax.swing.** { *; }
-keep class * extends java.awt.** { *; }

# NativeBridge использует рефлексию для поиска методов по аннотации
-keep class org.duollectis.mapart.tools.nativee.** { *; }

# Сохраняем все enum-константы (используются в switch и сериализации)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Атрибуты для корректной работы рефлексии и стектрейсов
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,Exceptions,LineNumberTable,SourceFile

-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn sun.**
-dontwarn com.sun.**
-dontwarn picocli.**
-dontwarn com.google.gson.**
-dontwarn com.google.common.**
-dontwarn net.minecraft.**

# Guava и другие транзитивные зависимости могут ссылаться на члены библиотечных классов,
# которые ProGuard не видит через jmods — подавляем эти предупреждения
-ignorewarnings
