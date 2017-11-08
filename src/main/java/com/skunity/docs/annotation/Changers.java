package com.skunity.docs.annotation;

import ch.njol.skript.classes.Changer;

import java.lang.annotation.*;

/**
 * Used to define the changers of an expression. The API tries to get the changers automatically, but in case you want
 * to manually set it, just send an array of {@link ch.njol.skript.classes.Changer.ChangeMode} as value.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Changers {
	Changer.ChangeMode[] value();
}
