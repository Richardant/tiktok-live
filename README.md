# TikTok LIVE

Shows your TikTok LIVE chat, gifts, follows and stats inside RuneLite, so you can keep playing while you stream. No other app is needed.

## Features

- **Chat overlay**: a movable, resizable on-screen chat with word wrap, configurable width, font, colors, message count and how long messages stay visible
- **Color per viewer**: every viewer gets their own name color, so it is easy to tell people apart
- **Chatbox**: TikTok messages also appear in the in-game chatbox (optional)
- **Side panel**: optionally show the chat in its own RuneLite sidebar panel instead of the game view
- **Gifts, follows, subscriptions, shares, joins and likes**: each can be turned on or off, with a minimum value filter for gifts. Gift combos are shown once, with the total
- **LIVE stats**: viewers, likes, diamonds and new followers, shown with icons
- **Highlights**: messages containing your chosen words are colored and can send a notification
- **Gift notifications**
- **Filters**: ignore users (e.g. bots) and hide `!commands`
- Right-click the overlay to clear the chat or reset the stats

## Setup

1. In the plugin settings, enter your **TikTok username** and accept the warning.
2. Start your LIVE. The plugin connects within about 30 seconds.

While it is not connected, the overlay shows the reason, such as *"@you is not live"*. The plugin keeps retrying on its own.

## Privacy

TikTok does not offer a public chat API, and it requires LIVE connections to be signed. The plugin asks the [Euler Stream](https://www.eulerstream.com/) sign server to do this, so your IP address and the TikTok username you enter are sent to Euler Stream when connecting. Nothing is sent until you enter your username.

Without an API key, Euler Stream allows 30 connections per hour and 100 per day, which is plenty for normal use. Staying connected to a LIVE does not use any. If you reconnect very often, create a free account at eulerstream.com and paste your key into **Advanced > Euler Stream API key**.

## Limitations

- Read-only: you cannot send TikTok chat messages from the game.
- The game fonts only support Latin characters. Emoji and other scripts are removed from messages, and viewers whose nickname cannot be shown appear by their @username.
- The plugin depends on TikTok's internal protocol and may stop working if TikTok changes it.
