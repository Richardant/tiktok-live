package com.tiktoklive;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Notification;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(TikTokLiveConfig.GROUP)
public interface TikTokLiveConfig extends Config
{
	String GROUP = "tiktoklivechat";

	@ConfigSection(
		name = "General",
		description = "Your TikTok LIVE and where its chat is shown",
		position = 0
	)
	String connectionSection = "connection";

	@ConfigSection(
		name = "Events",
		description = "Which LIVE events are shown",
		position = 1
	)
	String eventsSection = "events";

	@ConfigSection(
		name = "Chat overlay",
		description = "On-screen chat overlay",
		position = 2
	)
	String overlaySection = "overlay";

	@ConfigSection(
		name = "Colors",
		description = "Overlay colors",
		position = 3,
		closedByDefault = true
	)
	String colorsSection = "colors";

	@ConfigSection(
		name = "Filters & alerts",
		description = "Highlights, ignored users and notifications",
		position = 4
	)
	String filtersSection = "filters";

	@ConfigSection(
		name = "Advanced",
		description = "Settings most people never need",
		position = 5,
		closedByDefault = true
	)
	String advancedSection = "advanced";

	// Connection

	@ConfigItem(
		keyName = "username",
		name = "TikTok username",
		description = "Your TikTok @username. The plugin connects to your LIVE once this is set."
			+ " TikTok requires the connection to be signed, which is done by the Euler Stream server",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = connectionSection,
		position = 0
	)
	default String username()
	{
		return "";
	}

	@ConfigItem(
		keyName = "sidePanel",
		name = "Show side panel",
		description = "Show the TikTok chat in a side panel, with its own button in the RuneLite sidebar",
		section = connectionSection,
		position = 1
	)
	default boolean sidePanel()
	{
		return false;
	}

	@ConfigItem(
		keyName = "nameDisplay",
		name = "Show viewers as",
		description = "Show the viewer's nickname or their @username",
		section = connectionSection,
		position = 2
	)
	default NameDisplay nameDisplay()
	{
		return NameDisplay.NICKNAME;
	}

	@ConfigItem(
		keyName = "chatboxEnabled",
		name = "Show in chatbox",
		description = "Show TikTok messages in the in-game chatbox",
		section = connectionSection,
		position = 3
	)
	default boolean chatboxEnabled()
	{
		return true;
	}

	// Events

	@ConfigItem(
		keyName = "showChat",
		name = "Chat messages",
		description = "Show chat comments",
		section = eventsSection,
		position = 0
	)
	default boolean showChat()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showGifts",
		name = "Gifts",
		description = "Show gifts",
		section = eventsSection,
		position = 1
	)
	default boolean showGifts()
	{
		return true;
	}

	@Range(min = 1)
	@ConfigItem(
		keyName = "minGiftDiamonds",
		name = "Minimum gift value",
		description = "Only show gifts worth at least this many diamonds in total",
		section = eventsSection,
		position = 2
	)
	default int minGiftDiamonds()
	{
		return 1;
	}

	@ConfigItem(
		keyName = "showFollows",
		name = "Follows",
		description = "Show new followers",
		section = eventsSection,
		position = 3
	)
	default boolean showFollows()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSubscribes",
		name = "Subscriptions",
		description = "Show new subscribers",
		section = eventsSection,
		position = 4
	)
	default boolean showSubscribes()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showShares",
		name = "Shares",
		description = "Show when someone shares the LIVE",
		section = eventsSection,
		position = 5
	)
	default boolean showShares()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showJoins",
		name = "Joins",
		description = "Show when someone joins the LIVE (can be spammy)",
		section = eventsSection,
		position = 6
	)
	default boolean showJoins()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showLikes",
		name = "Likes",
		description = "Show likes (can be spammy)",
		section = eventsSection,
		position = 7
	)
	default boolean showLikes()
	{
		return false;
	}

	// Overlay

	@ConfigItem(
		keyName = "overlayEnabled",
		name = "Show overlay",
		description = "Show an on-screen chat overlay. Alt-drag to move it, alt-drag the corner to resize",
		section = overlaySection,
		position = 0
	)
	default boolean overlayEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overlayShowStats",
		name = "Show LIVE stats",
		description = "Show viewers, likes, diamonds and new followers at the top of the overlay",
		section = overlaySection,
		position = 1
	)
	default boolean overlayShowStats()
	{
		return true;
	}

	@Range(min = 1, max = TikTokLivePlugin.MAX_ENTRIES)
	@ConfigItem(
		keyName = "overlayMaxMessages",
		name = "Max messages",
		description = "Maximum number of messages shown in the overlay",
		section = overlaySection,
		position = 2
	)
	default int overlayMaxMessages()
	{
		return 10;
	}

	@Range(min = 0)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "overlayMessageDuration",
		name = "Message duration",
		description = "How long messages stay on the overlay. 0 keeps them until pushed out",
		section = overlaySection,
		position = 3
	)
	default int overlayMessageDuration()
	{
		return 60;
	}

	@Range(min = 100, max = 800)
	@Units(Units.PIXELS)
	@ConfigItem(
		keyName = "overlayWidth",
		name = "Width",
		description = "Overlay width, used until the overlay is resized with alt-drag",
		section = overlaySection,
		position = 4
	)
	default int overlayWidth()
	{
		return 260;
	}

	@ConfigItem(
		keyName = "overlayFont",
		name = "Font",
		description = "Overlay font",
		section = overlaySection,
		position = 5
	)
	default FontType overlayFont()
	{
		return FontType.REGULAR;
	}

	// Colors

	@Alpha
	@ConfigItem(
		keyName = "backgroundColor",
		name = "Background",
		description = "Overlay background color",
		section = colorsSection,
		position = 0
	)
	default Color backgroundColor()
	{
		return new Color(0, 0, 0, 140);
	}

	@ConfigItem(
		keyName = "viewerColors",
		name = "Color per viewer",
		description = "Give every viewer their own name color in the overlay, so it is easier to tell people apart",
		section = colorsSection,
		position = 1
	)
	default boolean viewerColors()
	{
		return true;
	}

	@ConfigItem(
		keyName = "viewerColorsChatbox",
		name = "Color per viewer in chatbox",
		description = "Also color viewer names in the in-game chatbox."
			+ " Off by default because the chatbox already uses colors for other chat channels",
		section = colorsSection,
		position = 2
	)
	default boolean viewerColorsChatbox()
	{
		return false;
	}

	@ConfigItem(
		keyName = "nameColor",
		name = "Names",
		description = "Color of viewer names when 'Color per viewer' is off",
		section = colorsSection,
		position = 3
	)
	default Color nameColor()
	{
		return new Color(0x25F4EE);
	}

	@ConfigItem(
		keyName = "messageColor",
		name = "Messages",
		description = "Color of chat messages",
		section = colorsSection,
		position = 4
	)
	default Color messageColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "giftColor",
		name = "Gifts",
		description = "Color of gift messages",
		section = colorsSection,
		position = 5
	)
	default Color giftColor()
	{
		return new Color(0xFFD700);
	}

	@ConfigItem(
		keyName = "eventColor",
		name = "Events",
		description = "Color of follows, shares, joins, likes and subscriptions",
		section = colorsSection,
		position = 6
	)
	default Color eventColor()
	{
		return new Color(0xFE2C55);
	}

	@ConfigItem(
		keyName = "highlightColor",
		name = "Highlights",
		description = "Color of messages containing a highlight word",
		section = colorsSection,
		position = 7
	)
	default Color highlightColor()
	{
		return new Color(0x7CFC00);
	}

	// Filters & alerts

	@ConfigItem(
		keyName = "highlightWords",
		name = "Highlight words",
		description = "Comma-separated words. Messages containing any of them are highlighted",
		section = filtersSection,
		position = 0
	)
	default String highlightWords()
	{
		return "";
	}

	@ConfigItem(
		keyName = "notifyOnHighlight",
		name = "Notify on highlight",
		description = "Send a notification when a message contains a highlight word",
		section = filtersSection,
		position = 1
	)
	default Notification notifyOnHighlight()
	{
		return Notification.OFF;
	}

	@ConfigItem(
		keyName = "notifyOnGift",
		name = "Notify on gift",
		description = "Send a notification for gifts that are shown",
		section = filtersSection,
		position = 2
	)
	default Notification notifyOnGift()
	{
		return Notification.OFF;
	}

	@ConfigItem(
		keyName = "ignoredUsers",
		name = "Ignored users",
		description = "Comma-separated @usernames or nicknames whose messages are hidden (e.g. bots)",
		section = filtersSection,
		position = 3
	)
	default String ignoredUsers()
	{
		return "";
	}

	@ConfigItem(
		keyName = "hideCommands",
		name = "Hide !commands",
		description = "Hide chat messages that start with '!'",
		section = filtersSection,
		position = 4
	)
	default boolean hideCommands()
	{
		return false;
	}

	// Advanced

	@ConfigItem(
		keyName = "eulerApiKey",
		name = "Euler Stream API key",
		description = "Optional. Without a key you can connect up to 30 times per hour and 100 times per day,"
			+ " which is plenty unless you reconnect very often. Get a free key at eulerstream.com",
		secret = true,
		section = advancedSection,
		position = 0
	)
	default String eulerApiKey()
	{
		return "";
	}
}
