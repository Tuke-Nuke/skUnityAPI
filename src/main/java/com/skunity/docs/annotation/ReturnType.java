package com.skunity.docs.annotation;

import java.lang.annotation.*;

/**
 * Used to set the return type of an expression. It accepts a string that will be used in {@link ch.njol.skript.registrations.Classes#getClassInfoFromUserInput(String)}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ReturnType {
	String value();
}
