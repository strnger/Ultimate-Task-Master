package com.ultimatetaskmaster.detection;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Detects task completions via GAMEMESSAGE chat and fires {@link TaskCompletionEvent}s.
 *
 * <h3>Pattern: Dink LeaguesNotifier</h3>
 * Regex and message handling copied from Dink's LeaguesNotifier (proven in production).
 * Source: {@code examples/DinkPlugin/src/main/java/dinkplugin/notifiers/LeaguesNotifier.java}
 * <ul>
 *   <li>Messages sanitized via {@code Text.removeTags()} (same as Dink's Utils.sanitize())</li>
 *   <li>Handles "double-pop" — multiple tasks completing in one tick</li>
 *   <li>Queues on ChatMessage, flushes on GameTick for stable player position</li>
 * </ul>
 *
 * @see TaskCompletionEvent
 */
@Singleton
@Slf4j
public class TaskCompletionListener
{
	/**
	 * Exact regex from Dink's LeaguesNotifier — proven in production.
	 *
	 * Matches: "Congratulations, you've completed an easy task: 1 Sarachnis Kill."
	 * Groups:  tier="easy", task="1 Sarachnis Kill"
	 *
	 * The "an?" handles "a medium task" vs "an easy task" vs "an elite task".
	 * The trailing "\\." matches the period at the end of the message.
	 */
	static final Pattern TASK_PATTERN = Pattern.compile(
		"Congratulations, you've completed an? (?<tier>\\w+) task: (?<task>.+)\\."
	);

	private final Client client;
	private final EventBus eventBus;

	/**
	 * Queue for completions detected within a single tick.
	 * Handles the "double-pop" problem: if two tasks complete simultaneously,
	 * both chat messages arrive in the same tick. We queue them and process
	 * on the next GameTick so we capture stable player position.
	 */
	private final Deque<PendingCompletion> pending = new ArrayDeque<>();

	@Inject
	public TaskCompletionListener(Client client, EventBus eventBus)
	{
		this.client = client;
		this.eventBus = eventBus;
	}

	public void register()
	{
		eventBus.register(this);
	}

	public void unregister()
	{
		eventBus.unregister(this);
		pending.clear();
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		// Strip RuneLite formatting tags — same as Dink's Utils.sanitize()
		String stripped = Text.removeTags(event.getMessage());

		Matcher matcher = TASK_PATTERN.matcher(stripped);
		if (matcher.find())
		{
			String tier = matcher.group("tier").toLowerCase();
			String taskName = matcher.group("task");
			pending.add(new PendingCompletion(taskName, tier));
			log.debug("Task completion chat detected: tier={}, task='{}'", tier, taskName);
		}
	}

	/**
	 * Process pending completions on the game tick boundary.
	 * This ensures stable player position and handles multiple completions per tick.
	 */
	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (pending.isEmpty())
		{
			return;
		}

		if (client.getLocalPlayer() == null)
		{
			pending.clear();
			return;
		}

		WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();
		long now = System.currentTimeMillis();

		while (!pending.isEmpty())
		{
			PendingCompletion completion = pending.poll();

			TaskCompletionEvent completionEvent = new TaskCompletionEvent(
				completion.getTaskName(),
				completion.getTier(),
				playerLocation,
				now
			);

			log.info("Task completed: [{}] '{}' at {}", completion.getTier(), completion.getTaskName(), playerLocation);
			eventBus.post(completionEvent);
		}
	}

	/**
	 * A parsed but not-yet-processed task completion.
	 * Holds the data extracted from the chat message until the next GameTick.
	 */
	@Value
	private static class PendingCompletion
	{
		String taskName;
		String tier;
	}
}
