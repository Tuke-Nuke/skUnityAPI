package com.skunity.docs;

import ch.njol.skript.classes.Changer;
import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.doc.*;
import ch.njol.skript.lang.*;
import ch.njol.skript.log.ParseLogHandler;
import ch.njol.skript.log.SkriptLogger;
import ch.njol.skript.registrations.Classes;
import com.skunity.docs.annotation.Changers;
import com.skunity.docs.annotation.Dependency;
import com.skunity.docs.annotation.Patterns;
import com.skunity.docs.annotation.ReturnType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Used to grab all information from a Skript syntax object and convert to {@link Syntax} object. It will call the methods
 * for all possible {@link com.skunity.docs.Syntax.Field} except {@link com.skunity.docs.Syntax.Field#ID} since it is
 * used internally. If you are extending that class, it is also used to detect if an syntax came from your addon, so
 * you <b>must</b> check if the Skript's syntax object represents your addon. By default, it is checked in method
 * {@link #getFromClass(Syntax.Field, Class)} by comparing the packages.
 *
 * <pre><code>
 * public String[] getFromClass(Syntax.Field fieldParameter, Class classParameter) {
 *     switch (fieldParameter) {
 *         case ADDON: return super.getFromClass(fielParameter, classParameter);
 *         //Check for all other fields you wan't to override. Only necessary in case you have your own documentation
 *         //system which doesn't use Skript annotations.
 *     }
 *     //In case you don't want to override all fields getter, you can just call super here instead.
 *     return super.getFromClass(fielParameter, classParameter);
 * }
 * </code></pre>
 *
 * Useful in case you have your own documentation methods, such as different annotations or external access.
 * @see Documentation#loadAutomatically(Syntax.Type...)
 */
public class SyntaxGetter {

	protected JavaPlugin addon;

	public SyntaxGetter(JavaPlugin addon) {
		this.addon = addon;
	}

	/**
	 * Check if the Syntax should be documented or not. If the <code>obj</code> is a {@link SkriptEventInfo}, it will return false
	 * if the {@link SkriptEventInfo#getDescription()} is equal to {@link SkriptEventInfo#NO_DOC}, since it is the way
	 * Skript select some events to not be documented. If it is {@link ClassInfo}, it will compare
	 * {@link ClassInfo#getDocName()} with {@link ClassInfo#NO_DOC}, if true, it won't be documented. For
	 * {@link SyntaxElementInfo} or {@link Class}, it will check if the class has the annotation {@link NoDoc}.
	 * <br>
	 * If {@link SkriptEventInfo} or {@link ClassInfo} returns false in its checking, it will check if the annotation is
	 * present in these classes as well.
	 * @param obj It will accept {@link SkriptEventInfo} (for events), {@link ClassInfo}, (for types),
	 * {@link SyntaxElementInfo} (for conditions, effects and expressions) or {@link Class} (for all of them).
	 * @return true if it should be added to the docs.
	 */
	public boolean check(Object obj) {
		if (obj == null)
			return false;
		Class c = null;
		if (obj instanceof SkriptEventInfo) {
			if (Arrays.equals(((SkriptEventInfo) obj).getDescription(), SkriptEventInfo.NO_DOC))
				return false;
			//If it's not, let check if the class has the annotation
			c = ((SkriptEventInfo) obj).c;
		} else if (obj instanceof ClassInfo) {
			if (((ClassInfo) obj).getDocName() != null && ((ClassInfo) obj).getDocName().equals(ClassInfo.NO_DOC))
				return false;
			c = ((ClassInfo) obj).getClass();
		} else if (obj instanceof SyntaxElementInfo)
			//There is no field for "NO_DOC"
			c = ((SyntaxElementInfo) obj).c;
		else if (obj instanceof Class)
			c = (Class)obj;
		return c != null && !c.isAnnotationPresent(NoDoc.class);
	}

	/**
	 * Get the syntax info from {@link Class}.
	 * @param field The Field of what it wants
	 * @param source The class of syntax, such as {@link Effect}, {@link Condition} or {@link Expression}. But in case
	 * {@link #getFromEvent(Syntax.Field, SkriptEventInfo)} or {@link #getFromClassInfo(Syntax.Field, ClassInfo)} returns a null array, it
	 *               will by default call this method as well, sending {@link SkriptEvent} or {@link ClassInfo}.
	 * @return An array of {@link String} of information. It can return a null or empty array.
	 */
	@SuppressWarnings("unchecked")
	public String[] getFromClass(Syntax.Field field, Class<?> source) {
		if (field == null || source == null)
			return null;
		switch (field) {
			case ADDON:
				if (source.getPackage().getName().startsWith(addon.getClass().getPackage().getName()))
					return new String[]{addon.getName()};
				break;
			case PATTERN:
				if (source.isAnnotationPresent(Patterns.class))
					return source.getAnnotation(Patterns.class).value();
				break;
			case NAME:
				if (source.isAnnotationPresent(Name.class))
					return new String[]{source.getAnnotation(Name.class).value()};
				break;
			case DESCRIPTION:
				if (source.isAnnotationPresent(Description.class))
					return source.getAnnotation(Description.class).value();
				break;
			case EXAMPLES:
				if (source.isAnnotationPresent(Examples.class))
					return source.getAnnotation(Examples.class).value();
				break;
			case SINCE:
				if (source.isAnnotationPresent(Since.class))
					return new String[]{source.getAnnotation(Since.class).value()};
				break;
			case RETURN_TYPE:
				if (!Expression.class.isAssignableFrom(source))
					return null;
				if (source.isAnnotationPresent(ReturnType.class))
					return new String[]{source.getAnnotation(ReturnType.class).value()};
				break;
			case TYPE:
				Syntax.Type type = Syntax.Type.getByClass(source);
				return type != null ? new String[]{type.toString()} : null;
			case CHANGERS:
				if (!Expression.class.isAssignableFrom(source))
					return null;
				if (source.isAssignableFrom(Changers.class)) {
					Changer.ChangeMode[] changers = source.getAnnotation(Changers.class).value();
					String[] result = new String[changers.length];
					int x = 0;
					for (Changer.ChangeMode changer : changers)
						result[x++] = changer.name().toLowerCase();
					return result;
				}
				ParseLogHandler logHandler = SkriptLogger.startParseLogHandler();
				try {
					Expression expr = (Expression) source.newInstance();
					List<String> changers = new ArrayList<>();
					for (Changer.ChangeMode mode : Changer.ChangeMode.values())
						if (expr.acceptChange(mode) != null)
							changers.add(mode.name().toLowerCase());
					if (changers.size() > 0)
						return changers.toArray(new String[changers.size()]);
				} catch (Exception ignored) {

				} finally {
					logHandler.stop();
				}
				break;
			case DEPENDENCY:
				if (source.isAssignableFrom(Dependency.class))
					return new String[]{source.getAnnotation(Dependency.class).value()};
				break;
		}
		return null;
	}

	/**
	 * Get the syntax info from {@link SyntaxElementInfo}. It is used for {@link Effect}, {@link Condition} or
	 * {@link Expression} to get the {@link com.skunity.docs.Syntax.Field#PATTERN} only. But for priority, it tries to
	 * catch the annotation {@link Patterns} before getting {@link SyntaxElementInfo#patterns}
	 * @param field The Field of what it wants
	 * @param source The {@link Effect}, {@link Condition} or {@link Expression} info.
	 * @return An array of {@link String} of information. It can return a null or empty array.
	 */
	public String[] getFromElement(Syntax.Field field, SyntaxElementInfo source) {
		String[] result = getFromClass(field, source.c);
		if (!StringUtils.isArrayEmpty(result))
			return result;
		switch (field) {
			case PATTERN: result = source.patterns; break;
			case RETURN_TYPE:
				if (source instanceof ExpressionInfo) {
					Class<?> returnType = ((ExpressionInfo) source).returnType;
					ClassInfo info = Classes.getExactClassInfo(returnType);
					if (info != null)
						result = new String[]{info.getCodeName()};
				}
				break;
		}
		return result;
	}

	/**
	 * Get the syntax info from {@link SkriptEventInfo}.
	 * @param field The Field of what it wants
	 * @param source The event syntax info.
	 * @return An array of {@link String} of information. It can return a null or empty array.
	 */
	public String[] getFromEvent(Syntax.Field field, SkriptEventInfo source) {
		String[] result = null;
		switch (field) {
			case NAME: result = new String[]{source.getName()}; break;
			case DESCRIPTION: result = source.getDescription(); break;
			case EXAMPLES: result = source.getExamples(); break;
			case PATTERN: result = source.patterns; break;
			case TYPE: result = new String[]{Syntax.Type.EVENT.toString()}; break;
			case USAGE: return null; //For types only
			case CHANGERS: return null; //For expressions only
			case SINCE: result = new String[]{source.getSince()}; break;
			case RETURN_TYPE: return null; //For expressions only
			case EVENT_VALUES: break; //TODO
			case ADDON: break; //No need to catch it here
			case ID: return null; //Not used here
			case DEPENDENCY: break; //No methods available in Skript object for it, so lets try the class annotation
		}
		if (result == null || result.length == 0 || StringUtils.isArrayEmpty(result))
			result = getFromClass(field, source.c);
		return result;
	}

	/**
	 * Get the syntax info from {@link ClassInfo}.
	 * @param field The Field of what it wants
	 * @param source The class syntax info.
	 * @return An array of {@link String} of information. It can return a null or empty array.
	 */
	public String[] getFromClassInfo(Syntax.Field field, ClassInfo source) {
		String[] result = null;
		switch (field) {
			case NAME: result = new String[]{source.getDocName()}; break;
			case DESCRIPTION: result = source.getDescription(); break;
			case EXAMPLES: result = source.getExamples(); break;
			case PATTERN:
				result = new String[source.getUserInputPatterns().length];
				int x = 0;
				for (Pattern p : source.getUserInputPatterns())
					result[x++] = p.pattern();
				break;
			case TYPE: result = new String[]{Syntax.Type.TYPE.toString()}; break;
			case USAGE: result = source.getUsage(); break;
			case CHANGERS: return null; //For expressions only
			case SINCE: result = new String[]{source.getSince()}; break;
			case RETURN_TYPE: return null; //For expressions only
			case EVENT_VALUES: break; //For events only
			case ADDON: result = new String[]{addon.getName()}; break;
			case ID: return null; //Not used here
			case DEPENDENCY: break; //No methods available in Skript object for it, so lets try the class annotation
		}
		if (result == null || result.length == 0 || StringUtils.isArrayEmpty(result))
			result = getFromClassInfo(field, source);
		return result;
	}

	/**
	 * Used internally to get a Syntax object from any Skript documentation objects.
	 * @param syntaxObject It receives {@link SkriptEvent}, {@link SyntaxElementInfo} (conditions, effects and expressions)
	 *
	 * @return
	 */
	protected Syntax getSyntax(Object syntaxObject) {
		Syntax.Type type = Syntax.Type.getByClass(syntaxObject.getClass());
		if (type == null || !check(syntaxObject))
			return null;
		Syntax s = new Syntax(type);
		for (Syntax.Field field : type.getFields()) {
			Object result;
			if (syntaxObject instanceof SkriptEventInfo)
				result = getFromEvent(field, (SkriptEventInfo)syntaxObject);
			else if (syntaxObject instanceof SyntaxElementInfo)
				result = getFromElement(field, (SyntaxElementInfo) syntaxObject);
			else if (syntaxObject instanceof ClassInfo)
				result = getFromClassInfo(field, (ClassInfo)syntaxObject);
			else if (syntaxObject instanceof Class)
				result = getFromClass(field, (Class) syntaxObject);
			else
				throw new IllegalArgumentException("The parameter is not a SkriptEventoInfo, ClassInfo, SyntaxElementInfo " +
						"nor a class");
			s.set(field, result);
		}
		return s;
	}
}
