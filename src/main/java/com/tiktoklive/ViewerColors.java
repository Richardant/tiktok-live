package com.tiktoklive;

import java.awt.Color;

/**
 * Gives every viewer a stable color, so people are easy to tell apart in chat.
 */
final class ViewerColors
{
	// Bright colors that stay readable on a dark, semi-transparent background
	private static final Color[] PALETTE = {
		new Color(0xFF6B6B),
		new Color(0xFFA94D),
		new Color(0xFFD43B),
		new Color(0xA9E34B),
		new Color(0x51CF66),
		new Color(0x38D9A9),
		new Color(0x3BC9DB),
		new Color(0x4DABF7),
		new Color(0x748FFC),
		new Color(0x9775FA),
		new Color(0xDA77F2),
		new Color(0xF783AC),
		new Color(0xFF8787),
		new Color(0x63E6BE),
		new Color(0x66D9E8),
		new Color(0xFFC078),
	};

	private ViewerColors()
	{
	}

	static Color forViewer(String key)
	{
		// String.hashCode is specified, so a viewer keeps the same color across restarts.
		// Mix the bits so similar names still spread over the palette.
		int h = key.hashCode();
		h ^= h >>> 16;
		h *= 0x45D9F3B;
		h ^= h >>> 16;
		return PALETTE[Math.floorMod(h, PALETTE.length)];
	}
}
