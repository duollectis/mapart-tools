package org.duollectis.mapart.tools.nativee;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface NativeMethod {

	String value() default ""; // Позволяет указать имя функции в C++, если оно отличается
}
