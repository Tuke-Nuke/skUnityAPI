package com.skunity.docs;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.lang.SkriptEventInfo;
import ch.njol.skript.lang.SyntaxElementInfo;
import com.google.gson.*;

import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.StringJoiner;

/**
 * A converter from Json to Syntax and vice versa, according to skUnity format
 */
class JsonSyntaxConverter {

	protected SyntaxGetter getter;
	public JsonSyntaxConverter(SyntaxGetter getter) {
		this.getter = getter;
	}

	public Syntax fromJson(JsonObject json) {
		String typeName = getAsString(json, Syntax.Field.TYPE);
		if (typeName == null)
			return null;
		Syntax.Type type = Syntax.Type.getByName(typeName);
		if (type == null)
			return null;
		Syntax s = new Syntax(type);
		s.setJson(json);
		for (Syntax.Field field : type.getFields()) {
			JsonElement element = json.get(field.toString());
			if (element == null)
				continue;
			try {
				s.set(field, element instanceof JsonArray ? getAsArray(element) :
						field == Syntax.Field.ID ? element.getAsInt() :element.getAsString());
			} catch (ClassCastException ignored) {

			}
		}
		return s;
	}

	public JsonObject fromSyntax(Syntax syntax) {
		if (syntax.getJson() != null) //The syntax was previously loaded with a json object, so let's get it back
			return syntax.getJson();
		JsonObject json = new JsonObject();
		Syntax.Type type = syntax.get(Syntax.Field.TYPE);
		for (Syntax.Field field : type.getFields()) {
			String[] array;
			Object get = syntax.get(field);
			if (field == Syntax.Field.ID) {
				// A hacky way to check for the id, since it's the only integer value.
				if (get != null && get instanceof Integer)
					json.addProperty(field.toString(), (Integer) get);
				continue;
			} if (!(get instanceof String[]))
				array = get != null ? new String[]{ get.toString()} : null;
			else
				array = (String[]) get;
			set(json, field, array);
		}
		return json;
	}

	private void set(JsonObject json, Syntax.Field field, String... array) {
		if (field == null || json == null)
			return;
		if (StringUtils.isArrayEmpty(array)) {
			if (field == Syntax.Field.SINCE)
				json.addProperty(field.toString(), "1.0"); //A default value is used, only for version
			else if (field == Syntax.Field.ADDON)
				json.addProperty(field.toString(), getter.addon.getName());
			else
				json.add(field.toString(), JsonNull.INSTANCE);
		} else {
			JsonElement property;
			switch (field) {
				//These two fields are the only that returns as a json array
				case CHANGERS:
				case EVENT_VALUES:
					property = new JsonArray();
					for (String str : array)
						((JsonArray)property).add(new JsonPrimitive(str));
					break;
				default:
					StringJoiner sj = new StringJoiner("\n");
					for (String str : array)
						sj.add(str);
					property = new JsonPrimitive(sj.toString());
					break;
			}
			json.add(field.toString(), property);
		}
	}

	private String getAsString(JsonObject json, Syntax.Field field) {
		JsonElement result = json != null && field != null ? json.get(field.toString()) : null;
		return result != null && result.isJsonPrimitive() ? result.getAsString() : null;
	}
	private String[] getAsArray(JsonElement result) {
		String[] array = null;
		if (result != null && result instanceof JsonArray) {
			array = new String[((JsonArray) result).size()];
			int x = 0;
			Iterator<JsonElement> it = ((JsonArray) result).iterator();
			while (it.hasNext()) {
				JsonElement entry = it.next();
				if (entry instanceof JsonPrimitive)
					array[x++] = entry.getAsString();
			}
		}
		return array;
	}
}
