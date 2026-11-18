package com.tiktoklive;

import com.google.common.base.Splitter;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.awt.Color;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.Notifier;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Notification;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(
	name = "TikTok LIVE",
	description = "Shows your TikTok LIVE chat, gifts and follows in game",
	tags = {"tiktok", "live", "stream", "streamer", "chat", "overlay"}
)
public class TikTokLivePlugin extends Plugin
{
	static final int MAX_ENTRIES = 50;

	private static final Charset GAME_CHARSET = Charset.forName("windows-1252");

	private static final Splitter LIST_SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();

	@Inject
	private Client client;

	@Inject
	private TikTokLiveConfig config;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private TikTokChatOverlay overlay;

	@Inject
	private Notifier notifier;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	private volatile TikTokDirectClient source;
	private volatile TikTokPanel panel;
	private NavigationButton navButton;

	private final Deque<ChatEntry> entries = new ConcurrentLinkedDeque<>();

	private volatile Set<String> highlightWords = Collections.emptySet();
	private volatile Set<String> ignoredUsers = Collections.emptySet();

	@Getter
	private volatile long viewers;
	@Getter
	private volatile long totalLikes;
	private final AtomicLong diamonds = new AtomicLong();
	private final AtomicLong newFollowers = new AtomicLong();

	@Provides
	TikTokLiveConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TikTokLiveConfig.class);
	}

	@Override
	protected void startUp()
	{
		loadLists();
		resetStats();
		overlayManager.add(overlay);
		updatePanel();

		createSource();
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		removePanel();
		closeSource();
		entries.clear();
	}

	private synchronized void createSource()
	{
		closeSource();

		String username = config.username().trim();
		if (username.startsWith("@"))
		{
			username = username.substring(1);
		}
		if (username.isEmpty())
		{
			return;
		}

		source = new TikTokDirectClient(okHttpClient, gson, executor, this::onTikTokEvent,
			username, config.eulerApiKey().trim());
		source.tick();
	}

	private void updatePanel()
	{
		if (config.sidePanel())
		{
			if (panel == null)
			{
				panel = new TikTokPanel(this);
				panel.setEntries(new ArrayList<>(entries));
				navButton = NavigationButton.builder()
					.tooltip("TikTok LIVE")
					.icon(TikTokIcon.create(16))
					.priority(10)
					.panel(panel)
					.build();
				clientToolbar.addNavigation(navButton);
			}
		}
		else
		{
			removePanel();
		}
	}

	private void removePanel()
	{
		if (panel != null)
		{
			clientToolbar.removeNavigation(navButton);
			panel.dispose();
			panel = null;
			navButton = null;
		}
	}

	private synchronized void closeSource()
	{
		if (source != null)
		{
			source.close();
			source = null;
		}
	}

	@Schedule(period = 10, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void checkConnection()
	{
		TikTokDirectClient s = source;
		if (s != null)
		{
			s.tick();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!TikTokLiveConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		switch (event.getKey())
		{
			case "username":
				// a different LIVE, so the old totals no longer apply
				resetStats();
				createSource();
				break;
			case "eulerApiKey":
				createSource();
				break;
			case "sidePanel":
				SwingUtilities.invokeLater(this::updatePanel);
				break;
			case "highlightWords":
			case "ignoredUsers":
				loadLists();
				break;
		}
	}

	boolean isConnected()
	{
		TikTokDirectClient s = source;
		return s != null && s.isConnected();
	}

	String getConnectionStatus()
	{
		TikTokDirectClient s = source;
		return s != null ? s.getStatus() : "enter your TikTok username in the plugin settings";
	}

	long getDiamonds()
	{
		return diamonds.get();
	}

	long getNewFollowers()
	{
		return newFollowers.get();
	}

	void clearChat()
	{
		entries.clear();
		TikTokPanel p = panel;
		if (p != null)
		{
			p.clear();
		}
	}

	void resetStats()
	{
		viewers = 0;
		totalLikes = 0;
		diamonds.set(0);
		newFollowers.set(0);
	}

	/**
	 * Returns the newest messages to show in the overlay, oldest first.
	 */
	List<ChatEntry> getVisibleEntries()
	{
		int duration = config.overlayMessageDuration();
		long cutoff = System.currentTimeMillis() - duration * 1000L;
		int max = config.overlayMaxMessages();

		List<ChatEntry> visible = new ArrayList<>(max);
		for (ChatEntry entry : (Iterable<ChatEntry>) entries::descendingIterator)
		{
			if (visible.size() >= max || (duration > 0 && entry.getTimestamp() < cutoff))
			{
				break;
			}
			visible.add(entry);
		}
		Collections.reverse(visible);
		return visible;
	}

	private void loadLists()
	{
		highlightWords = toLowerSet(config.highlightWords(), false);
		ignoredUsers = toLowerSet(config.ignoredUsers(), true);
	}

	private static Set<String> toLowerSet(String list, boolean usernames)
	{
		Set<String> set = new HashSet<>();
		for (String s : LIST_SPLITTER.split(list))
		{
			if (usernames && s.startsWith("@"))
			{
				s = s.substring(1);
			}
			if (!s.isEmpty())
			{
				set.add(s.toLowerCase(Locale.ROOT));
			}
		}
		return set;
	}

	// Called from the OkHttp thread
	private void onTikTokEvent(JsonObject json)
	{
		if (source == null)
		{
			// a message that was in flight while the plugin was stopped
			return;
		}

		JsonElement typeElement = json.get("event");
		JsonElement dataElement = json.get("data");
		if (typeElement == null || !typeElement.isJsonPrimitive())
		{
			return;
		}

		String type = typeElement.getAsString();
		EventData data = new EventData(dataElement != null && dataElement.isJsonObject() ? dataElement.getAsJsonObject() : null);

		switch (type)
		{
			case "chat":
				onChat(data);
				break;
			case "gift":
				onGift(data);
				break;
			case "follow":
				newFollowers.incrementAndGet();
				if (config.showFollows())
				{
					addEvent(data, "followed you!");
				}
				break;
			case "subscribe":
				if (config.showSubscribes())
				{
					addEvent(data, "subscribed!");
				}
				break;
			case "share":
				if (config.showShares())
				{
					addEvent(data, "shared the LIVE");
				}
				break;
			case "member":
				if (config.showJoins())
				{
					addEvent(data, "joined");
				}
				break;
			case "like":
				onLike(data);
				break;
			case "roomUser":
				viewers = data.getLong("viewerCount");
				break;
			case "roomStats":
				// current totals, sent once when connecting
				long likes = data.getLong("totalLikeCount");
				if (likes > 0)
				{
					totalLikes = likes;
				}
				long viewerCount = data.getLong("viewerCount");
				if (viewerCount > 0)
				{
					viewers = viewerCount;
				}
				break;
			case "streamEnd":
				add(ChatEntry.Kind.EVENT, null, "TikTok", "The LIVE has ended", false);
				break;
			default:
				log.debug("Unhandled TikTok event {}", type);
		}
	}

	private void onChat(EventData data)
	{
		if (!config.showChat())
		{
			return;
		}

		String message = data.getString("comment");
		if (message.isEmpty() || isIgnored(data))
		{
			return;
		}

		if (config.hideCommands() && message.startsWith("!"))
		{
			return;
		}

		boolean highlighted = isHighlighted(message);
		String name = displayName(data);
		if (add(ChatEntry.Kind.CHAT, viewerKey(data), name, message, highlighted) && highlighted)
		{
			notify(config.notifyOnHighlight(), name + ": " + message);
		}
	}

	private void onGift(EventData data)
	{
		// Streakable gifts send an update for every combo step; only count the final one
		boolean streakable = data.getLong("giftType") == 1;
		if (streakable && !data.getBoolean("repeatEnd", true))
		{
			return;
		}

		long count = Math.max(1, data.getLong("repeatCount"));
		long value = data.getLong("diamondCount") * count;
		diamonds.addAndGet(value);

		if (!config.showGifts() || value < config.minGiftDiamonds() || isIgnored(data))
		{
			return;
		}

		String giftName = data.getString("giftName");
		if (giftName.isEmpty())
		{
			giftName = "a gift";
		}

		String message = "sent " + giftName + (count > 1 ? " x" + count : "")
			+ " (" + value + (value == 1 ? " diamond)" : " diamonds)");
		String name = displayName(data);
		if (add(ChatEntry.Kind.GIFT, viewerKey(data), name, message, false))
		{
			notify(config.notifyOnGift(), name + " " + message);
		}
	}

	private void onLike(EventData data)
	{
		long total = data.getLong("totalLikeCount");
		if (total > 0)
		{
			totalLikes = total;
		}

		if (config.showLikes() && !isIgnored(data))
		{
			long count = Math.max(1, data.getLong("likeCount"));
			addEvent(data, "sent " + count + (count == 1 ? " like" : " likes"));
		}
	}

	private void addEvent(EventData data, String message)
	{
		if (!isIgnored(data))
		{
			add(ChatEntry.Kind.EVENT, viewerKey(data), displayName(data), message, false);
		}
	}

	private boolean add(ChatEntry.Kind kind, String viewerKey, String name, String message, boolean highlighted)
	{
		name = sanitize(name);
		message = sanitize(message);
		if (message.isEmpty())
		{
			// e.g. emoji-only messages, which the game fonts cannot draw
			return false;
		}
		if (name.isEmpty())
		{
			name = "?";
		}

		ChatEntry entry = new ChatEntry(kind, name, viewerKey, message, highlighted, System.currentTimeMillis());
		entries.addLast(entry);
		while (entries.size() > MAX_ENTRIES)
		{
			entries.pollFirst();
		}

		if (config.chatboxEnabled())
		{
			clientThread.invokeLater(() -> addChatboxMessage(entry));
		}

		TikTokPanel p = panel;
		if (p != null)
		{
			p.addEntry(entry);
		}
		return true;
	}

	Color nameColor(String viewerKey)
	{
		return config.viewerColors() && viewerKey != null ? ViewerColors.forViewer(viewerKey) : config.nameColor();
	}

	Color messageColor(ChatEntry entry)
	{
		if (entry.isHighlighted())
		{
			return config.highlightColor();
		}

		switch (entry.getKind())
		{
			case GIFT:
				return config.giftColor();
			case EVENT:
				return config.eventColor();
			default:
				return config.messageColor();
		}
	}

	// Called on the client thread
	private void addChatboxMessage(ChatEntry entry)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		String chatName = Text.escapeJagex(entry.getName());
		if (config.viewerColorsChatbox() && entry.getViewerKey() != null)
		{
			Color color = ViewerColors.forViewer(entry.getViewerKey());
			boolean transparent = client.isResized() && client.getVarbitValue(VarbitID.CHATBOX_TRANSPARENCY) == 1;
			if (!transparent)
			{
				// the opaque chatbox is light, so use a darker shade of the viewer's color
				float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
				color = Color.getHSBColor(hsb[0], 1f, 0.5f);
			}
			chatName = ColorUtil.wrapWithColorTag(chatName, color);
		}

		String chatMessage = new ChatMessageBuilder()
			.append(entry.getKind() == ChatEntry.Kind.CHAT && !entry.isHighlighted() ? ChatColorType.NORMAL : ChatColorType.HIGHLIGHT)
			.append(Text.escapeJagex(entry.getMessage()))
			.build();

		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.FRIENDSCHAT)
			.sender("TikTok")
			.name(chatName)
			.runeLiteFormattedMessage(chatMessage)
			.timestamp((int) (System.currentTimeMillis() / 1000))
			.build());
	}

	private void notify(Notification notification, String message)
	{
		if (notification.isEnabled())
		{
			notifier.notify(notification, "TikTok: " + message);
		}
	}

	/**
	 * A stable id for the viewer, which does not change when they change their nickname.
	 */
	private static String viewerKey(EventData data)
	{
		String uniqueId = data.getString("uniqueId");
		if (!uniqueId.isEmpty())
		{
			return uniqueId.toLowerCase(Locale.ROOT);
		}
		String userId = data.getString("userId");
		if (!userId.isEmpty())
		{
			return userId;
		}
		String nickname = data.getString("nickname");
		return nickname.isEmpty() ? null : nickname;
	}

	private String displayName(EventData data)
	{
		String nickname = sanitize(data.getString("nickname"));
		String uniqueId = data.getString("uniqueId");
		String username = uniqueId.isEmpty() ? "" : "@" + uniqueId;
		if (config.nameDisplay() == NameDisplay.USERNAME && !username.isEmpty())
		{
			return username;
		}
		// Fall back to the @username when the nickname is made of characters the game can't draw
		return hasLetterOrDigit(nickname) || username.isEmpty() ? nickname : username;
	}

	private boolean isIgnored(EventData data)
	{
		Set<String> ignored = ignoredUsers;
		return !ignored.isEmpty()
			&& (ignored.contains(data.getString("uniqueId").toLowerCase(Locale.ROOT))
			|| ignored.contains(data.getString("nickname").toLowerCase(Locale.ROOT)));
	}

	private boolean isHighlighted(String message)
	{
		String lower = message.toLowerCase(Locale.ROOT);
		for (String word : highlightWords)
		{
			if (lower.contains(word))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Drops characters the game cannot draw and collapses whitespace. The game fonts only
	 * cover the Windows-1252 character set, so emoji, Arabic, CJK, Cyrillic and decorative
	 * unicode letters would otherwise show up as question marks.
	 */
	static String sanitize(String s)
	{
		CharsetEncoder encoder = GAME_CHARSET.newEncoder();
		StringBuilder sb = new StringBuilder(s.length());
		s.codePoints().forEach(cp ->
		{
			if (Character.isWhitespace(cp))
			{
				sb.append(' ');
			}
			else if (!Character.isISOControl(cp) && Character.isBmpCodePoint(cp) && encoder.canEncode((char) cp))
			{
				sb.appendCodePoint(cp);
			}
		});
		return sb.toString().replaceAll(" {2,}", " ").trim();
	}

	private static boolean hasLetterOrDigit(String s)
	{
		return s.codePoints().anyMatch(Character::isLetterOrDigit);
	}
}
