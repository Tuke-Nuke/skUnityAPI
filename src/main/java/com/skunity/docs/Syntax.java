package com.skunity.docs;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.lang.*;
import com.google.gson.*;
import com.skunity.docs.annotation.Dependency;
import org.bukkit.Bukkit;

import java.util.Arrays;

/**
 * An object to represent a Skript syntax. It can be created manually, setting each value, or with {@link SyntaxGetter}
 * to get based in Skript's syntax objects or with {@link JsonSyntaxConverter} which is used internally to get a syntax
 * from a JSON (when requesting all addon syntaxes).<br>
 * You will only need if you want to manually add a syntax or in case, somehow, it isn't loaded automatically.
 * @see Documentation#loadAutomatically(Syntax.Type...)
 */
public class Syntax {
	/**
	 * The type of syntax.
	 */
	public enum Type {
		EVENT(Field.EVENT_VALUES),
		CONDITION,
		EFFECT,
		EXPRESSION(Field.RETURN_TYPE, Field.CHANGERS),
		TYPE(Field.USAGE);

		private final Field[] fields;
		Type(Field... fields) {
			//The default fields that all of them has.
			Field[] def = new Field[]{
					Field.TYPE,
					Field.NAME,
					Field.ID,
					Field.DESCRIPTION,
					Field.EXAMPLES,
					Field.PATTERN,
					Field.ADDON,
					Field.DEPENDENCY,
					Field.SINCE};
			if (fields != null && fields.length > 0) {
				this.fields = Arrays.copyOf(def, def.length + fields.length);
				int x = def.length;
				for (Field f : fields)
					this.fields[x] = fields[x++ - def.length];
			} else
				this.fields = def;
		}

		/**
		 * Get an array of fields that this type must have.
		 * @return The array of fields that this type must have.
		 */
		public Field[] getFields() {
			return fields;
		}

		/**
		 * Get a type of a given Skript syntax class.
		 * @param clz The class, such as {@link Effect}, {@link Condition}, {@link Expression}, {@link SkriptEvent} or {@link ClassInfo}.
		 * @return The type of matching class, null if it doesn't extends any of listed classes.
		 */
		public static Type getByClass(Class<?> clz) {
			if (SkriptEvent.class.isAssignableFrom(clz))
				return EVENT;
			if (Condition.class.isAssignableFrom(clz))
				return CONDITION;
			if (Effect.class.isAssignableFrom(clz))
				return EFFECT;
			if (Expression.class.isAssignableFrom(clz))
				return EXPRESSION;
			if (ClassInfo.class.isAssignableFrom(clz))
				return TYPE;
			return null;
		}

		/**
		 * Get a type by name. It does the same as {@link Type#valueOf(String)} but doesn't throw exception.
		 * @param name The field name
		 * @return The field that matches the name
		 */
		public static Type getByName(String name) {
			if (name.endsWith("s"))
				name = name.substring(0, name.length() - 1);
			for (Type type : values())
				if (type.name().equalsIgnoreCase(name))
					return type;
			return null;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String toString() {
			return name().toLowerCase() + "s";
		}
	}

	/**
	 * Represents a field of contents of a syntax. It is used to create the json object to send to the docs and you can
	 * change how each fields gets its value.<br>
	 * For example, by default, to get a name syntax, if it is a condition, effect or expression, it gets the value from
	 * annotation {@link ch.njol.skript.doc.Name} (From Skript), if it is an event, it gets from {@link ch.njol.skript.lang.SkriptEventInfo#getName()},
	 * and if it is a type, it gets from {@link ch.njol.skript.classes.ClassInfo#getDocName()}.<br>
	 * All fields make this process to get a info from a syntax (Except the {@link Dependency} annotation which is not
	 * available for types and events). You can change by your own methods depending of your needs, in case you have
	 * your own documentation system, for example.
	 */
	public enum Field {
		/**
		 * The name of the syntax. Required.
		 */
		NAME,
		/**
		 * The description of the syntax. Optional.
		 */
		DESCRIPTION("desc"),
		/**
		 * The examples of the syntax. Optional.
		 */
		EXAMPLES,
		/**
		 * The pattern of the syntax. Required.
		 */
		PATTERN,
		/**
		 * The version of the syntax was added. If null, defaults to {@code "1.0"}.
		 */
		SINCE("version"),
		/**
		 * The {@link Type} of the syntax.
		 */
		TYPE("doc"),
		/**
		 * The dependency of the syntax.
		 */
		DEPENDENCY("plugin"),
		/**
		 * The return type of the syntax, available only for expressions.
		 */
		RETURN_TYPE("returntype"),
		/**
		 * The changers of the syntax, available only for expressions.
		 */
		CHANGERS,
		/**
		 * The usage of the syntax, available only for types.
		 */
		USAGE,
		/**
		 * The event values of the syntax, available only for events.
		 */
		EVENT_VALUES,
		/**
		 * The addon of the syntax.
		 */
		ADDON,
		/**
		 * The ID of the syntax. Internal use only.
		 */
		ID;
		private String name;
		Field() {
			name = name().toLowerCase();
		}
		Field(String value) {
			name = value;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	private String name, description, patterns, since = "1.0", examples, dependency, returnType, usage, addon;
	private Integer id;
	private String[] changers, eventValues;
	private Type type;
	private JsonObject json;
	/**
	 * A default constructor to build your syntax info.
	 * @param type The {@link Syntax.Type}
	 */
	public Syntax(Type type) {
		this.type = type;
	}

	protected JsonObject getJson() {
		return json;
	}

	protected Syntax setJson(JsonObject json) {
		this.json = json;
		return this;
	}

	/**
	 * It is used internally to check if the Syntax has the requirements: <br>
	 *     <ul>
	 *         <li>Name</li>
	 *         <li>Pattern</li>
	 *     </ul>
	 *     The <code>addon</code> and <code>since</code> is by default set to <code>Plugin's name</code> and <code>1.0</code>
	 *     if null.
	 * @return True if the syntax has at least a name and a pattern.
	 */
	public boolean isValid() {
		return StringUtils.hasEmptyString(name, patterns);
	}

	/**
	 * Get a specific field of an syntax, such as name or description.
	 * @param <T> A {@link String}, {@link String[]}, {@link Type} or {@link Integer}
	 * @param field The {@link Field}
	 * @return It returns {@link Type} if the field is {@link Field#TYPE}, {@link String[]} if the field are
	 * {@link Field#EVENT_VALUES} or {@link Field#CHANGERS}, the {@link ClassInfo} if the field is {@link Field#RETURN_TYPE}
	 * (for expressions only), {@link Integer} if {@link Field#ID} or just {@link String} for the rest.
	 * @throws ClassCastException if the value is not casted properly.
	 */
	@SuppressWarnings("unchecked")
	public <T> T get(Field field) {
		if (field == null)
			return null;
		switch (field) {
			case NAME: return (T) name;
			case DESCRIPTION: return (T) description;
			case EXAMPLES: return (T) examples;
			case PATTERN: return (T) patterns;
			case USAGE: return (T) usage;
			case TYPE: return (T) type;
			case DEPENDENCY: return (T) dependency;
			case ADDON: return (T) addon;
			case SINCE: return (T) since;
			case RETURN_TYPE: return (T) returnType;
			case EVENT_VALUES: return (T) eventValues;
			case CHANGERS: return (T) changers;
			case ID: return (T) id;
			default: return null;
		}
	}

	/**
	 * Set a specific field of the Syntax to a value.
	 * @param <T> A {@link String}, {@link String[]}, {@link Type} or {@link Integer}
	 * @param field The field to set the value.
	 * @param object The field's value, following the same type from {@link #get(Field)}, some fields requires specific
	 *               object types
	 * @return Its own instance.
	 * @throws ClassCastException Using a wrong type object for a field. Check {@link #get(Field)}.
	 */
	public <T> Syntax set(Field field, T object) throws ClassCastException {
		if (field != null && object != null) {
			switch (field) {
				case NAME: name = (String) object; break;
				case DESCRIPTION: description = (String) object; break;
				case EXAMPLES: examples = (String) object; break;
				case PATTERN: patterns = (String) object; break;
				case USAGE: usage = (String) object; break;
				case TYPE: type = (Type) object; break;
				case DEPENDENCY: dependency = (String) object; break;
				case ADDON: addon = (String) object; break;
				case SINCE: since = (String) object; break;
				case RETURN_TYPE: returnType = (String) object; break;
				case EVENT_VALUES: eventValues = (String[]) object; break;
				case CHANGERS: changers = (String[]) object; break;
				case ID: id = (Integer) object; break;
			}
			//Since the field was changed, this object will be created again
			json = null;
		}
		return this;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof Syntax))
			return false;
		Syntax s = (Syntax) obj;
		return type == s.type &&
				StringUtils.equals(name, s.name) &&
				StringUtils.equals(description, s.description) &&
				//StringUtils.equals(examples, s.examples) &&
				StringUtils.equalsPatterns(patterns, s.patterns) &&
				StringUtils.equals(dependency, s.dependency) &&
				StringUtils.equals(since, s.since) &&
				StringUtils.equals(returnType, s.returnType) &&
				StringUtils.equals(addon, s.addon) &&
				StringUtils.equals(usage, s.usage) && //Only Type.Type should have it
				Arrays.equals(changers, s.changers) &&
				Arrays.equals(eventValues, s.eventValues);
	}
}
