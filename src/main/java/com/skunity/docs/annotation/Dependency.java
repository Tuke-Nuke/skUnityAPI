package com.skunity.docs.annotation;

import java.lang.annotation.*;

/**
 * Define the dependency(ies) of a Syntax. It accepts a {@link String} which may contain the plugin's name, it can also
 * have a list of dependencies, as <code>X or Y</code> or <code>X and Y</code>.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Dependency {
	String value();
}
