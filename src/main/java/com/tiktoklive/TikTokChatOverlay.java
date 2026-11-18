package com.tiktoklive;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.AllArgsConstructor;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.QuantityFormatter;

import static net.runelite.api.MenuAction.RUNELITE_OVERLAY;
import static net.runelite.api.MenuAction.RUNELITE_OVERLAY_CONFIG;
import static net.runelite.client.ui.overlay.OverlayManager.OPTION_CONFIGURE;

class TikTokChatOverlay extends Overlay
{
	private static final int PADDING = 4;
	private static final int HEADER_GAP = 3;
	private static final Color DISCONNECTED_COLOR = new Color(0xFF5050);
	private static final Color STATS_COLOR = new Color(0xF0F0F0);
	private static final Color DIVIDER_COLOR = new Color(255, 255, 255, 50);
	private static final int ICON_GAP = 3;
	private static final int STAT_GAP = 10;

	private final TikTokLivePlugin plugin;
	private final TikTokLiveConfig config;

	@AllArgsConstructor
	private static class Run
	{
		final String text;
		final int x;
		final int line;
		final Color color;
	}

	/**
	 * Word-wraps colored text into runs positioned on lines.
	 */
	private static class Layout
	{
		final FontMetrics fm;
		final int maxWidth;
		final List<Run> runs = new ArrayList<>();
		int x;
		int line;

		Layout(FontMetrics fm, int maxWidth)
		{
			this.fm = fm;
			this.maxWidth = maxWidth;
		}

		void append(String text, Color color)
		{
			// split after each space, keeping the space on the preceding word
			for (String word : text.split("(?<= )"))
			{
				appendWord(word, color);
			}
		}

		private void appendWord(String word, Color color)
		{
			if (x > 0 && x + fm.stringWidth(word.trim()) > maxWidth)
			{
				newLine();
			}

			// break words that are wider than a whole line
			while (fm.stringWidth(word.trim()) > maxWidth && word.length() > 1)
			{
				int end = 1;
				while (end < word.length() && fm.stringWidth(word.substring(0, end + 1)) <= maxWidth - x)
				{
					end++;
				}
				runs.add(new Run(word.substring(0, end), x, line, color));
				word = word.substring(end);
				newLine();
			}

			runs.add(new Run(word, x, line, color));
			x += fm.stringWidth(word);
		}

		void newLine()
		{
			x = 0;
			line++;
		}

		int lineCount()
		{
			return x > 0 ? line + 1 : line;
		}
	}

	@Inject
	TikTokChatOverlay(TikTokLivePlugin plugin, TikTokLiveConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setResizable(true);
		setMovable(true);
		addMenuEntry(RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "TikTok LIVE");
		addMenuEntry(RUNELITE_OVERLAY, "Clear chat", "TikTok LIVE", e -> plugin.clearChat());
		addMenuEntry(RUNELITE_OVERLAY, "Reset stats", "TikTok LIVE", e -> plugin.resetStats());
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.overlayEnabled())
		{
			return null;
		}

		boolean connected = plugin.isConnected();
		List<ChatEntry> visible = plugin.getVisibleEntries();

		String status = null;
		Map<StatIcon, Long> stats = new EnumMap<>(StatIcon.class);
		if (!connected)
		{
			String reason = plugin.getConnectionStatus();
			status = "TikTok: " + (reason != null ? reason : "not connected");
		}
		else if (config.overlayShowStats())
		{
			// only show the stats that have a value
			putIfPositive(stats, StatIcon.VIEWERS, plugin.getViewers());
			putIfPositive(stats, StatIcon.LIKES, plugin.getTotalLikes());
			putIfPositive(stats, StatIcon.DIAMONDS, plugin.getDiamonds());
			putIfPositive(stats, StatIcon.FOLLOWS, plugin.getNewFollowers());
		}

		if (visible.isEmpty() && status == null && stats.isEmpty())
		{
			return null;
		}

		graphics.setFont(config.overlayFont().getFont());
		FontMetrics fm = graphics.getFontMetrics();
		int lineHeight = fm.getHeight();

		Dimension preferred = getPreferredSize();
		int width = preferred != null && preferred.width > 0 ? preferred.width : config.overlayWidth();
		Layout layout = new Layout(fm, width - PADDING * 2);

		int headerLines = 0;
		if (status != null)
		{
			layout.append(status, DISCONNECTED_COLOR);
			layout.newLine();
			headerLines = layout.line;
		}
		else if (!stats.isEmpty())
		{
			// the stats row is drawn separately, keep its line free
			layout.newLine();
			headerLines = 1;
		}

		for (ChatEntry entry : visible)
		{
			layout.append(entry.getName() + (entry.getKind() == ChatEntry.Kind.CHAT ? ": " : " "), plugin.nameColor(entry.getViewerKey()));
			layout.append(entry.getMessage(), plugin.messageColor(entry));
			layout.newLine();
		}

		int gap = headerLines > 0 && !visible.isEmpty() ? HEADER_GAP : 0;
		int height = PADDING * 2 + layout.lineCount() * lineHeight + gap;

		graphics.setColor(config.backgroundColor());
		graphics.fillRect(0, 0, width, height);

		if (!stats.isEmpty())
		{
			drawStats(graphics, fm, stats);
			if (!visible.isEmpty())
			{
				int y = PADDING + lineHeight + gap / 2;
				graphics.setColor(DIVIDER_COLOR);
				graphics.drawLine(PADDING, y, width - PADDING, y);
			}
		}

		for (Run run : layout.runs)
		{
			int x = PADDING + run.x;
			int y = PADDING + run.line * lineHeight + fm.getAscent() + (run.line >= headerLines ? gap : 0);
			graphics.setColor(Color.BLACK);
			graphics.drawString(run.text, x + 1, y + 1);
			graphics.setColor(run.color);
			graphics.drawString(run.text, x, y);
		}

		return new Dimension(width, height);
	}

	private static void putIfPositive(Map<StatIcon, Long> stats, StatIcon icon, long value)
	{
		if (value > 0)
		{
			stats.put(icon, value);
		}
	}

	/**
	 * Draws the stats as icon + number pairs on the first line.
	 */
	private static void drawStats(Graphics2D graphics, FontMetrics fm, Map<StatIcon, Long> stats)
	{
		int iconSize = fm.getAscent() - 1;
		int baseline = PADDING + fm.getAscent();
		int iconY = baseline - iconSize;
		int x = PADDING;

		Object antialias = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		Stroke stroke = graphics.getStroke();
		for (Map.Entry<StatIcon, Long> stat : stats.entrySet())
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			stat.getKey().draw(graphics, x, iconY, iconSize);
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialias);
			graphics.setStroke(stroke);
			x += iconSize + ICON_GAP;

			String value = format(stat.getValue());
			graphics.setColor(Color.BLACK);
			graphics.drawString(value, x + 1, baseline + 1);
			graphics.setColor(STATS_COLOR);
			graphics.drawString(value, x, baseline);
			x += fm.stringWidth(value) + STAT_GAP;
		}
	}

	private static String format(long value)
	{
		return QuantityFormatter.quantityToRSDecimalStack((int) Math.min(value, Integer.MAX_VALUE));
	}
}
