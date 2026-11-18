package com.tiktoklive;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class SanitizeTest
{
	@Test
	public void keepsLatinText()
	{
		assertEquals("José ñandú ¿qué tal?", TikTokLivePlugin.sanitize("José ñandú ¿qué tal?"));
	}

	@Test
	public void dropsCharactersTheGameCannotDraw()
	{
		assertEquals("Ana", TikTokLivePlugin.sanitize("Ana ✨🔥"));
		assertEquals("", TikTokLivePlugin.sanitize("علي"));
		assertEquals("", TikTokLivePlugin.sanitize("小明"));
		assertEquals("", TikTokLivePlugin.sanitize("𝓐𝓵𝓮𝔁"));
		assertEquals("hola amigo", TikTokLivePlugin.sanitize("hola   😀  amigo"));
	}
}
