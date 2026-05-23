import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class MyBot extends TelegramLongPollingBot {

    private static final String BOT_TOKEN    = "8954070748:AAHcSQT7k3z9RjmMsxlIm1mtDFlncl-28L8";
    private static final String BOT_USERNAME = "java_counter_bot";

    @Override public String getBotToken()    { return BOT_TOKEN; }
    @Override public String getBotUsername() { return BOT_USERNAME; }

    // ── Per-user language: "kh" or "en" ──
    private final Map<String, String>        userLang     = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> userCounters = new ConcurrentHashMap<>();
    private final ExecutorService            executor     = Executors.newCachedThreadPool();

    private String  lang(String chatId)  { return userLang.getOrDefault(chatId, "kh"); }
    private boolean isKh(String chatId)  { return lang(chatId).equals("kh"); }

    // ── Game state ──
    private static class GameState {
        int secret;
        int guesses = 0;
        GameState(int s) { this.secret = s; }
    }
    private final Map<String, GameState> activeGames = new ConcurrentHashMap<>();

    // ── Stats ──
    private static class UserStats {
        int  gamesPlayed = 0;
        int  gamesWon    = 0;
        long totalGuesses = 0;
        String firstSeen;
        UserStats(String d) { this.firstSeen = d; }
    }
    private final Map<String, UserStats> statsMap = new ConcurrentHashMap<>();

    private UserStats getStats(String chatId) {
        return statsMap.computeIfAbsent(chatId, k ->
            new UserStats(java.time.LocalDate.now().toString()));
    }
    private AtomicBoolean getCounter(String chatId) {
        return userCounters.computeIfAbsent(chatId, k -> new AtomicBoolean(false));
    }

    // ── Programming jokes (KH + EN pairs) ──
    private static final String[][] JOKES_KH = {
        {"😄 ហេតុអ្វីបានជា programmer ចូលចិត្ត dark mode?\nព្រោះ light ទាក់ bugs! 🐛",
         "😄 Why do programmers prefer dark mode?\nBecause light attracts bugs! 🐛"},
        {"😄 programmer ១ នាក់ស្ទាក់ក្នុង shower ហេតុអ្វី?\nព្រោះ shampoo ចែបៀន: 'Lather, Rinse, Repeat' គ្មាន exit condition! 🔄",
         "😄 A programmer is stuck in the shower — why?\nThe shampoo said: 'Lather, Rinse, Repeat' — no exit condition! 🔄"},
        {"😄 មានភាពខុសគ្នាអ្វីរវាង programmer និង pizza?\nPizza អាចចិញ្ចឹម family of four! 🍕",
         "😄 What's the difference between a programmer and a pizza?\nA pizza can feed a family of four! 🍕"},
        {"😄 99 bugs in the code… take one down, patch it around…\n127 bugs in the code 😅",
         "😄 99 bugs in the code… take one down, patch it around…\n127 bugs in the code 😅"},
        {"😄 ហេតុអ្វី Java developer ពាក់ glasses?\nព្រោះគេមិន C#! 👓",
         "😄 Why do Java developers wear glasses?\nBecause they don't C#! 👓"},
    };

    // ══════════════════════════════════════════
    //   TRANSLATIONS helper
    // ══════════════════════════════════════════
    private String kh(String chatId, String khText, String enText) {
        return isKh(chatId) ? khText : enText;
    }

    // ══════════════════════════════════════════
    //   KEYBOARD (4 rows) — always shown
    // ══════════════════════════════════════════
    private ReplyKeyboardMarkup buildKeyboard(String chatId) {
        boolean kh = isKh(chatId);

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("/count"));
        row1.add(new KeyboardButton("/random"));
        row1.add(new KeyboardButton("/stop"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("/game"));
        row2.add(new KeyboardButton("/quit"));
        row2.add(new KeyboardButton("/stats"));

        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton("/flip"));
        row3.add(new KeyboardButton("/dice"));
        row3.add(new KeyboardButton("/joke"));
        row3.add(new KeyboardButton("/clear"));

        KeyboardRow row4 = new KeyboardRow();
        row4.add(new KeyboardButton("/help"));
        row4.add(new KeyboardButton("/update"));
        row4.add(new KeyboardButton(kh ? "🌐 English" : "🌐 ខ្មែរ"));

        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setKeyboard(Arrays.asList(row1, row2, row3, row4));
        kb.setResizeKeyboard(true);
        kb.setOneTimeKeyboard(false);
        return kb;
    }

    // ══════════════════════════════════════════
    //   onUpdateReceived
    // ══════════════════════════════════════════
    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        String chatId = update.getMessage().getChatId().toString();
        String text   = update.getMessage().getText().trim();
        String name   = update.getMessage().getFrom().getFirstName();

        System.out.println("[" + name + " | " + chatId + "]: " + text);

        AtomicBoolean counting = getCounter(chatId);

        // ── Game guess interception ──
        if (activeGames.containsKey(chatId) && !text.startsWith("/")
                && !text.equals("🌐 English") && !text.equals("🌐 ខ្មែរ")) {
            handleGameGuess(chatId, text);
            return;
        }

        // ── Lang switch via keyboard button ──
        if (text.equals("🌐 English")) {
            userLang.put(chatId, "en");
            sendMsg(chatId, "🌐 Language switched to *English*!");
            return;
        }
        if (text.equals("🌐 ខ្មែរ")) {
            userLang.put(chatId, "kh");
            sendMsg(chatId, "🌐 ប្តូរជា *ភាសាខ្មែរ* រួចហើយ!");
            return;
        }

        switch (text.toLowerCase()) {

            case "/start":
                sendMsg(chatId, String.format(
                    kh(chatId,
                        "👋 សួស្តី *%s*!\n\nជ្រើសរើស Command ពី Menu ខាងក្រោម:",
                        "👋 Hello *%s*!\n\nPick a command from the menu below:"
                    ), name));
                break;

            case "/update":
                sendMsg(chatId, kh(chatId,
                    "🔄 Bot ត្រូវបាន Update រួចហើយ! Menu បានផ្ទុកឡើងវិញ ✅",
                    "🔄 Bot updated! Menu reloaded successfully ✅"));
                break;

            case "/lang":
                if (isKh(chatId)) {
                    userLang.put(chatId, "en");
                    sendMsg(chatId, "🌐 Language switched to *English*!");
                } else {
                    userLang.put(chatId, "kh");
                    sendMsg(chatId, "🌐 ប្តូរជា *ភាសាខ្មែរ* រួចហើយ!");
                }
                break;

            case "/help":
                sendMsg(chatId, kh(chatId,
                    "📋 *Command ទាំងអស់*\n\n" +
                    "▶️ /count  — រាប់លេខ 0–9 (3 threads)\n" +
                    "🎰 /random — លេខចៃដន្យ (3 threads)\n" +
                    "⏹️ /stop   — ឈប់ counter\n" +
                    "🎮 /game   — ទាយលេខ 1–100\n" +
                    "🏳️ /quit   — បោះបង់ game\n" +
                    "📊 /stats  — ស្ថិតិរបស់អ្នក\n" +
                    "🪙 /flip   — ក្បាលឬចុង\n" +
                    "🎲 /dice   — ទោះខ្នងឡើង\n" +
                    "😄 /joke   — លេងសើច programmer\n" +
                    "🗑️ /clear  — Reset ទាំងអស់\n" +
                    "🔄 /update — Reload Menu\n" +
                    "🌐 /lang   — ប្តូរភាសា",
                    "📋 *All Commands*\n\n" +
                    "▶️ /count  — Count 0–9 (3 threads)\n" +
                    "🎰 /random — Random numbers (3 threads)\n" +
                    "⏹️ /stop   — Stop counter\n" +
                    "🎮 /game   — Guess a number 1–100\n" +
                    "🏳️ /quit   — Quit current game\n" +
                    "📊 /stats  — Your game statistics\n" +
                    "🪙 /flip   — Flip a coin\n" +
                    "🎲 /dice   — Roll a die\n" +
                    "😄 /joke   — Random programmer joke\n" +
                    "🗑️ /clear  — Reset everything\n" +
                    "🔄 /update — Reload Menu\n" +
                    "🌐 /lang   — Toggle language"));
                break;

            case "/count":
                if (activeGames.containsKey(chatId)) {
                    sendMsg(chatId, kh(chatId,
                        "🎮 Game កំពុងលេង! វាយ /quit ជាមុន",
                        "🎮 Game in progress! Type /quit first."));
                } else if (counting.get()) {
                    sendMsg(chatId, kh(chatId,
                        "⚠️ Counter កំពុងដំណើរការ! វាយ /stop ជាមុន",
                        "⚠️ Counter is running! Type /stop first."));
                } else {
                    sendMsg(chatId, kh(chatId,
                        "▶️ ចាប់ផ្តើមរាប់...",
                        "▶️ Starting count..."));
                    startCounter(chatId, counting);
                }
                break;

            case "/random":
                if (activeGames.containsKey(chatId)) {
                    sendMsg(chatId, kh(chatId,
                        "🎮 Game កំពុងលេង! វាយ /quit ជាមុន",
                        "🎮 Game in progress! Type /quit first."));
                } else if (counting.get()) {
                    sendMsg(chatId, kh(chatId,
                        "⚠️ Counter កំពុងដំណើរការ! វាយ /stop ជាមុន",
                        "⚠️ Counter is running! Type /stop first."));
                } else {
                    sendMsg(chatId, kh(chatId,
                        "🎰 រង្វិលលេខចៃដន្យ...",
                        "🎰 Rolling random numbers..."));
                    startRandom(chatId, counting);
                }
                break;

            case "/stop":
                if (counting.compareAndSet(true, false)) {
                    sendMsg(chatId, kh(chatId, "⏹️ ឈប់រាប់ហើយ!", "⏹️ Stopped!"));
                } else {
                    sendMsg(chatId, kh(chatId,
                        "ℹ️ គ្មាន Counter ដំណើរការ",
                        "ℹ️ Nothing is running."));
                }
                break;

            case "/game":
                if (counting.get()) {
                    sendMsg(chatId, kh(chatId,
                        "⚠️ Counter កំពុងដំណើរការ! វាយ /stop ជាមុន",
                        "⚠️ Counter is running! Type /stop first."));
                } else if (activeGames.containsKey(chatId)) {
                    sendMsg(chatId, kh(chatId,
                        "🎮 Game កំពុងលេង! វាយ /quit ដើម្បីបោះបង់",
                        "🎮 Game in progress! Type /quit to quit."));
                } else {
                    int secret = new Random().nextInt(100) + 1;
                    activeGames.put(chatId, new GameState(secret));
                    getStats(chatId).gamesPlayed++;
                    sendMsg(chatId, kh(chatId,
                        "🎮 ខ្ញុំគិតលេខ 1–100!\nវាយលេខដើម្បីទាយ:",
                        "🎮 I'm thinking of a number 1–100!\nType your guess:"));
                }
                break;

            case "/quit":
                GameState gs = activeGames.remove(chatId);
                if (gs != null) {
                    sendMsg(chatId, String.format(kh(chatId,
                        "🏳️ បោះបង់! លេខគឺ *%d*\nវាយ /game លេងទៀត",
                        "🏳️ Quit! The number was *%d*\nType /game to play again"),
                        gs.secret));
                } else {
                    sendMsg(chatId, kh(chatId,
                        "ℹ️ គ្មាន Game ដំណើរការ",
                        "ℹ️ No game running."));
                }
                break;

            case "/stats":
                sendStats(chatId);
                break;

            case "/clear":
                boolean wasRunning = counting.getAndSet(false);
                boolean hadGame    = activeGames.remove(chatId) != null;
                if (!wasRunning && !hadGame) {
                    sendMsg(chatId, kh(chatId,
                        "ℹ️ គ្មានអ្វីត្រូវ Reset",
                        "ℹ️ Nothing to clear."));
                } else {
                    StringBuilder cleared = new StringBuilder(kh(chatId,
                        "🗑️ *Reset រួចហើយ!*\n", "🗑️ *Cleared!*\n"));
                    if (wasRunning) cleared.append(kh(chatId,
                        "• Counter ត្រូវបានឈប់\n", "• Counter stopped\n"));
                    if (hadGame) cleared.append(kh(chatId,
                        "• Game ត្រូវបានបញ្ចប់\n", "• Game ended\n"));
                    sendMsg(chatId, cleared.toString().trim());
                }
                break;

            case "/flip":
                boolean heads = new Random().nextBoolean();
                sendMsg(chatId, heads
                    ? kh(chatId, "🪙 *ក្បាល!* (Heads)", "🪙 *Heads!*")
                    : kh(chatId, "🪙 *ចុង!* (Tails)", "🪙 *Tails!*"));
                break;

            case "/dice":
                int roll = new Random().nextInt(6) + 1;
                String[] diceFaces = {"⚀","⚁","⚂","⚃","⚄","⚅"};
                sendMsg(chatId, String.format(kh(chatId,
                    "🎲 ទោះ: %s *%d*",
                    "🎲 You rolled: %s *%d*"),
                    diceFaces[roll - 1], roll));
                break;

            case "/joke":
                int j = new Random().nextInt(JOKES_KH.length);
                sendMsg(chatId, isKh(chatId) ? JOKES_KH[j][0] : JOKES_KH[j][1]);
                break;

            default:
                sendMsg(chatId, kh(chatId,
                    "❓ វាយ /help ដើម្បីមើល Commands ទាំងអស់",
                    "❓ Type /help to see all commands."));
        }
    }

    private void handleGameGuess(String chatId, String text) {
        GameState gs = activeGames.get(chatId);
        if (gs == null) return;
        int guess;
        try {
            guess = Integer.parseInt(text.trim());
            if (guess < 1 || guess > 100) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sendMsg(chatId, kh(chatId,
                "❓ វាយ *លេខ* ពី 1–100 ប៉ុណ្ណោះ!",
                "❓ Enter a *number* between 1–100!"));
            return;
        }
        gs.guesses++;
        if (guess < gs.secret) {
            sendMsg(chatId, kh(chatId,
                "📉 តូចពេក! ព្យាយាមម្តងទៀត:",
                "📉 Too low! Try again:"));
        } else if (guess > gs.secret) {
            sendMsg(chatId, kh(chatId,
                "📈 ធំពេក! ព្យាយាមម្តងទៀត:",
                "📈 Too high! Try again:"));
        } else {
            activeGames.remove(chatId);
            UserStats st = getStats(chatId);
            st.gamesWon++;
            st.totalGuesses += gs.guesses;
            sendMsg(chatId, String.format(kh(chatId,
                "🎉 ត្រូវហើយ! លេខ *%d* ក្នុង *%d* ការទាយ!\nវាយ /game លេងទៀត",
                "🎉 Correct! *%d* in *%d* guesses!\nType /game to play again"),
                gs.secret, gs.guesses));
        }
    }

    private void sendStats(String chatId) {
        UserStats st = getStats(chatId);
        String winRate    = st.gamesPlayed == 0 ? "0"
            : String.format("%.1f", (st.gamesWon * 100.0) / st.gamesPlayed);
        String avgGuesses = st.gamesWon == 0 ? "-"
            : String.format("%.1f", (double) st.totalGuesses / st.gamesWon);
        sendMsg(chatId, String.format(kh(chatId,
            "📊 *ស្ថិតិរបស់អ្នក*\n\n" +
            "🎮 លេងសរុប:     *%d* លើក\n" +
            "🏆 ឈ្នះ:         *%d* លើក\n" +
            "🎯 អត្រាឈ្នះ:    *%s%%*\n" +
            "🔢 ទាយជាមធ្យម:  *%s* ដង\n" +
            "📅 ថ្ងៃចូលមក:   *%s*",
            "📊 *Your Stats*\n\n" +
            "🎮 Games played:  *%d*\n" +
            "🏆 Games won:     *%d*\n" +
            "🎯 Win rate:      *%s%%*\n" +
            "🔢 Avg guesses:   *%s*\n" +
            "📅 First seen:    *%s*"),
            st.gamesPlayed, st.gamesWon, winRate, avgGuesses, st.firstSeen));
    }

    private void startCounter(String chatId, AtomicBoolean counting) {
        counting.set(true);
        int[]    delays = {400, 700, 1100};
        String[] names  = {"🟢 Box 1", "🔵 Box 2", "🟠 Box 3"};
        for (int i = 0; i < 3; i++) {
            final int idx = i; final String box = names[i];
            executor.submit(() -> {
                for (int n = 0; n <= 9 && counting.get(); n++) {
                    sendMsg(chatId, box + ": *" + n + "*");
                    try { Thread.sleep(delays[idx]); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                }
                if (counting.get())
                    sendMsg(chatId, "✅ " + box + " " + kh(chatId, "រាប់រួចហើយ!", "done!"));
            });
        }
    }

    private void startRandom(String chatId, AtomicBoolean counting) {
        counting.set(true);
        int[]    delays = {50, 80, 110};
        int[]    steps  = {20, 25, 30};
        String[] names  = {"🟢 Box 1", "🔵 Box 2", "🟠 Box 3"};
        for (int i = 0; i < 3; i++) {
            final int idx = i; final String box = names[i];
            executor.submit(() -> {
                Random rand = new Random(); int finalVal = 0;
                for (int step = 0; step < steps[idx] && counting.get(); step++) {
                    finalVal = rand.nextInt(10);
                    sendMsg(chatId, box + ": *" + finalVal + "*");
                    try {
                        long speed = delays[idx] + ((long) step * 20);
                        Thread.sleep(Math.min(speed, 500));
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                }
                sendMsg(chatId, "🎯 " + box + " " +
                    String.format(kh(chatId, "បានឈប់នៅ: *%d*", "stopped at: *%d*"), finalVal));
            });
        }
    }

    private void sendMsg(String chatId, String text) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText(text);
        msg.setParseMode("Markdown");
        msg.setReplyMarkup(buildKeyboard(chatId));
        try { execute(msg); }
        catch (TelegramApiException e) {
            System.err.println("Send error [" + chatId + "]: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════
    //   MAIN
    // ══════════════════════════════════════════
    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new java.io.PrintStream(System.err, true, StandardCharsets.UTF_8));

        // ── HTTP Server សម្រាប់ Render (Port 8080) ──
        new Thread(() -> {
            try {
                com.sun.net.httpserver.HttpServer server =
                    com.sun.net.httpserver.HttpServer.create(
                        new java.net.InetSocketAddress(8080), 0);
                server.createContext("/", exchange -> {
                    byte[] response = "Bot is running!".getBytes();
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.getResponseBody().close();
                });
                server.start();
                System.out.println("HTTP Server started on port 8080");
            } catch (Exception e) {
                System.err.println("HTTP Server error: " + e.getMessage());
            }
        }).start();

        try {
            MyBot bot = new MyBot();

            org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook del =
                new org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook();
            del.setDropPendingUpdates(true);
            try { bot.execute(del); System.out.println("Webhook cleared"); }
            catch (Exception e) { System.out.println("Webhook skip: " + e.getMessage()); }

            TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
            api.registerBot(bot);
            System.out.println("Bot is running: @" + BOT_USERNAME);
        } catch (TelegramApiException e) {
            System.err.println("Startup error: " + e.getMessage());
        }
    }
}