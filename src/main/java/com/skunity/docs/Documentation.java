package com.skunity.docs;

import ch.njol.skript.Skript;
import ch.njol.skript.classes.Changer;
import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.classes.Serializer;
import ch.njol.skript.lang.*;
import ch.njol.skript.registrations.Classes;
import com.google.common.collect.Lists;
import com.google.gson.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Automatically update new or edited local syntax in your addon to skUnity Docs.
 * <br>
 * This API should be run once you want to upload new or edited syntaxes, so to prevent it be triggered in every server
 * that the addon is in, the API only works with a private key, which you can get it from skUnity docs page, and put the
 * key in a file called <code>plugins/&lt;Addon folder&gt;/addon.key</code> (you need to create yourself). Now every time this object is created, it will
 * check skUnity for your addon's syntaxes and compare them if there is something new or edited locally, and them it sends
 * directly to skUnity. More info about this API in <a href="https://github.com/Tuke-Nuke/skUnityAPI/wiki">Github</a>.
 *
 */
public class Documentation {

	//The instance of your addon
	protected final JavaPlugin ADDON;

	//The skUnity API Documentation key. The API will only work if the API is correct.
	protected final String KEY;

	//It is setted to true when the KEY is a correct key.
	protected boolean isKeyValid = false;

	//A list of all of loaded syntax of your addon. Before updating, it will filter all syntax that are already in docs
	//and didn't had any change on it (description, patterns...).
	protected List<Syntax> syntaxes = new ArrayList<>();

	//Your current addon syntaxes from the documentation. It will be used to check if something was changed or added.
	protected final List<Syntax> downloadedSyntax = new ArrayList<>();

		//Some options, see its method below to see what they do.
	protected boolean friendlySyntax = true;

	//The types that will be loaded automatically. By default of them are.
	protected Syntax.Type[] automaticallyLoad = Syntax.Type.values();

	//The class that will get every info from a syntax.
	private SyntaxGetter getter;

	//The converter JsonObject <-> Syntax
	private JsonSyntaxConverter converter;
	// Static values
	private static final String API_BASE_URL = "https://docs.skunity.com/api/?key=%s";
	private static final String API_FUNCTION_CHECK_KEY= "&function=checkKey";
	private static final String API_FUNCTION_GET_ADDON_SYNTAX = "&function=getAddonSyntax&addon=%s";
	private static final String API_FUNCTION_POST_ADDON_SYNTAX = "&function=massCreate&arrayReturn=true";
	/**
	 * The API version.
	 */
	public static final String VERSION = "1.0";

	/**
	 * Create a new instance of a Documentation.
	 * @param instance The instance of your addon.
	 *
	 * @throws NullPointerException if the parameter is null.
	 * @throws IllegalArgumentException if the parameter is not an addon (not registered with {@link Skript#registerAddon(JavaPlugin)}).
	 */
	public Documentation(JavaPlugin instance) {
		if (instance == null)
			throw new NullPointerException("The instance of your plugin can not be null.");
		ADDON = instance;
		File keyFile = new File(instance.getDataFolder(), "addon.key");
		if (keyFile.exists()) {
			String line = null;
			try (BufferedReader br = new BufferedReader(new FileReader(keyFile))) {
				line = br.readLine();
			} catch (Exception e) {

			}
			KEY = line;
			if (KEY != null)
				validateKey();
		} else
			KEY = null;
	}


	/**
	 * Set the {@link SyntaxGetter} for this documentation. It is used only in cases you have your own documentation system.
	 *
	 * @see SyntaxGetter
	 *
	 * @param getter The SyntaxGetter
	 * @return Its own instance
	 */
	public Documentation setSyntaxGetter(SyntaxGetter getter) {
		if (getter == null)
			throw new NullPointerException("The SyntaxGetter can not be null");
		this.getter = getter;
		return this;
	}

	/**
	 * Get the {@link SyntaxGetter}. If {@link #setSyntaxGetter(SyntaxGetter)} wasn't used, it will use a default object.
	 * @return The SyntaxGetter.
	 */
	public SyntaxGetter getGetter() {
		if (getter == null)
			getter = new SyntaxGetter(ADDON);
		return getter;
	}

	/**
	 * Convert some syntaxes to user friendly to see.
	 * Some group indexes (<code>1¦|2¦</code>...) and escaped values
	 * (removes all <code>\\</code> that is used to escape the next char).<br>
	 * Examples:
	 * <pre><code>
	 *     (1¦one|2¦two) of %player% -&gt; (one|two) of player
	 *
	 *     do \\function\(%objects%\) -&gt; do \function(%objects%)
	 *
	 *     //For Types only, it converts from java regex to Skript regex
	 *     some ?example(s)? -&gt; some[ ]example[s]
	 * </code></pre>
	 *
	 * @param value True to make a friendly syntax.
	 * @return Its own instance
	 */
	public Documentation friendlySyntax(boolean value) {
		friendlySyntax = value;
		return this;
	}

	/**
	 * Set which types of syntaxes should be automatically loaded.<br>
	 * Since Skript doesn't track the addon owner of a given element, it will find the addon by checking its package names. <br>
	 * Your syntax must be at same package level of your main class, for example:<br>
	 * Same package, it will find it fine.<br>
	 * <pre><code>
	 * Main class: ch.njol.skript.Skript
	 * Base package of syntaxes: ch.njol.skript.&lt;syntax type&gt;
	 * </code></pre>
	 * Different packages, it won't find them.<br>
	 * <pre><code>
	 * Main class: ch.njol.core.Skript
	 * Base package of syntaxes: ch.njol.elements.&lt;syntax type&gt;
	 * </code></pre>
	 * For <b>events</b>, it will check the package from {@link SkriptEvent} class.
	 * If you use {@link ch.njol.skript.lang.util.SimpleEvent (from Skript)} to register your events, it won't find
	 * these events. To fix it, just create a class extending SkriptEvent and use it instead.<br>
	 * For <b>conditions</b>, <b>effects</b> and <b>expressions</b>, it will check the <b>syntax class's package</b>.<br>
	 * For <b>types</b>, it will check one of the following classes to match your addon.<br>
	 * <ul>
	 *     <li>The {@link Parser}, if not null, then.</li>
	 *     <li>The {@link Changer}, if not null, then.</li>
	 *     <li>The {@link Serializer}, if not null, then.</li>
	 *     <li>The {@link ClassInfo} itself.</li>
	 * </ul>
	 * For the last option, you will need to create your class that extends {@link ClassInfo}, so this way the object will have your addon's package.
	 * @param types An array of {@link com.skunity.docs.Syntax.Type} that should be loaded automatically. By default, is setted to all {@link Syntax.Type Syntax.Types}
	 * @return Its own instance
	 */
	public Documentation loadAutomatically(Syntax.Type... types) {
		//automaticallyLoad = types;
		return this;
	}
	/**
	 * Manually add the syntax of your addon. It is only required in case it can't find your addon syntaxes,
	 * {@link #loadAutomatically(Syntax.Type...)} is setted to null or you want to manually add something.
	 * <br>
	 * @see Syntax
	 * @param syntax The Syntax object.
	 * @return true if it has the minimum requirements: The API Key setted, the syntax's name and patterns not null
	 */
	public boolean addSyntax(Syntax syntax) {
		//It won't add any syntax if the key is not present or the syntax doesn't have a name and pattern
		if (KEY == null || syntax == null || !syntax.isValid())
			return false;
		if (syntax.get(Syntax.Field.ADDON) == null)
			syntax.set(Syntax.Field.ADDON, ADDON.getName());
		syntaxes.add(syntax);
		return true;
	}

	/*
	  --------------------- INTERNAL CODE ---------------------
	  No methods below should be used (since it's internal usage only)
	  ---------------------------------------------------------
	 */
	private JsonSyntaxConverter getConverter() {
		if (converter == null)
			converter = new JsonSyntaxConverter(getGetter());
		return converter;
	}

	protected void validateKey() {
		if (KEY != null && !isKeyValid) {
			log(Level.INFO, "A key was found, validating the key: " + KEY);
			Thread check = new Thread(() -> {
				HttpURLConnection skunity = null;
				try {
					skunity = (HttpURLConnection) new URL(method(API_FUNCTION_CHECK_KEY)).openConnection();
					skunity.addRequestProperty("Connection", "close");
					skunity.setRequestProperty("Content-Type", "application/json");
					skunity.setRequestProperty("User-Agent", "skUnity API Documentation/" + VERSION);
					skunity.setRequestMethod("GET");
					skunity.setUseCaches(false);
					JsonObject result = (JsonObject) new JsonParser().parse(new InputStreamReader(skunity.getInputStream()));
					JsonElement response = result.get("response");
					isKeyValid = skunity.getResponseCode() == 200 || response.isJsonPrimitive()
							&& response.getAsString().equals("success");
					if (isKeyValid) {
						log(Level.INFO, "The key is correct. Waiting for Skript finishes registration and loading to continue.");
						download();
					} else {
						log(Level.WARNING, "The key is incorrect. Go to your skUnity addon page and check for your API key.");
					}
				} catch (UnknownHostException e){
					log(Level.WARNING, "Couldn't upload the syntaxes due to connection issue. Check your connection status.");
				} catch (Exception e) {
					log(Level.SEVERE, "A error occurred while checking the key to SkUnity:");
					e.printStackTrace();
				} finally {
					if (skunity != null)
						skunity.disconnect();
				}
			},"Checking skUnity API key of  " + ADDON.getName());
			check.setDaemon(true);
			check.start();
		}
	}
	protected void download() {
		if (KEY != null && isKeyValid) {
			//No new thread needed here, it should run at first one
			HttpURLConnection skunity;
			try {
				skunity = (HttpURLConnection) new URL(method(API_FUNCTION_GET_ADDON_SYNTAX, ADDON.getName())).openConnection();
				skunity.addRequestProperty("Connection", "close");
				skunity.setRequestProperty("Content-Type", "application/json");
				skunity.setRequestProperty("User-Agent", "skUnity API Documentation/" + VERSION);
				skunity.setRequestMethod("GET");
				JsonObject result = (JsonObject) new JsonParser().parse(new InputStreamReader(skunity.getInputStream()));
				for (JsonElement json : (JsonArray)result.get("result")) {
					downloadedSyntax.add(getConverter().fromJson((JsonObject) json));
				}
				log(Level.INFO, "A total of " + downloadedSyntax.size() + " syntaxes was found in skUnity.");
				ADDON.getServer().getScheduler().runTaskLaterAsynchronously(ADDON, this::upload, 5L);
			} catch (UnknownHostException e){
				log(Level.WARNING, "Couldn't upload the syntaxes due to connection issue. Check your connection status.");
			} catch (IOException e) {
				log(Level.SEVERE, "A error occurred while downloading the documentation.");
				e.printStackTrace();
			}
		}
	}

	protected void upload() {
		// Just some safe check.
		if (KEY == null)
			throw new IllegalStateException("The KEY can't not be null");
		if (!isKeyValid)
			// It never happen unless someone tries to manually call it
			throw new IllegalStateException("You need to input a valid key to upload it.");
		if (Skript.isAcceptRegistrations())
			// Same as above
			throw new IllegalStateException("The documentation can't be uploaded while Skript is accepting registration.");
		if (Skript.getAddon(ADDON) == null)
			// It doesn't change nothing in the API, but just a check in case someone makes that mistake
			throw new IllegalStateException("You must register your plugin as addon using Skript.registerAddon(JavaPlugin)");
		// As said above, it will ran at same thread.
		HttpURLConnection skunity = null;
		try {
			// Filter the downloaded syntaxes which is the same as local syntaxes (same name, pattern, description...)
			filterSyntaxes();
			if (syntaxes.size() == 0) {//Nothing to add
				return;
			}
			// Add all syntaxes to a json array
			JsonArray array = new JsonArray();
			int added = 0, edited = 0;
			for (Syntax syntax : syntaxes) {
				array.add(getConverter().fromSyntax(syntax));
				if (syntax.get(Syntax.Field.ID) == null)
					added++;
				else
					edited++;
			}
			// Encode the syntaxes as an array data
			String data = "data=" + URLEncoder.encode(array.toString(), "UTF-8");
			skunity = (HttpURLConnection) new URL(method(API_FUNCTION_POST_ADDON_SYNTAX)).openConnection();
			skunity.addRequestProperty("Connection", "close");
			skunity.addRequestProperty("Content-Length", data.length() + "");
			skunity.setRequestProperty("Content-Type", "application/json");
			skunity.setRequestProperty("User-Agent", "skUnity API Documentation/" + VERSION);
			skunity.setRequestMethod("POST");
			skunity.setDoOutput(true);
			DataOutputStream output = new DataOutputStream(skunity.getOutputStream());
			output.write(data.getBytes("UTF-8"));
			output.flush();
			output.close();
			log(Level.INFO, "A total of " + added + " syntax(es) was(were) added and "  + edited + " edited." );
		} catch (UnknownHostException e){
			log(Level.WARNING, "Couldn't upload the syntaxes due to connection issue. Check your connection status.");
		} catch (IOException e) {
			log(Level.SEVERE, "A error occurred while sending documentation to skUnity");
			e.printStackTrace();
		} finally {
			if (skunity != null)
				skunity.disconnect();
		}
	}

	/**
	 * Load syntaxes from Skript.
	 */
	protected void loadSyntaxes() {
		if (automaticallyLoad != null)
			for (Syntax.Type type : automaticallyLoad) {
				Collection list = null;
				switch (type) {
					case EVENT: list = Skript.getEvents(); break;
					case CONDITION: list = Skript.getConditions(); break;
					case EFFECT: list = Skript.getEffects(); break;
					case EXPRESSION: list = Lists.newArrayList(Skript.getExpressions()); break;
					case TYPE: list = Classes.getClassInfos(); break;
				}
				// A kind of impossible.
				if (list == null)
					continue;
				for (Object info : list)
					addSyntax(getGetter().getSyntax(info));
			}
	}

	protected void filterSyntaxes() {
		// Load syntaxes from Skript
		loadSyntaxes();
		// Nothing in skUnity, so send it all as new.
		if (downloadedSyntax.size() == 0)
			return;
		syntaxes = syntaxes.stream().filter(syntax1 -> {
			for (Syntax syntax2 : downloadedSyntax) {
				if (!syntax1.get(Syntax.Field.TYPE).equals(syntax2.get(Syntax.Field.TYPE)))
					continue;
				if (StringUtils.equals(syntax1.get(Syntax.Field.NAME), syntax2.get(Syntax.Field.NAME))
						|| StringUtils.equalsPatterns(syntax1.get(Syntax.Field.PATTERN), syntax2.get(Syntax.Field.PATTERN))) {
					if (syntax1.equals(syntax2)) // It means that if they are the same, they don't need to be added/edited.
						return false;
					// It will copy the id of the downloaded syntax, to the new one.
					// So it will be edited instead of added.
					syntax1.set(Syntax.Field.ID, syntax2.get(Syntax.Field.ID));
					return true;
				}
			}
			// If it reaches here, it means it couldn't find one, so it will be added instead.
			return true;
		}).collect(Collectors.toList());
	}

	protected void log(Level lvl, String msg) {
		ADDON.getLogger().log(lvl,"[skUnity API] " + msg);
	}

	private String method(String method, String... args) {
		return String.format(API_BASE_URL, KEY) + String.format(method, args) ;
	}
}
