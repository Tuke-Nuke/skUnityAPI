package com.skunity.docs.annotation;

import java.lang.annotation.*;

/**
 * Define the patterns of the Syntax. In case your syntax is not friendly to see and it is not well cleaned in this API,
 * you can define a friendly syntax.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Patterns {
	String[] value();
}
