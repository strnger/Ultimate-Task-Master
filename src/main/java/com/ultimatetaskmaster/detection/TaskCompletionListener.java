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
 * <h3>Architecture: Swappable Detection</h3>
 * This is ONE of potentially many detectors. The plugin subscribes to
 * {@link TaskCompletionEvent} and does not care who fires it. To add a new
 * detection method, create a class that posts TaskCompletionEvent on the EventBus.
 * See {@link TaskCompletionEvent} javadoc for the full contract.
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
 * <h3>Manual Testing</h3>
 * Send fake GAMEMESSAGE chat in-game via developer tools to bootstrap the location
 * database. The listener will record the player position for each "completed" task:
 * <pre>
 *   client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
 *       "Congratulations, you've completed an easy task: Cook Shrimp.", null);
 * </pre>
 *
 * <h3>Next Step: Varbit-Based Detection (WikiSync Pattern)</h3>
 * League tasks are stored as bitpacked VarPlayers (varps). Each varp holds 32 bits,
 * each bit = one task's completion flag. When a bit flips 0->1, that task was completed.
 *
 * <p>Known league task varp IDs (from wikisync-api/src/runelite/data/leagueTaskVarps.json):</p>
 * <pre>
 *   Group 1: varps 2616-2631 (16 varps, 512 task slots)
 *   Group 2: varps 2808-2835 (28 varps, 896 task slots)
 *   Group 3: varps 3339-3342 ( 4 varps, 128 task slots)
 *   Group 4: varps 4036-4049 (14 varps, 448 task slots)
 *   Total: 62 varps, 1,984 task slots (>1,589 tasks)
 * </pre>
 *
 * <p>Detection algorithm (from wikisync-api LeagueTransformer.ts):</p>
 * <pre>
 *   for each varp in LEAGUE_TASK_VARPS:
 *     for bit 0..31:
 *       if (varp & (1 << bit)) != 0:
 *         task at index (32 * varpIndex + bit) is completed
 * </pre>
 *
 * <p>To detect NEW completions, compare previous varp values with current on
 * VarbitChanged. See OverallDesign.md section 8 "Method 2" for full Java code.</p>
 *
 * <p>Ideally the varp list would come from a server manifest (WikiSync pattern:
 * server tells plugin what varps to read), so new leagues don't need plugin updates.</p>
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
