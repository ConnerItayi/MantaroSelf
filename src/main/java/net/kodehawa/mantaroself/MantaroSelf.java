package net.kodehawa.mantaroself;

import br.com.brjdevs.java.utils.extensions.Async;
import frederikam.jca.JCA;
import frederikam.jca.JCABuilder;
import lombok.Getter;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.JDAInfo;
import net.kodehawa.mantaroself.core.CommandProcessor;
import net.kodehawa.mantaroself.core.LoadState;
import net.kodehawa.mantaroself.core.MantaroEventManager;
import net.kodehawa.mantaroself.core.listeners.command.CommandListener;
import net.kodehawa.mantaroself.core.listeners.operations.InteractiveOperations;
import net.kodehawa.mantaroself.data.Config;
import net.kodehawa.mantaroself.data.MantaroData;
import net.kodehawa.mantaroself.log.SimpleLogToSLF4JAdapter;
import net.kodehawa.mantaroself.modules.CommandRegistry;
import net.kodehawa.mantaroself.modules.HasPostLoad;
import net.kodehawa.mantaroself.modules.RegisterCommand;
import org.reflections.Reflections;

import java.io.PrintStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;

import static net.kodehawa.mantaroself.MantaroInfo.VERSION;
import static net.kodehawa.mantaroself.core.LoadState.*;
import static net.kodehawa.mantaroself.data.MantaroData.config;

@Slf4j
public class MantaroSelf implements JDA {
	public static final boolean DEBUG = System.getProperty("mantaro.debug", null) != null;
	public static JCA CLEVERBOT;
	private static MantaroSelf instance;
	private static LoadState status = PRELOAD;

	public static MantaroSelf getInstance() {
		return instance;
	}

	public static LoadState getLoadStatus() {
		return status;
	}

	public static void main(String[] args) {
		if (System.getProperty("mantaro.verbose", null) != null) {
			System.setOut(new PrintStream(System.out) {
				@Override
				public void println(String s) {
					StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
					String current = stackTrace[2].toString();
					int i = 3;
					while ((current.startsWith("sun.") || current.startsWith("java.")) && i < stackTrace.length)
						current = stackTrace[i++].toString();
					super.println("[" + current + "]: " + s);
				}
			});
			System.setErr(new PrintStream(System.err) {
				@Override
				public void println(String s) {
					StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
					String current = stackTrace[2].toString();
					int i = 3;
					while ((current.startsWith("sun.") || current.startsWith("java.")) && i < stackTrace.length)
						current = stackTrace[i++].toString();
					super.println("[" + current + "]: " + s);
				}
			});
		}

		try {
			instance = new MantaroSelf();
		} catch (Exception e) {
			log.error("Could not complete Main Thread routine!", e);
			log.error("Cannot continue! Exiting program...");
			System.exit(-1);
		}
	}

	@Getter
	@Delegate //I love Lombok so much
	private final JDA jda;

	private MantaroSelf() throws Exception {
		SimpleLogToSLF4JAdapter.install();
		log.info("MantaroSelf starting...");

		Config config = MantaroData.config().get();
		Future<Set<Class<?>>> classesAsync = Async.future(() -> new Reflections("net.kodehawa.mantaroself.commands").getTypesAnnotatedWith(RegisterCommand.Class.class));
		Async.thread("CleverBot Builder", () -> CLEVERBOT = new JCABuilder().setUser(config.cleverbotUser).setKey(config.cleverbotKey).buildBlocking());

		status = LOADING;
		jda = new JDABuilder(AccountType.CLIENT)
			.setAudioEnabled(false)
			.setToken(config().get().token)
			.setEventManager(new MantaroEventManager())
			.addEventListener(new CommandListener(), InteractiveOperations.listener())
			.setAutoReconnect(true)
			.setCorePoolSize(5)
			.buildBlocking();

		status = LOADED;
		log.info("[-=-=-=-=-=- SELFBOT STARTED -=-=-=-=-=-]");
		log.info("Started selfbot " + VERSION + " on JDA " + JDAInfo.VERSION);

		MantaroData.config().save();

		Set<HasPostLoad> modules = new HashSet<>();
		for (Class<?> c : classesAsync.get()) {
			try {
				Object instance = null;
				if (HasPostLoad.class.isAssignableFrom(c)) {
					instance = c.newInstance();
					modules.add((HasPostLoad) instance);
				}
				for (Method m : c.getMethods()) {
					if (m.getAnnotation(RegisterCommand.class) == null) continue;
					if (!Modifier.isStatic(m.getModifiers()) && instance == null) {
						instance = c.newInstance();
					}
					Class<?>[] params = m.getParameterTypes();
					if (params.length != 1 || params[0] != CommandRegistry.class) {
						throw new IllegalArgumentException("Invalid method: " + m);
					}
					m.invoke(instance, CommandProcessor.REGISTRY);
				}
			} catch (InstantiationException e) {
				log.error("Cannot initialize a command", e);
			} catch (IllegalAccessException e) {
				log.error("Cannot access a command class!", e);
			}
		}

		status = POSTLOAD;
		log.info("Finished loading basic components. Status is now set to POSTLOAD");
		modules.forEach(HasPostLoad::onPostLoad);

		log.info("Loaded " + CommandProcessor.REGISTRY.commands().size() + " commands.");
	}
}
