package com.tiktoklive;

import java.awt.Font;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import net.runelite.client.ui.FontManager;

@RequiredArgsConstructor
public enum FontType
{
	REGULAR("Regular", FontManager::getRunescapeFont),
	BOLD("Bold", FontManager::getRunescapeBoldFont),
	SMALL("Small", FontManager::getRunescapeSmallFont);

	private final String name;
	private final Supplier<Font> font;

	public Font getFont()
	{
		return font.get();
	}

	@Override
	public String toString()
	{
		return name;
	}
}
