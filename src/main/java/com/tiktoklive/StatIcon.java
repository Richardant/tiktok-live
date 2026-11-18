package com.tiktoklive;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/**
 * Small vector icons for the LIVE stats, drawn in code so they stay sharp at any font size.
 */
enum StatIcon
{
	VIEWERS(new Color(0xE0E0E0))
		{
			@Override
			void paint(Graphics2D g, float x, float y, float s, boolean shadow)
			{
				// eye: almond outline with a pupil
				Path2D eye = new Path2D.Float();
				eye.moveTo(x, y + s * 0.5f);
				eye.quadTo(x + s * 0.5f, y - s * 0.05f, x + s, y + s * 0.5f);
				eye.quadTo(x + s * 0.5f, y + s * 1.05f, x, y + s * 0.5f);
				eye.closePath();
				g.setStroke(new BasicStroke(Math.max(1f, s / 9f)));
				g.draw(eye);
				g.fill(new Ellipse2D.Float(x + s * 0.32f, y + s * 0.32f, s * 0.36f, s * 0.36f));
			}
		},
	LIKES(new Color(0xFE2C55))
		{
			@Override
			void paint(Graphics2D g, float x, float y, float s, boolean shadow)
			{
				Path2D heart = new Path2D.Float();
				heart.moveTo(x + s * 0.5f, y + s * 0.95f);
				heart.curveTo(x - s * 0.15f, y + s * 0.5f, x + s * 0.05f, y - s * 0.1f, x + s * 0.5f, y + s * 0.25f);
				heart.curveTo(x + s * 0.95f, y - s * 0.1f, x + s * 1.15f, y + s * 0.5f, x + s * 0.5f, y + s * 0.95f);
				heart.closePath();
				g.fill(heart);
			}
		},
	DIAMONDS(new Color(0x25F4EE))
		{
			@Override
			void paint(Graphics2D g, float x, float y, float s, boolean shadow)
			{
				Path2D gem = new Path2D.Float();
				gem.moveTo(x + s * 0.22f, y + s * 0.08f);
				gem.lineTo(x + s * 0.78f, y + s * 0.08f);
				gem.lineTo(x + s, y + s * 0.38f);
				gem.lineTo(x + s * 0.5f, y + s * 0.98f);
				gem.lineTo(x, y + s * 0.38f);
				gem.closePath();
				g.fill(gem);

				// facets
				g.setColor(shadow ? Color.BLACK : getColor().darker());
				g.setStroke(new BasicStroke(Math.max(1f, s / 12f)));
				Path2D facets = new Path2D.Float();
				facets.moveTo(x, y + s * 0.38f);
				facets.lineTo(x + s, y + s * 0.38f);
				facets.moveTo(x + s * 0.32f, y + s * 0.38f);
				facets.lineTo(x + s * 0.5f, y + s * 0.98f);
				facets.lineTo(x + s * 0.68f, y + s * 0.38f);
				g.draw(facets);
			}
		},
	FOLLOWS(new Color(0x69DB7C))
		{
			@Override
			void paint(Graphics2D g, float x, float y, float s, boolean shadow)
			{
				// person with a plus sign
				g.fill(new Ellipse2D.Float(x + s * 0.12f, y + s * 0.05f, s * 0.38f, s * 0.38f));
				Shape body = new Ellipse2D.Float(x - s * 0.02f, y + s * 0.5f, s * 0.66f, s * 0.7f);
				Shape oldClip = g.getClip();
				g.clip(new Rectangle2D.Float(x - s, y, s * 3, s));
				g.fill(body);
				g.setClip(oldClip);

				float t = Math.max(1f, s / 7f);
				g.fill(new Rectangle2D.Float(x + s * 0.8f - t / 2, y + s * 0.3f, t, s * 0.42f));
				g.fill(new Rectangle2D.Float(x + s * 0.59f, y + s * 0.51f - t / 2, s * 0.42f, t));
			}
		};

	private final Color color;

	StatIcon(Color color)
	{
		this.color = color;
	}

	Color getColor()
	{
		return color;
	}

	abstract void paint(Graphics2D g, float x, float y, float size, boolean shadow);

	/**
	 * Draws the icon with a drop shadow, matching how RuneLite draws overlay text.
	 */
	void draw(Graphics2D g, int x, int y, int size)
	{
		g.setColor(Color.BLACK);
		paint(g, x + 1, y + 1, size, true);
		g.setColor(color);
		paint(g, x, y, size, false);
	}
}
