package com.tiktoklive;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NameDisplay
{
	NICKNAME("Nickname"),
	USERNAME("@username");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
