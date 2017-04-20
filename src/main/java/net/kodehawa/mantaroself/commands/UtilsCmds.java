package net.kodehawa.mantaroself.commands;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.TextChannel;
import net.kodehawa.lib.google.Crawler;
import net.kodehawa.mantaroself.commands.utils.data.UrbanData;
import net.kodehawa.mantaroself.commands.utils.data.WeatherData;
import net.kodehawa.mantaroself.commands.utils.data.YoutubeMp3Info;
import net.kodehawa.mantaroself.modules.CommandRegistry;
import net.kodehawa.mantaroself.modules.Commands;
import net.kodehawa.mantaroself.modules.RegisterCommand;
import net.kodehawa.mantaroself.modules.commands.Category;
import net.kodehawa.mantaroself.utils.DiscordUtils;
import net.kodehawa.mantaroself.utils.Utils;
import net.kodehawa.mantaroself.utils.commands.EmoteReference;
import net.kodehawa.mantaroself.utils.data.GsonDataManager;
import org.json.JSONArray;
import org.json.JSONObject;
import us.monoid.web.Resty;

import java.awt.Color;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.function.IntConsumer;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static net.kodehawa.mantaroself.MantaroSelf.prefix;
import static net.kodehawa.mantaroself.data.MantaroData.config;

@Slf4j
@RegisterCommand.Class
public class UtilsCmds {
	private static final Resty resty = new Resty();

	private static String dateGMT(String timezone) throws ParseException, NullPointerException {
		SimpleDateFormat dateGMT = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss");
		dateGMT.setTimeZone(TimeZone.getTimeZone(timezone));
		SimpleDateFormat dateLocal = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss");
		Date date1 = dateLocal.parse(dateGMT.format(new Date()));
		return DateFormat.getInstance().format(date1);
	}

	@RegisterCommand
	public static void googleSearch(CommandRegistry cr) {
		cr.register("google", Commands.newSimple(Category.UTILS)

			.code((thiz, event, content, args) -> {
				StringBuilder b = new StringBuilder();
				EmbedBuilder builder = new EmbedBuilder();
				List<Crawler.SearchResult> result = Crawler.get(content);
				for (int i = 0; i < 5 && i < result.size(); i++) {
					Crawler.SearchResult data = result.get(i);
					if (data != null)
						b.append('[').append(i + 1).append("] ").append(data.getTitle()).append("\n");
				}

				event.getChannel().sendMessage(builder.setDescription(b.toString()).build()).queue();

				IntConsumer selector = (c) -> {
					event.getChannel().sendMessage(EmoteReference.OK + "Result for " + content + ": " + result.get(c - 1).getUrl()).queue();

					event.getMessage().addReaction(EmoteReference.OK.getUnicode()).queue();
				};
				DiscordUtils.selectInt(event, result.size() + 1, selector);
			})
			.help((thiz, event) -> thiz.helpEmbed(event, "Google search")
				.setDescription("Searches on google.")
				.addField("Usage", prefix() + "google <query>", false)
				.addField("Parameters", "query: the search query", false)
				.build())
			.build());
	}

	@RegisterCommand
	public static void time(CommandRegistry cr) {
		cr.register("time", Commands.newSimple(Category.UTILS)

			.code((thiz, event, content, args) -> {
				try {
					if (content.startsWith("GMT")) {
						event.getChannel().sendMessage(EmoteReference.MEGA + "It's " + dateGMT(content) + " in the " + content + " " +
							"timezone").queue();
					} else {
						event.getChannel().sendMessage(EmoteReference.ERROR2 + "You didn't specify a valid timezone").queue();
					}
				} catch (Exception ignored) {
				}
			})
			.help((thiz, event) -> thiz.helpEmbed(event, "Time")
				.setDescription("Get the time in a specific timezone.\n"
					+ prefix() + "time [timezone]. Retrieves the time in the specified timezone [**Don't write a country**!].\n"
					+ "**Parameter specification**\n"
					+ "[timezone] A **valid** timezone [no countries!] between GMT-12 and GMT+14")
				.build())
			.build());
	}

	@RegisterCommand
	public static void translate(CommandRegistry cr) {
		cr.register("translate", Commands.newSimple(Category.UTILS)

			.code((thiz, event, content, args) -> {
				try {
					TextChannel channel = event.getChannel();

					if (!content.isEmpty()) {
						String sourceLang = args[0];
						String targetLang = args[1];
						String textToEncode = content.replace(args[0] + " " + args[1] + " ", "");
						String textEncoded = "";
						String translatorUrl2;

						try {
							textEncoded = URLEncoder.encode(textToEncode, "UTF-8");
						} catch (UnsupportedEncodingException ignored) {
						}

						String translatorUrl = String.format("https://translate.google.com/translate_a/" +
								"single?client=at&dt=t&dt=ld&dt=qca&dt=rm&dt=bd&dj=1&hl=es-ES&ie=UTF-8&oe=UTF-8&inputm=2" +
								"&otf=2&iid=1dd3b944-fa62-4b55-b330-74909a99969e&sl=%1s&tl=%2s&dt=t&q=%3s", sourceLang, targetLang,
							textEncoded);

						try {
							resty.identifyAsMozilla();
							translatorUrl2 = resty.text(translatorUrl).toString();
							JSONArray data = new JSONObject(translatorUrl2).getJSONArray("sentences");

							for (int i = 0; i < data.length(); i++) {
								JSONObject entry = data.getJSONObject(i);
								channel.sendMessage(":speech_balloon: " + "Translation for " + textToEncode + ": " + entry.getString
									("trans")).queue();
							}
						} catch (IOException e) {
							event.getChannel().sendMessage("I got an error while translating, Google doesn't like me " + EmoteReference.SAD
								.getDiscordNotation())
								.queue();
						}
					} else {
						thiz.onHelp(event);
					}
				} catch (Exception e) {
					event.getChannel().sendMessage("Error while fetching results. Google doesn't like us " + EmoteReference.SAD
						.getDiscordNotation())
						.queue();
				}
			})
			.help((thiz, event) -> thiz.helpEmbed(event, "Translation command")
				.setDescription("Translates the given sentence.\n"
					+ "**Usage:**\n"
					+ prefix() + "translate <sourcelang> <outputlang> <sentence>.\n"
					+ "**Parameter explanation**\n"
					+ "sourcelang: The language the sentence is written in. Use codes (english = en)\n"
					+ "outputlang: The language you want to translate to (french = fr, for example)\n"
					+ "sentence: The sentence to translate.")
				.setColor(Color.BLUE)
				.build())
			.build());
	}

	@RegisterCommand
	public static void urban(CommandRegistry cr) {
		cr.register("urban", Commands.newSimple(Category.UTILS)

			.code((thiz, event, content, args) -> {
				String beheadedSplit[] = content.split("->");
				EmbedBuilder embed = new EmbedBuilder();

				if (!content.isEmpty()) {
					long start = System.currentTimeMillis();
					String url = null;
					try {
						url = "http://api.urbandictionary.com/v0/define?term=" + URLEncoder.encode(beheadedSplit[0], "UTF-8");
					} catch (UnsupportedEncodingException ignored) {
					}
					String json = Utils.wgetResty(url);
					UrbanData data = GsonDataManager.GSON_PRETTY.fromJson(json, UrbanData.class);

					long end = System.currentTimeMillis() - start;
					//This shouldn't happen, but it fucking happened.
					if (beheadedSplit.length < 1) {
						return;
					}

					if (data.list.isEmpty()) {
						event.getChannel().sendMessage(EmoteReference.ERROR + "No results.").queue();
						return;
					}

					switch (beheadedSplit.length) {
						case 1:
							embed.setAuthor("Urban Dictionary definition for " + content, data.list.get(0).permalink, null)
								.setDescription("Main definition.")
								.setThumbnail("https://everythingfat.files.wordpress.com/2013/01/ud-logo.jpg")
								.setColor(Color.GREEN)
								.addField("Definition", data.list.get(0).definition, false)
								.addField("Example", data.list.get(0).example, false)
								.addField(":thumbsup:", data.list.get(0).thumbs_up, true)
								.addField(":thumbsdown:", data.list.get(0).thumbs_down, true)
								.setFooter("Information by Urban Dictionary (Process time: " + end + "ms)", null);
							event.getChannel().sendMessage(embed.build()).queue();
							break;
						case 2:
							int defn = Integer.parseInt(beheadedSplit[1]) - 1;
							String defns = String.valueOf(defn + 1);
							embed.setAuthor("Urban Dictionary definition for " + beheadedSplit[0], data.list.get(defn).permalink, null)
								.setThumbnail("https://everythingfat.files.wordpress.com/2013/01/ud-logo.jpg")
								.setDescription("Definition " + defns)
								.setColor(Color.GREEN)
								.addField("Definition", data.list.get(defn).definition, false)
								.addField("Example", data.list.get(defn).example, false)
								.addField(":thumbsup:", data.list.get(defn).thumbs_up, true)
								.addField(":thumbsdown:", data.list.get(defn).thumbs_down, true)
								.setFooter("Information by Urban Dictionary (Process time: " + end + "ms)", null);
							event.getChannel().sendMessage(embed.build()).queue();
							break;
						default:
							thiz.onHelp(event);
							break;
					}
				}
			})
			.help((thiz, event) -> thiz.helpEmbed(event, "Urban dictionary")
				.setColor(Color.CYAN)
				.setDescription("Retrieves definitions from **Urban Dictionary**.\n"
					+ "Usage: \n"
					+ prefix() + "urban <term>-><number>: Retrieve a definition based on the given parameters.\n"
					+ "Parameter description:\n"
					+ "term: The term you want to look up\n"
					+ "number: **OPTIONAL** Parameter defined with the modifier '->' after the term. You don't need to use it" +
					".\n"
					+ "e.g. putting 2 will fetch the second result on Urban Dictionary")
				.build())
			.build());
	}

	@RegisterCommand
	public static void weather(CommandRegistry cr) {
		if (config().get().weatherAppId() == null) {
			log.info("OpenWeatherMap AppId not defined. Anime Commands will not load.");
			return;
		}

		cr.register("weather", Commands.newSimple(Category.FUN)
			.code((thiz, event, content, args) -> {
				if (content.isEmpty()) {
					thiz.onHelp(event);
					return;
				}

				EmbedBuilder embed = new EmbedBuilder();
				try {
					long start = System.currentTimeMillis();
					String APP_ID = config().get().weatherAppId();
					String json = Utils.wget(String.format("http://api.openweathermap.org/data/2.5/weather?q=%s&appid=%s", URLEncoder
						.encode(content, "UTF-8"), APP_ID));
					WeatherData data = GsonDataManager.GSON_PRETTY.fromJson(json, WeatherData.class);

					String countryCode = data.sys.country;
					String status = data.getWeather().get(0).main;
					Double temp = data.getMain().getTemp();
					double pressure = data.getMain().getPressure();
					int hum = data.getMain().getHumidity();
					Double ws = data.getWind().speed;
					int clness = data.getClouds().all;

					Double finalTemperatureCelcius = temp - 273.15;
					Double finalTemperatureFarnheit = temp * 9 / 5 - 459.67;
					Double finalWindSpeedMetric = ws * 3.6;
					Double finalWindSpeedImperial = ws / 0.447046;
					long end = System.currentTimeMillis() - start;

					embed.setColor(Color.CYAN)
						.setTitle(":flag_" + countryCode.toLowerCase() + ":" + " Forecast information for " + content, null)
						.setDescription(status + " (" + clness + "% cloudiness)")
						.addField(":thermometer: Temperature", finalTemperatureCelcius.intValue() + "°C | " +
							finalTemperatureFarnheit.intValue() + "°F", true)
						.addField(":droplet: Humidity", hum + "%", true)
						.addBlankField(true)
						.addField(":wind_blowing_face: Wind Speed", finalWindSpeedMetric.intValue() + "km/h | " +
							finalWindSpeedImperial.intValue() + "mph", true)
						.addField("Pressure", pressure + "kPA", true)
						.addBlankField(true)
						.setFooter("Information provided by OpenWeatherMap (Process time: " + end + "ms)", null);
					event.getChannel().sendMessage(embed.build()).queue();
				} catch (Exception e) {
					event.getChannel().sendMessage("Error while fetching results.").queue();
					if (!(e instanceof NullPointerException))
						log.warn("Exception caught while trying to fetch weather data, maybe the API changed something?", e);
				}
			})
			.help((thiz, event) -> thiz.helpEmbed(event, "Weather command")
				.setDescription("This command retrieves information from OpenWeatherMap. Used to check **forecast information.**\n"
					+ "> Usage:\n"
					+ prefix() + "weather <city>,<countrycode>: Retrieves the forecast information for the given location.\n"
					+ "> Parameters:\n"
					+ "city: Your city name, e.g. New York\n"
					+ "countrycode: (OPTIONAL) The abbreviation for your country, for example US (USA) or MX (Mexico).")
				.build())
			.build());
	}

	@RegisterCommand
	public static void ytmp3(CommandRegistry cr) {
		cr.register("ytmp3", Commands.newSimple(Category.FUN)

			.code((thiz, event, content, args) -> {
				YoutubeMp3Info info = YoutubeMp3Info.forLink(content);

				if (info == null) {
					event.getChannel().sendMessage(":heavy_multiplication_x: Your link seems to be invalid.").queue();
					return;
				}

				if (info.error != null) {
					event.getChannel().sendMessage(":heavy_multiplication_x: I got an error while fetching that link``" + info.error
						+ "``").queue();
					return;
				}

				EmbedBuilder builder = new EmbedBuilder()
					.setAuthor(info.title, info.link, event.getAuthor().getEffectiveAvatarUrl())
					.setFooter("Powered by the youtubeinmp3.com API", null);

				try {
					int length = Integer.parseInt(info.length);
					builder.addField("Length",
						String.format(
							"%02d minutes, %02d seconds",
							SECONDS.toMinutes(length),
							length - MINUTES.toSeconds(SECONDS.toMinutes(length))
						),
						false
					);
				} catch (Exception ignored) {
				}

				event.getChannel().sendMessage(builder
					.addField("Download Link", "[Click Here!](" + info.link + ")", false)
					.build()
				).queue();
			})
			.help((thiz, event) -> thiz.helpEmbed(event, "Youtube MP3 command")
				.setDescription("Youtube video to MP3 converter")
				.addField("Usage", prefix() + "ytmp3 <youtube link>", true)
				.addField("Parameters", "youtube link: The link of the video to convert to MP3", true)
				.build())
			.build());
	}
}
