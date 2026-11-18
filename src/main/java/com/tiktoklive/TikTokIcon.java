package com.tiktoklive;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/**
 * Draws the sidebar icon: a music note with the cyan and pink offsets TikTok is known for.
 */
final class TikTokIcon
{
	private static final Color CYAN = new Color(0x25F4EE);
	private static final Color PINK = new Color(0xFE2C55);

	private TikTokIcon()
	{
	}

	static BufferedImage create(int size)
	{
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

		float offset = Math.max(1f, size / 16f);
		drawNote(g, size, -offset / 2, -offset / 2, CYAN);
		drawNote(g, size, offset / 2, offset / 2, PINK);
		drawNote(g, size, 0, 0, Color.WHITE);

		g.dispose();
		return image;
	}

	private static void drawNote(Graphics2D g, float s, float dx, float dy, Color color)
	{
		g.setColor(color);
		g.setStroke(new BasicStroke(s * 0.14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

		// stem and flag
		Path2D note = new Path2D.Float();
		note.moveTo(dx + s * 0.56f, dy + s * 0.68f);
		note.lineTo(dx + s * 0.56f, dy + s * 0.12f);
		note.quadTo(dx + s * 0.62f, dy + s * 0.34f, dx + s * 0.84f, dy + s * 0.36f);
		g.draw(note);

		// head
		float r = s * 0.17f;
		g.draw(new Ellipse2D.Float(dx + s * 0.39f - r, dy + s * 0.70f - r, r * 2, r * 2));
	}
}
