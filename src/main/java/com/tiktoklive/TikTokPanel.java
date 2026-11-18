package com.tiktoklive;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.QuantityFormatter;

/**
 * Sidebar panel showing the TikTok LIVE chat, for people who prefer it out of the game view.
 */
class TikTokPanel extends PluginPanel
{
	private static final int MAX_MESSAGES = 200;
	private static final int ICON_SIZE = 12;
	// width of the message text, leaving room for the padding and the scrollbar
	private static final int TEXT_WIDTH = PANEL_WIDTH - 40;

	private final TikTokLivePlugin plugin;
	private final JLabel statusLabel = new JLabel();
	private final Map<StatIcon, JLabel> statLabels = new EnumMap<>(StatIcon.class);
	private final JPanel messages = new JPanel();
	private final JScrollPane scrollPane;
	private final Timer refreshTimer;

	TikTokPanel(TikTokLivePlugin plugin)
	{
		super(false);
		this.plugin = plugin;

		setLayout(new BorderLayout(0, 6));
		setBorder(new EmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		// header: title, clear button, stats and connection status
		JLabel title = new JLabel("TikTok LIVE", new ImageIcon(TikTokIcon.create(16)), JLabel.LEFT);
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);

		JButton clear = new JButton("Clear");
		clear.setFocusable(false);
		clear.addActionListener(e -> plugin.clearChat());

		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setOpaque(false);
		titleRow.add(title, BorderLayout.WEST);
		titleRow.add(clear, BorderLayout.EAST);

		JPanel statsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		statsRow.setOpaque(false);
		for (StatIcon icon : StatIcon.values())
		{
			JLabel label = new JLabel(new ImageIcon(iconImage(icon)));
			label.setForeground(Color.WHITE);
			label.setIconTextGap(3);
			label.setVisible(false);
			statLabels.put(icon, label);
			statsRow.add(label);
		}

		statusLabel.setForeground(new Color(0xFF6B6B));
		statusLabel.setFont(FontManager.getRunescapeSmallFont());

		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setOpaque(false);
		titleRow.setAlignmentX(LEFT_ALIGNMENT);
		statsRow.setAlignmentX(LEFT_ALIGNMENT);
		statusLabel.setAlignmentX(LEFT_ALIGNMENT);
		header.add(titleRow);
		header.add(statsRow);
		header.add(statusLabel);
		add(header, BorderLayout.NORTH);

		// messages, newest at the bottom
		messages.setLayout(new BoxLayout(messages, BoxLayout.Y_AXIS));
		messages.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		wrapper.add(messages, BorderLayout.NORTH);

		scrollPane = new JScrollPane(wrapper);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		add(scrollPane, BorderLayout.CENTER);

		refreshTimer = new Timer(1000, e -> refreshStats());
		refreshTimer.start();
		refreshStats();
	}

	void dispose()
	{
		refreshTimer.stop();
	}

	void setEntries(List<ChatEntry> entries)
	{
		SwingUtilities.invokeLater(() ->
		{
			messages.removeAll();
			int start = Math.max(0, entries.size() - MAX_MESSAGES);
			for (ChatEntry entry : entries.subList(start, entries.size()))
			{
				messages.add(createRow(entry));
			}
			messages.revalidate();
			scrollToBottom();
		});
	}

	void addEntry(ChatEntry entry)
	{
		SwingUtilities.invokeLater(() ->
		{
			boolean atBottom = isAtBottom();
			messages.add(createRow(entry));
			while (messages.getComponentCount() > MAX_MESSAGES)
			{
				messages.remove(0);
			}
			messages.revalidate();
			if (atBottom)
			{
				scrollToBottom();
			}
		});
	}

	void clear()
	{
		SwingUtilities.invokeLater(() ->
		{
			messages.removeAll();
			messages.revalidate();
			messages.repaint();
		});
	}

	private JLabel createRow(ChatEntry entry)
	{
		String separator = entry.getKind() == ChatEntry.Kind.CHAT ? ": " : " ";
		String html = "<html><body style='width:" + TEXT_WIDTH + "px'>"
			+ "<span style='color:" + hex(plugin.nameColor(entry.getViewerKey())) + "'>"
			+ escape(entry.getName()) + separator + "</span>"
			+ "<span style='color:" + hex(plugin.messageColor(entry)) + "'>"
			+ escape(entry.getMessage()) + "</span></body></html>";

		JLabel row = new JLabel(html);
		row.setBorder(new EmptyBorder(3, 5, 3, 5));
		row.setAlignmentX(LEFT_ALIGNMENT);
		return row;
	}

	private void refreshStats()
	{
		boolean connected = plugin.isConnected();
		String status = connected ? null : plugin.getConnectionStatus();
		statusLabel.setText(status != null ? status : "");
		statusLabel.setVisible(status != null);

		long[] values = {plugin.getViewers(), plugin.getTotalLikes(), plugin.getDiamonds(), plugin.getNewFollowers()};
		StatIcon[] icons = StatIcon.values();
		for (int i = 0; i < icons.length; i++)
		{
			JLabel label = statLabels.get(icons[i]);
			boolean visible = connected && values[i] > 0;
			label.setVisible(visible);
			if (visible)
			{
				label.setText(QuantityFormatter.quantityToRSDecimalStack((int) Math.min(values[i], Integer.MAX_VALUE)));
			}
		}
	}

	private boolean isAtBottom()
	{
		JScrollBar bar = scrollPane.getVerticalScrollBar();
		return bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 20;
	}

	private void scrollToBottom()
	{
		SwingUtilities.invokeLater(() ->
		{
			JScrollBar bar = scrollPane.getVerticalScrollBar();
			bar.setValue(bar.getMaximum());
		});
	}

	private static BufferedImage iconImage(StatIcon icon)
	{
		BufferedImage image = new BufferedImage(ICON_SIZE + 1, ICON_SIZE + 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		icon.draw(g, 0, 0, ICON_SIZE);
		g.dispose();
		return image;
	}

	private static String hex(Color color)
	{
		return String.format("#%06x", color.getRGB() & 0xFFFFFF);
	}

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
