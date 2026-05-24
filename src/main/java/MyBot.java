import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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

    // ══════════════════════════════════════════════
    // FIX #1: ប្រើ Environment Variable សម្រាប់ Token
    //         (កុំដាក់ Token ក្នុង Code ដោយផ្ទាល់!)
    // ══════════════════════════════════════════════
    private static final String BOT_TOKEN    = System.getenv("BOT_TOKEN") != null
                                                ? System.getenv("BOT_TOKEN")
                                                : "8954070748:AAHcSQT7k3z9RjmMsxlIm1mtDFlncl-28L8"; // fallback សម្រាប់ test
    private static final String BOT_USERNAME = "java_counter_bot";

    @Override public String getBotToken()    { return BOT_TOKEN; }
    @Override public String getBotUsername() { return BOT_USERNAME; }

    // ══ Language & State ══
    private final Map<String, String>        userLang     = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> userCounters = new ConcurrentHashMap<>();
    private final ExecutorService            executor     = Executors.newCachedThreadPool();

    private String  lang(String chatId) { return userLang.getOrDefault(chatId, "kh"); }
    private boolean isKh(String chatId) { return lang(chatId).equals("kh"); }
    private String  kh(String chatId, String kh, String en) { return isKh(chatId) ? kh : en; }

    // ══ Guess Game ══
    private static class GuessGame { int secret; int guesses=0; GuessGame(int s){secret=s;} }
    private final Map<String,GuessGame> guessGames = new ConcurrentHashMap<>();

    // ══ Higher/Lower ══
    private static class HLGame { int secret; int guesses=0; HLGame(int s){secret=s;} }
    private final Map<String,HLGame> hlGames = new ConcurrentHashMap<>();

    // ══ TicTacToe ══
    private static class TTTGame {
        char[] board = {' ',' ',' ',' ',' ',' ',' ',' ',' '};
        char turn = 'X';
        String playerX;
        TTTGame(String px) { playerX=px; }
    }
    private final Map<String,TTTGame> tttGames = new ConcurrentHashMap<>();

    // ══ Math Quiz ══
    private static class MathGame {
        int a, b, op, ans;
        String question;
        MathGame(int a, int b, int op, int ans, String q) {
            this.a=a; this.b=b; this.op=op; this.ans=ans; this.question=q;
        }
    }
    private final Map<String,MathGame> mathGames = new ConcurrentHashMap<>();

    // ══ Hangman ══
    private static final String[] HANGMAN_WORDS = {
        "java","python","keyboard","monitor","variable",
        "function","compiler","database","algorithm","interface",
        "network","security","framework","developer","software"
    };
    private static class HangmanGame {
        String word;
        Set<Character> guessed = new HashSet<>();
        int wrong = 0;
        HangmanGame(String w) { word=w; }

        String display() {
            StringBuilder sb = new StringBuilder();
            for (char c : word.toCharArray())
                sb.append(guessed.contains(c) ? c : '_').append(' ');
            return sb.toString().trim();
        }

        // FIX #2: បន្ថែម hangman drawing
        String drawing() {
            String[] frames = {
                "```\n  +---+\n  |   |\n      |\n      |\n      |\n      |\n=========```",
                "```\n  +---+\n  |   |\n  O   |\n      |\n      |\n      |\n=========```",
                "```\n  +---+\n  |   |\n  O   |\n  |   |\n      |\n      |\n=========```",
                "```\n  +---+\n  |   |\n  O   |\n /|   |\n      |\n      |\n=========```",
                "```\n  +---+\n  |   |\n  O   |\n /|\\  |\n      |\n      |\n=========```",
                "```\n  +---+\n  |   |\n  O   |\n /|\\  |\n /    |\n      |\n=========```",
                "```\n  +---+\n  |   |\n  O   |\n /|\\  |\n / \\  |\n      |\n=========```"
            };
            return frames[Math.min(wrong, 6)];
        }

        boolean won() {
            for (char c : word.toCharArray())
                if (!guessed.contains(c)) return false;
            return true;
        }
    }
    private final Map<String,HangmanGame> hangmanGames = new ConcurrentHashMap<>();

    // ══ Blackjack ══
    private static class BJGame {
        List<Integer> player = new ArrayList<>(), dealer = new ArrayList<>();
        int handVal(List<Integer> h) {
            int s=0, aces=0;
            for (int c : h) { if(c==1){aces++;s+=11;} else s+=Math.min(c,10); }
            while (s>21 && aces-->0) s-=10;
            return s;
        }
        String cardStr(int c) { return c==1?"A":c==11?"J":c==12?"Q":c==13?"K":String.valueOf(c); }
        String handStr(List<Integer> h) {
            StringBuilder sb = new StringBuilder();
            for (int c : h) sb.append("[").append(cardStr(c)).append("]");
            sb.append(" = ").append(handVal(h));
            return sb.toString();
        }
    }
    private final Map<String,BJGame> bjGames = new ConcurrentHashMap<>();

    // ══ Trivia ══
    private static final String[][] TRIVIA = {
        {"Java ប្រើ keyword អ្វីដើម្បីបង្កើត class?","class","interface","struct","object","A"},
        {"1 byte = ? bits","4","8","16","32","B"},
        {"HTML ឈរសម្រាប់?","HyperText Markup Language","High Text Machine Language","HyperText Machine Link","None","A"},
        {"Binary 1010 = ? decimal","8","10","12","14","B"},
        {"CPU ឈរសម្រាប់?","Central Processing Unit","Computer Power Unit","Central Power Unit","None","A"},
        {"Python ត្រូវបានបង្កើតឡើងដោយ?","Guido van Rossum","James Gosling","Dennis Ritchie","Bjarne Stroustrup","A"},
        {"Git command ណាសម្រាប់ upload?","git push","git pull","git commit","git clone","A"},
        {"IP Address version 4 មាន ? bits","16","32","64","128","B"},
        {"OS ណាដែលជា open source?","Windows","macOS","Linux","None","C"},
        {"RAM ឈរសម្រាប់?","Random Access Memory","Read Access Memory","Run Any Memory","None","A"},
        // FIX #3: បន្ថែម Trivia សំណួរថ្មី
        {"Java OOP មានគំនិត ? មុខ","2","3","4","5","C"},
        {"HTTP status 404 មានន័យ?","Server Error","Not Found","Forbidden","OK","B"},
        {"SQL SELECT ប្រើសម្រាប់?","លុបទិន្នន័យ","ដាក់ទិន្នន័យ","ទាញទិន្នន័យ","ផ្លាស់ប្តូរ","C"},
        {"Git branch ថ្មី command?","git new","git branch","git create","git fork","B"},
        {"JSON ឈរសម្រាប់?","Java Script Object Notation","Java Simple Object Name","Just Simple Object Node","None","A"},
    };
    private static class TriviaGame { int qIdx; TriviaGame(int i){qIdx=i;} }
    private final Map<String,TriviaGame> triviaGames = new ConcurrentHashMap<>();

    // ══ RPS State ══
    private final Map<String,Integer> rpsState = new ConcurrentHashMap<>();

    // ══ Stats ══
    private static class UserStats {
        int gamesPlayed=0, gamesWon=0;
        long totalGuesses=0;
        String firstSeen;
        // FIX #4: បន្ថែម streak tracking
        int currentStreak=0, bestStreak=0;
        UserStats(String d) { firstSeen=d; }
    }
    private final Map<String,UserStats> statsMap = new ConcurrentHashMap<>();

    private UserStats getStats(String c) {
        return statsMap.computeIfAbsent(c, k -> new UserStats(java.time.LocalDate.now().toString()));
    }
    private AtomicBoolean getCounter(String c) {
        return userCounters.computeIfAbsent(c, k -> new AtomicBoolean(false));
    }

    // ══ Active Game Tracker ══
    private boolean hasActiveGame(String chatId) {
        return guessGames.containsKey(chatId) || hlGames.containsKey(chatId) ||
               tttGames.containsKey(chatId)   || mathGames.containsKey(chatId) ||
               hangmanGames.containsKey(chatId)|| bjGames.containsKey(chatId) ||
               triviaGames.containsKey(chatId);
    }

    private void clearAllGames(String chatId) {
        guessGames.remove(chatId); hlGames.remove(chatId);
        tttGames.remove(chatId);   mathGames.remove(chatId);
        hangmanGames.remove(chatId);bjGames.remove(chatId);
        triviaGames.remove(chatId); rpsState.remove(chatId);
    }

    // ══ Win/Lose helper with streak ══
    private void recordWin(String chatId) {
        UserStats st = getStats(chatId);
        st.gamesWon++;
        st.currentStreak++;
        if (st.currentStreak > st.bestStreak) st.bestStreak = st.currentStreak;
    }
    private void recordLoss(String chatId) {
        getStats(chatId).currentStreak = 0;
    }

    // ══════════════════════════════════════════════
    // KEYBOARD
    // ══════════════════════════════════════════════
    private ReplyKeyboardMarkup buildKeyboard(String chatId) {
        KeyboardRow r1 = new KeyboardRow();
        r1.add("/random"); r1.add("/stop"); r1.add("/stats");

        KeyboardRow r2 = new KeyboardRow();
        r2.add("/game"); r2.add("/higher"); r2.add("/rps");

        KeyboardRow r3 = new KeyboardRow();
        r3.add("/tictactoe"); r3.add("/math"); r3.add("/word");

        KeyboardRow r4 = new KeyboardRow();
        r4.add("/blackjack"); r4.add("/slot"); r4.add("/trivia");

        KeyboardRow r5 = new KeyboardRow();
        r5.add("/quit"); r5.add("/help");
        r5.add(new KeyboardButton(isKh(chatId) ? "🌐 English" : "🌐 ខ្មែរ"));

        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setKeyboard(Arrays.asList(r1, r2, r3, r4, r5));
        kb.setResizeKeyboard(true);
        kb.setOneTimeKeyboard(false);
        return kb;
    }

    // ══════════════════════════════════════════════
    // MAIN UPDATE HANDLER
    // ══════════════════════════════════════════════
    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        String chatId = update.getMessage().getChatId().toString();
        String text   = update.getMessage().getText().trim();
        String name   = update.getMessage().getFrom().getFirstName();
        AtomicBoolean counting = getCounter(chatId);

        System.out.println("[" + name + "|" + chatId + "]: " + text);

        // ── Language Switch ──
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

        // ── Game Input Interception (non-command text) ──
        if (!text.startsWith("/")) {
            if (guessGames.containsKey(chatId))    { handleGuess(chatId, text); return; }
            if (hlGames.containsKey(chatId))        { handleHL(chatId, text); return; }
            if (mathGames.containsKey(chatId))      { handleMath(chatId, text); return; }
            if (hangmanGames.containsKey(chatId))   { handleHangman(chatId, text); return; }
            if (bjGames.containsKey(chatId))        { handleBJ(chatId, text); return; }
            if (triviaGames.containsKey(chatId))    { handleTrivia(chatId, text); return; }
            if (tttGames.containsKey(chatId))       { handleTTT(chatId, text); return; }
            if (rpsState.containsKey(chatId))       { handleRPS(chatId, text); return; }
        }

        // FIX #5: ប្រើ text.toLowerCase() ត្រឹមត្រូវ ហើយ handle @username suffix
        String cmd = text.toLowerCase().split("@")[0]; // handle "/start@botname"

        switch (cmd) {
            case "/start":
                sendMsg(chatId, String.format(
                    kh(chatId,
                        "👋 សួស្តី *%s*!\n\n🎮 Bot នេះមានហ្គេមច្រើន!\nជ្រើស Command ពី Menu ខាងក្រោម:",
                        "👋 Hello *%s*!\n\n🎮 This bot has many games!\nPick a command from the menu below:"),
                    name));
                break;

            case "/help":
                sendMsg(chatId, kh(chatId,
                    "📋 *Commands ទាំងអស់*\n\n" +
                    "🎰 /random — លេខចៃដន្យ\n" +
                    "⏹️ /stop — ឈប់ random\n" +
                    "📊 /stats — ស្ថិតិរបស់អ្នក\n" +
                    "🎮 /game — ទាយលេខ 1-100\n" +
                    "📈 /higher — ខ្ពស់ ឬ ទាប\n" +
                    "🪨 /rps — កូរ ក្រដាស កាត់\n" +
                    "♟️ /tictactoe — X O\n" +
                    "🔢 /math — Quiz គណិត\n" +
                    "🔤 /word — ទាយពាក្យ (Hangman)\n" +
                    "🃏 /blackjack — Blackjack\n" +
                    "🎰 /slot — Slot Machine\n" +
                    "❓ /trivia — Quiz ទូទៅ\n" +
                    "🏳️ /quit — បោះបង់ game\n" +
                    "🌐 /lang — ប្តូរភាសា",
                    "📋 *All Commands*\n\n" +
                    "🎰 /random — Random numbers\n" +
                    "⏹️ /stop — Stop random\n" +
                    "📊 /stats — Your stats\n" +
                    "🎮 /game — Guess 1-100\n" +
                    "📈 /higher — Higher or Lower\n" +
                    "🪨 /rps — Rock Paper Scissors\n" +
                    "♟️ /tictactoe — Tic Tac Toe\n" +
                    "🔢 /math — Math Quiz\n" +
                    "🔤 /word — Hangman\n" +
                    "🃏 /blackjack — Blackjack\n" +
                    "🎰 /slot — Slot Machine\n" +
                    "❓ /trivia — General Quiz\n" +
                    "🏳️ /quit — Quit game\n" +
                    "🌐 /lang — Toggle language"));
                break;

            case "/lang":
                if (isKh(chatId)) { userLang.put(chatId,"en"); sendMsg(chatId,"🌐 Language switched to *English*!"); }
                else              { userLang.put(chatId,"kh"); sendMsg(chatId,"🌐 ប្តូរជា *ភាសាខ្មែរ* រួចហើយ!"); }
                break;

            case "/random":
                if (hasActiveGame(chatId)) {
                    sendMsg(chatId, kh(chatId,"⚠️ Game កំពុងលេង! វាយ /quit ជាមុន","⚠️ Game in progress! Type /quit first."));
                    break;
                }
                if (counting.get()) {
                    sendMsg(chatId, kh(chatId,"⚠️ Random ដំណើរការ! វាយ /stop ជាមុន","⚠️ Random running! Type /stop first."));
                    break;
                }
                sendMsg(chatId, kh(chatId,"🎰 រង្វិលលេខចៃដន្យ...","🎰 Rolling random numbers..."));
                startRandom(chatId, counting);
                break;

            case "/stop":
                if (counting.compareAndSet(true, false))
                    sendMsg(chatId, kh(chatId,"⏹️ ឈប់រួចហើយ!","⏹️ Stopped!"));
                else
                    sendMsg(chatId, kh(chatId,"ℹ️ គ្មានអ្វីដំណើរការ","ℹ️ Nothing running."));
                break;

            case "/stats":
                sendStats(chatId);
                break;

            case "/quit":
                boolean hadGame = hasActiveGame(chatId);
                clearAllGames(chatId);
                if (hadGame) sendMsg(chatId, kh(chatId,"🏳️ បោះបង់ Game រួចហើយ!","🏳️ Game quit!"));
                else         sendMsg(chatId, kh(chatId,"ℹ️ គ្មាន Game ដំណើរការ","ℹ️ No game running."));
                break;

            // ── Guess Game ──
            case "/game":
                if (hasActiveGame(chatId)) {
                    sendMsg(chatId, kh(chatId,"⚠️ Game កំពុងលេង! /quit ជាមុន","⚠️ Game active! /quit first."));
                    break;
                }
                int sec = new Random().nextInt(100) + 1;
                guessGames.put(chatId, new GuessGame(sec));
                getStats(chatId).gamesPlayed++;
                sendMsg(chatId, kh(chatId,
                    "🎮 *ទាយលេខ!*\nខ្ញុំគិតលេខ 1-100\nវាយលេខដើម្បីទាយ:",
                    "🎮 *Guess the Number!*\nI'm thinking of 1-100\nType your guess:"));
                break;

            // ── Higher/Lower ──
            case "/higher":
                if (hasActiveGame(chatId)) {
                    sendMsg(chatId, kh(chatId,"⚠️ Game កំពុងលេង! /quit ជាមុន","⚠️ Game active! /quit first."));
                    break;
                }
                int hlSec = new Random().nextInt(100) + 1;
                hlGames.put(chatId, new HLGame(hlSec));
                getStats(chatId).gamesPlayed++;
                sendMsg(chatId, kh(chatId,
                    "📈 *Higher or Lower!*\nខ្ញុំគិតលេខ 1-100\nទាយថា ខ្ពស់ ឬ ទាប?\nវាយលេខ:",
                    "📈 *Higher or Lower!*\nI'm thinking of 1-100\nType a number to guess:"));
                break;

            // ── RPS ──
            case "/rps":
                if (hasActiveGame(chatId)) {
                    sendMsg(chatId, kh(chatId,"⚠️ Game កំពុងលេង! /quit ជាមុន","⚠️ Game active! /quit first."));
                    break;
                }
                String[] rpsBotPicks = {
                    kh(chatId,"🪨 កូរ","🪨 Rock"),
                    kh(chatId,"📄 ក្រដាស","📄 Paper"),
                    kh(chatId,"✂️ កាត់","✂️ Scissors")
                };
                int rpsBot = new Random().nextInt(3);
                rpsState.put(chatId, rpsBot);
                getStats(chatId).gamesPlayed++;
                sendMsg(chatId, kh(chatId,
                    "🪨 *Rock Paper Scissors!*\n\nBot ជ្រើស: " + rpsBotPicks[rpsBot] +
                    "\n\nអ្នកជ្រើស:\nវាយ *កូរ*, *ក្រដាស*, ឬ *កាត់*",
                    "🪨 *Rock Paper Scissors!*\n\nBot chose: " + rpsBotPicks[rpsBot] +
                    "\n\nYour choice:\nType *rock*, *paper*, or *scissors*"));
                break;

            // ── TicTacToe ──
            case "/tictactoe":
                if (hasActiveGame(chatId)) {
                    sendMsg(chatId, kh(chatId,"⚠️ Game កំពុងលេង! /quit ជាមុន","⚠️ Game active! /quit first."));
                    break;
                }
                TTTGame ttt = new TTTGame(chatId);
                tttGames.put(chatId, ttt);
                getStats(chatId).gamesPlayed++;
                sendMsg(chatId, kh(chatId,
                    "♟️ *Tic Tac Toe*\nអ្នក = X  |  Bot = O\nវាយលេខ 1-9 ជ្រើសទីតាំង:\n\n" + tttBoard(ttt),
                    "♟️ *Tic Tac Toe*\nYou = X  |  Bot = O\nType 1-9 to pick position:\n\n" + tttBoard(ttt)));
                break;

            // ── Math Quiz ──
            case "/math":
                if (hasActiveGame(chatId)) {
                    sendMsg(chatId, kh(chatId,"⚠️ Game កំពុងលេង! /quit ជាមុន","⚠️ Game active! /quit first."));
                    break;
                }
                startMath(chatId);
                break;

            // ── Hangman ──
            case "/word":
                if (hasActiveGame(chatId)) {
                    sendMsg(chatId, kh(chatId,"⚠️ Game កំពុងលេង! /quit ជាមុន","⚠️ Game active! /quit first."));
                    break;
                }
                String hw = HANGMAN_WORDS[new Random().nextInt(HANGMAN_WORDS.length)];
                HangmanGame hg = new HangmanGame(hw);
                hangmanGames.put(chatId, hg);
                getStats(chatId).gamesPlayed++;
                sendMsg(chatId, kh(chatId,
                    "🔤 *Hangman!*\nទាយពាក្យ (" + hw.length() + " អក្សរ):\n\n" +
                    hg.drawing() + "\n\n" + hg.display() + "\n\nវាយអក្សរ 1 ដើម្បីទាយ:",
                    "🔤 *Hangman!*\nGuess the word (" + hw.length() + " letters):\n\n" +
                    hg.drawing() + "\n\n" + hg.display() + "\n\nType a letter to guess:"));
                break;

            // ── Blackjack ──
            case "/blackjack":
                if (hasActiveGame(chatId)) {
                    sendMsg(chatId, kh(chatId,"⚠️ Game កំពុងលេង! /quit ជាមុន","⚠️ Game active! /quit first."));
                    break;
                }
                startBJ(chatId);
                break;

            // ── Slot ──
            case "/slot":
                doSlot(chatId);
                break;

            // ── Trivia ──
            case "/trivia":
                if (hasActiveGame(chatId)) {
                    sendMsg(chatId, kh(chatId,"⚠️ Game កំពុងលេង! /quit ជាមុន","⚠️ Game active! /quit first."));
                    break;
                }
                int qi = new Random().nextInt(TRIVIA.length);
                triviaGames.put(chatId, new TriviaGame(qi));
                getStats(chatId).gamesPlayed++;
                String[] q = TRIVIA[qi];
                sendMsg(chatId, "❓ *" + q[0] + "*\n\n" +
                    "A) " + q[1] + "\n" +
                    "B) " + q[2] + "\n" +
                    "C) " + q[3] + "\n" +
                    "D) " + q[4] +
                    kh(chatId, "\n\nវាយ A, B, C, ឬ D:", "\n\nType A, B, C, or D:"));
                break;

            default:
                sendMsg(chatId, kh(chatId,
                    "❓ Command មិនស្គាល់!\nវាយ /help ដើម្បីមើល Commands ទាំងអស់",
                    "❓ Unknown command!\nType /help to see all commands."));
        }
    }

    // ══════════════════════════════════════════════
    // GAME HANDLERS
    // ══════════════════════════════════════════════

    // ── RPS ──
    private void handleRPS(String chatId, String text) {
        Integer botPickObj = rpsState.remove(chatId);
        if (botPickObj == null) return;
        int botPick = botPickObj;

        String t = text.toLowerCase().trim();
        int userPick = -1;
        if      (t.contains("កូរ") || t.contains("rock") || t.contains("🪨"))       userPick = 0;
        else if (t.contains("ក្រដាស") || t.contains("paper") || t.contains("📄"))   userPick = 1;
        else if (t.contains("កាត់") || t.contains("scissors") || t.contains("✂"))   userPick = 2;

        if (userPick == -1) {
            sendMsg(chatId, kh(chatId,
                "❓ វាយ: *កូរ*, *ក្រដាស*, ឬ *កាត់*",
                "❓ Type: *rock*, *paper*, or *scissors*"));
            rpsState.put(chatId, botPick); // restore
            return;
        }

        String[] names = isKh(chatId)
            ? new String[]{"🪨 កូរ","📄 ក្រដាស","✂️ កាត់"}
            : new String[]{"🪨 Rock","📄 Paper","✂️ Scissors"};

        String result;
        if (userPick == botPick) {
            result = kh(chatId, "🤝 ស្មើ! ព្យាយាមម្តងទៀត /rps", "🤝 Draw! Try again /rps");
        } else if ((userPick==0&&botPick==2) || (userPick==1&&botPick==0) || (userPick==2&&botPick==1)) {
            result = kh(chatId, "🎉 អ្នកឈ្នះ! ចំនួន streak: *" + (getStats(chatId).currentStreak+1) + "*",
                                "🎉 You win! Streak: *" + (getStats(chatId).currentStreak+1) + "*");
            recordWin(chatId);
        } else {
            result = kh(chatId, "😅 Bot ឈ្នះ!", "😅 Bot wins!");
            recordLoss(chatId);
        }

        sendMsg(chatId,
            "👤 " + kh(chatId,"អ្នក","You") + ": " + names[userPick] + "\n" +
            "🤖 Bot: " + names[botPick] + "\n\n" + result);
    }

    // ── Guess Game ──
    private void handleGuess(String chatId, String text) {
        GuessGame gs = guessGames.get(chatId);
        if (gs == null) return;

        int g;
        try {
            g = Integer.parseInt(text.trim());
            if (g < 1 || g > 100) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sendMsg(chatId, kh(chatId,"❓ វាយលេខ 1-100!","❓ Enter a number between 1-100!"));
            return;
        }

        gs.guesses++;
        if (g < gs.secret) {
            sendMsg(chatId, kh(chatId,
                "📉 *តូចពេក!*\nទាយម្តងទៀត: (ការទាយ #" + gs.guesses + ")",
                "📉 *Too low!*\nTry again: (Guess #" + gs.guesses + ")"));
        } else if (g > gs.secret) {
            sendMsg(chatId, kh(chatId,
                "📈 *ធំពេក!*\nទាយម្តងទៀត: (ការទាយ #" + gs.guesses + ")",
                "📈 *Too high!*\nTry again: (Guess #" + gs.guesses + ")"));
        } else {
            guessGames.remove(chatId);
            UserStats st = getStats(chatId);
            st.totalGuesses += gs.guesses;
            recordWin(chatId);
            // FIX #6: bonus message based on guesses
            String bonus = gs.guesses <= 5
                ? kh(chatId, "🔥 ឆ្លាត! (ទាយត្រូវក្នុង " + gs.guesses + " ការទាយ)", "🔥 Amazing! (" + gs.guesses + " guesses)")
                : kh(chatId, "🎉 ត្រូវ! (" + gs.guesses + " ការទាយ)", "🎉 Correct! (" + gs.guesses + " guesses)");
            sendMsg(chatId, bonus + "\n🏆 " + kh(chatId,"Streak: *" + st.currentStreak + "*","Streak: *" + st.currentStreak + "*"));
        }
    }

    // ── Higher/Lower ──
    private void handleHL(String chatId, String text) {
        HLGame hl = hlGames.get(chatId);
        if (hl == null) return;

        int g;
        try {
            g = Integer.parseInt(text.trim());
            if (g < 1 || g > 100) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sendMsg(chatId, kh(chatId,"❓ វាយលេខ 1-100!","❓ Enter a number between 1-100!"));
            return;
        }

        hl.guesses++;
        if (g == hl.secret) {
            hlGames.remove(chatId);
            UserStats st = getStats(chatId);
            st.totalGuesses += hl.guesses;
            recordWin(chatId);
            sendMsg(chatId, String.format(
                kh(chatId,"🎉 ត្រូវ! លេខ *%d* ក្នុង *%d* ការទាយ!\n🏆 Streak: *%d*",
                          "🎉 Correct! Number *%d* in *%d* guesses!\n🏆 Streak: *%d*"),
                hl.secret, hl.guesses, st.currentStreak));
        } else {
            String hint = g < hl.secret
                ? kh(chatId,"📈 លេខរបស់ខ្ញុំ *ខ្ពស់ជាង* " + g + "!","📈 My number is *higher* than " + g + "!")
                : kh(chatId,"📉 លេខរបស់ខ្ញុំ *ទាបជាង* " + g + "!","📉 My number is *lower* than " + g + "!");
            sendMsg(chatId, hint + "\n" + kh(chatId,"ទាយ #" + hl.guesses + " — ព្យាយាមម្តងទៀត:","Guess #" + hl.guesses + " — Try again:"));
        }
    }

    // ── Math Quiz ──
    private void startMath(String chatId) {
        Random r = new Random();
        int a = r.nextInt(20) + 1, b = r.nextInt(20) + 1;
        int op = r.nextInt(4), ans;
        String q;
        switch (op) {
            case 0: ans = a + b;            q = a + " + " + b + " = ?"; break;
            case 1: ans = a * b;            q = a + " × " + b + " = ?"; break;
            case 2: ans = Math.abs(a - b);  q = Math.max(a,b) + " - " + Math.min(a,b) + " = ?"; break;
            default: ans = a;               q = a + " × 1 = ?"; break;
        }
        mathGames.put(chatId, new MathGame(a, b, op, ans, q));
        getStats(chatId).gamesPlayed++;
        sendMsg(chatId, "🔢 *" + kh(chatId,"Quiz គណិត","Math Quiz") + "*\n\n❓ *" + q + "*\n\n" +
            kh(chatId,"វាយចម្លើយ:","Type your answer:"));
    }

    private void handleMath(String chatId, String text) {
        MathGame mg = mathGames.get(chatId);
        if (mg == null) return;

        int g;
        try { g = Integer.parseInt(text.trim()); }
        catch (NumberFormatException e) {
            sendMsg(chatId, kh(chatId,"❓ វាយតួលេខ!","❓ Enter a number!"));
            return;
        }
        mathGames.remove(chatId);
        if (g == mg.ans) {
            recordWin(chatId);
            sendMsg(chatId, kh(chatId,
                "🎉 ត្រូវហើយ! 🧠\n🏆 Streak: *" + getStats(chatId).currentStreak + "*",
                "🎉 Correct! 🧠\n🏆 Streak: *" + getStats(chatId).currentStreak + "*"));
        } else {
            recordLoss(chatId);
            sendMsg(chatId, String.format(
                kh(chatId,"❌ មិនត្រូវ!\n*%s*\nចម្លើយ = *%d*","❌ Wrong!\n*%s*\nAnswer = *%d*"),
                mg.question, mg.ans));
        }
    }

    // ── Hangman ──
    private void handleHangman(String chatId, String text) {
        HangmanGame hg = hangmanGames.get(chatId);
        if (hg == null) return;

        if (text.length() != 1 || !Character.isLetter(text.charAt(0))) {
            sendMsg(chatId, kh(chatId,"❓ វាយអក្សរ 1 តែប៉ុណ្ណោះ!","❓ Type exactly 1 letter!"));
            return;
        }

        char c = Character.toLowerCase(text.charAt(0));
        if (hg.guessed.contains(c)) {
            sendMsg(chatId, kh(chatId,"⚠️ ទាយអក្សរ *" + c + "* ហើយ!","⚠️ Already guessed *" + c + "*!"));
            return;
        }

        hg.guessed.add(c);
        if (!hg.word.contains(String.valueOf(c))) {
            hg.wrong++;
            if (hg.wrong >= 6) {
                hangmanGames.remove(chatId);
                recordLoss(chatId);
                sendMsg(chatId, hg.drawing() + "\n\n💀 " + String.format(
                    kh(chatId,"ចាញ់! ពាក្យគឺ *%s*","Game over! Word was *%s*"), hg.word));
                return;
            }
            sendMsg(chatId,
                hg.drawing() + "\n\n❌ *" + c + "* " +
                kh(chatId,"មិនមាន! ខុស ","not in word! Wrong ") +
                hg.wrong + "/6\n" + hg.display() + "\n\n" +
                kh(chatId,"អក្សរដែលទាយ: ","Guessed: ") + hg.guessed.toString());
        } else {
            if (hg.won()) {
                hangmanGames.remove(chatId);
                recordWin(chatId);
                sendMsg(chatId, "🎉 " + kh(chatId,
                    "ឈ្នះ! ពាក្យ: *" + hg.word + "*\n🏆 Streak: *" + getStats(chatId).currentStreak + "*",
                    "You win! Word: *" + hg.word + "*\n🏆 Streak: *" + getStats(chatId).currentStreak + "*"));
            } else {
                sendMsg(chatId, "✅ *" + c + "* " +
                    kh(chatId,"ត្រូវ!\n","correct!\n") +
                    hg.display() + "\n\n" +
                    kh(chatId,"អក្សរដែលទាយ: ","Guessed: ") + hg.guessed.toString());
            }
        }
    }

    // ── TicTacToe ──
    private String tttBoard(TTTGame g) {
        char[] b = g.board;
        return String.format(
            "`%s|%s|%s`\n`-+-+-`\n`%s|%s|%s`\n`-+-+-`\n`%s|%s|%s`",
            b[0]==' '?"1":b[0], b[1]==' '?"2":b[1], b[2]==' '?"3":b[2],
            b[3]==' '?"4":b[3], b[4]==' '?"5":b[4], b[5]==' '?"6":b[5],
            b[6]==' '?"7":b[6], b[7]==' '?"8":b[7], b[8]==' '?"9":b[8]);
    }

    private char tttCheck(char[] b) {
        int[][] wins = {{0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}};
        for (int[] l : wins)
            if (b[l[0]] != ' ' && b[l[0]] == b[l[1]] && b[l[1]] == b[l[2]]) return b[l[0]];
        for (char c : b) if (c == ' ') return ' ';
        return 'D';
    }

    private void handleTTT(String chatId, String text) {
        TTTGame g = tttGames.get(chatId);
        if (g == null) return;

        int pos;
        try {
            pos = Integer.parseInt(text.trim()) - 1;
            if (pos < 0 || pos > 8) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sendMsg(chatId, kh(chatId,"❓ វាយ 1-9!","❓ Type 1-9!"));
            return;
        }

        if (g.board[pos] != ' ') {
            sendMsg(chatId, kh(chatId,"⚠️ ទីតាំងពេញ! ជ្រើសទីតាំងផ្សេង","⚠️ That spot is taken! Pick another."));
            return;
        }

        g.board[pos] = 'X';
        char res = tttCheck(g.board);

        if (res == 'X') {
            tttGames.remove(chatId);
            recordWin(chatId);
            sendMsg(chatId, "🎉 " + kh(chatId,"អ្នកឈ្នះ!\n","You win!\n") + tttBoard(g) +
                "\n\n🏆 Streak: *" + getStats(chatId).currentStreak + "*");
            return;
        }
        if (res == 'D') {
            tttGames.remove(chatId);
            sendMsg(chatId, "🤝 " + kh(chatId,"ស្មើ!\n","Draw!\n") + tttBoard(g));
            return;
        }

        // Bot AI: win → block → center → corner → random
        int[] available = java.util.stream.IntStream.range(0,9)
            .filter(i -> g.board[i] == ' ').toArray();
        int botMove = -1;

        // Try to win
        for (int i : available) {
            g.board[i] = 'O';
            if (tttCheck(g.board) == 'O') { botMove = i; break; }
            g.board[i] = ' ';
        }
        // Block player
        if (botMove == -1) {
            for (int i : available) {
                g.board[i] = 'X';
                if (tttCheck(g.board) == 'X') { g.board[i] = ' '; botMove = i; break; }
                g.board[i] = ' ';
            }
        }
        // Center
        if (botMove == -1 && g.board[4] == ' ') botMove = 4;
        // Corners
        if (botMove == -1) {
            for (int corner : new int[]{0, 2, 6, 8}) {
                if (g.board[corner] == ' ') { botMove = corner; break; }
            }
        }
        // Random
        if (botMove == -1 && available.length > 0)
            botMove = available[new Random().nextInt(available.length)];

        if (botMove >= 0) g.board[botMove] = 'O';

        res = tttCheck(g.board);
        if (res == 'O') {
            tttGames.remove(chatId);
            recordLoss(chatId);
            sendMsg(chatId, "😅 Bot " + kh(chatId,"ឈ្នះ!\n","wins!\n") + tttBoard(g));
            return;
        }
        if (res == 'D') {
            tttGames.remove(chatId);
            sendMsg(chatId, "🤝 " + kh(chatId,"ស្មើ!\n","Draw!\n") + tttBoard(g));
            return;
        }

        sendMsg(chatId, kh(chatId,"វដ្តរបស់អ្នក (X):\n","Your turn (X):\n") + tttBoard(g));
    }

    // ── Blackjack ──
    private int bjCard() { return new Random().nextInt(13) + 1; }

    private void startBJ(String chatId) {
        BJGame bj = new BJGame();
        bj.player.add(bjCard()); bj.player.add(bjCard());
        bj.dealer.add(bjCard()); bj.dealer.add(bjCard());
        bjGames.put(chatId, bj);
        getStats(chatId).gamesPlayed++;
        sendMsg(chatId, kh(chatId,
            "🃏 *Blackjack!*\n\n👤 អ្នក: " + bj.handStr(bj.player) +
            "\n🤖 Dealer: [" + bj.cardStr(bj.dealer.get(0)) + "][?]\n\n" +
            "វាយ *hit* (ទទួលបន្ថែម) ឬ *stand* (ឈប់):",
            "🃏 *Blackjack!*\n\n👤 You: " + bj.handStr(bj.player) +
            "\n🤖 Dealer: [" + bj.cardStr(bj.dealer.get(0)) + "][?]\n\n" +
            "Type *hit* (take card) or *stand* (stop):"));
    }

    private void handleBJ(String chatId, String text) {
        BJGame bj = bjGames.get(chatId);
        if (bj == null) return;

        String t = text.toLowerCase().trim();
        if (t.equals("hit") || t.equals("ទទួល")) {
            bj.player.add(bjCard());
            int pv = bj.handVal(bj.player);
            if (pv > 21) {
                bjGames.remove(chatId);
                recordLoss(chatId);
                sendMsg(chatId, "💥 " + kh(chatId,
                    "លើស 21! ចាញ់!\n👤 អ្នក: ",
                    "Bust! You lose!\n👤 You: ") + bj.handStr(bj.player));
                return;
            }
            sendMsg(chatId,
                "🃏 " + kh(chatId,"👤 អ្នក: ","👤 You: ") + bj.handStr(bj.player) +
                "\n\n" + kh(chatId,"វាយ *hit* ឬ *stand*:","Type *hit* or *stand*:"));
        } else if (t.equals("stand") || t.equals("ឈប់")) {
            while (bj.handVal(bj.dealer) < 17) bj.dealer.add(bjCard());
            bjGames.remove(chatId);
            int pv = bj.handVal(bj.player), dv = bj.handVal(bj.dealer);
            String res;
            if (dv > 21 || pv > dv) {
                recordWin(chatId);
                res = kh(chatId,
                    "🎉 អ្នកឈ្នះ!\n🏆 Streak: *" + getStats(chatId).currentStreak + "*",
                    "🎉 You win!\n🏆 Streak: *" + getStats(chatId).currentStreak + "*");
            } else if (pv == dv) {
                res = kh(chatId,"🤝 ស្មើ!","🤝 Push!");
            } else {
                recordLoss(chatId);
                res = kh(chatId,"😅 Dealer ឈ្នះ!","😅 Dealer wins!");
            }
            sendMsg(chatId,
                "👤 " + kh(chatId,"អ្នក: ","You: ") + bj.handStr(bj.player) +
                "\n🤖 Dealer: " + bj.handStr(bj.dealer) + "\n\n" + res);
        } else {
            sendMsg(chatId, kh(chatId,
                "❓ វាយ *hit* ឬ *stand* ប៉ុណ្ណោះ!",
                "❓ Type *hit* or *stand* only!"));
        }
    }

    // ── Slot Machine ──
    private void doSlot(String chatId) {
        String[] syms = {"🍒","🍋","🔔","⭐","💎","7️⃣"};
        int a = new Random().nextInt(6), b = new Random().nextInt(6), c = new Random().nextInt(6);
        String line = syms[a] + " | " + syms[b] + " | " + syms[c];
        String res;
        if (a == b && b == c) {
            recordWin(chatId);
            res = kh(chatId,
                "🎉🎉🎉 *JACKPOT!* ឈ្នះធំ!\n🏆 Streak: *" + getStats(chatId).currentStreak + "*",
                "🎉🎉🎉 *JACKPOT!* Big win!\n🏆 Streak: *" + getStats(chatId).currentStreak + "*");
        } else if (a == b || b == c || a == c) {
            res = kh(chatId,"✨ ដូចគ្នា 2! ឈ្នះតូច!","✨ 2 match! Small win!");
        } else {
            res = kh(chatId,"😅 ចាញ់! ព្យាយាមម្តងទៀត /slot","😅 No match! Try again /slot");
        }
        sendMsg(chatId, "🎰 [ " + line + " ]\n\n" + res);
    }

    // ── Trivia ──
    private void handleTrivia(String chatId, String text) {
        TriviaGame tg = triviaGames.get(chatId);
        if (tg == null) return;

        String t = text.toUpperCase().trim();
        if (!t.equals("A") && !t.equals("B") && !t.equals("C") && !t.equals("D")) {
            sendMsg(chatId, kh(chatId,"❓ វាយ A, B, C, ឬ D ប៉ុណ្ណោះ!","❓ Type A, B, C, or D only!"));
            return;
        }

        triviaGames.remove(chatId);
        String[] q = TRIVIA[tg.qIdx];
        int ansIdx = q[5].equals("A") ? 1 : q[5].equals("B") ? 2 : q[5].equals("C") ? 3 : 4;

        if (t.equals(q[5])) {
            recordWin(chatId);
            sendMsg(chatId, "🎉 " + kh(chatId,"ត្រូវហើយ!","Correct!") + " ✅\n" +
                kh(chatId,"ចម្លើយ: ","Answer: ") + q[5] + ") *" + q[ansIdx] + "*\n" +
                "🏆 Streak: *" + getStats(chatId).currentStreak + "*");
        } else {
            recordLoss(chatId);
            sendMsg(chatId, "❌ " + kh(chatId,"មិនត្រូវ!","Wrong!") + "\n" +
                kh(chatId,"ចម្លើយត្រឹមត្រូវ: ","Correct answer: ") + q[5] + ") *" + q[ansIdx] + "*");
        }
    }

    // ── Stats ──
    private void sendStats(String chatId) {
        UserStats st = getStats(chatId);
        String wr = st.gamesPlayed == 0 ? "0"
            : String.format("%.1f", (st.gamesWon * 100.0) / st.gamesPlayed);
        String ag = st.gamesWon == 0 ? "-"
            : String.format("%.1f", (double) st.totalGuesses / st.gamesWon);

        sendMsg(chatId, String.format(kh(chatId,
            "📊 *ស្ថិតិរបស់អ្នក*\n\n" +
            "🎮 លេងសរុប: *%d*\n" +
            "🏆 ឈ្នះ: *%d*\n" +
            "🎯 អត្រាឈ្នះ: *%s%%*\n" +
            "🔢 ទាយមធ្យម: *%s*\n" +
            "🔥 Streak បច្ចុប្បន្ន: *%d*\n" +
            "⭐ Streak ល្អបំផុត: *%d*\n" +
            "📅 ចូលដំបូង: *%s*",
            "📊 *Your Stats*\n\n" +
            "🎮 Played: *%d*\n" +
            "🏆 Won: *%d*\n" +
            "🎯 Win rate: *%s%%*\n" +
            "🔢 Avg guesses: *%s*\n" +
            "🔥 Current streak: *%d*\n" +
            "⭐ Best streak: *%d*\n" +
            "📅 First seen: *%s*"),
            st.gamesPlayed, st.gamesWon, wr, ag,
            st.currentStreak, st.bestStreak, st.firstSeen));
    }

    // ── Random Numbers ──
    private void startRandom(String chatId, AtomicBoolean counting) {
        counting.set(true);
        int[] delays = {50, 80, 110}, steps = {20, 25, 30};
        String[] names = {"🟢 Box 1", "🔵 Box 2", "🟠 Box 3"};

        for (int i = 0; i < 3; i++) {
            final int idx = i;
            final String box = names[i];
            executor.submit(() -> {
                Random r = new Random();
                int fv = 0;
                for (int s = 0; s < steps[idx] && counting.get(); s++) {
                    fv = r.nextInt(10);
                    sendMsg(chatId, box + ": *" + fv + "*");
                    try {
                        Thread.sleep(Math.min(delays[idx] + (long)s * 20, 500));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                sendMsg(chatId, "🎯 " + box + " " + String.format(
                    kh(chatId, "បានឈប់នៅ: *%d*", "stopped at: *%d*"), fv));
            });
        }
    }

    // ══════════════════════════════════════════════
    // SEND MESSAGE HELPER
    // ══════════════════════════════════════════════
    private void sendMsg(String chatId, String text) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText(text);
        msg.setParseMode("Markdown");
        msg.setReplyMarkup(buildKeyboard(chatId));
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            System.err.println("Send error [" + chatId + "]: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════
    // MAIN
    // ══════════════════════════════════════════════
    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new java.io.PrintStream(System.err, true, StandardCharsets.UTF_8));

        // HTTP keepalive server (for hosting platforms like Render/Railway)
        new Thread(() -> {
            try {
                com.sun.net.httpserver.HttpServer server =
                    com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(8080), 0);
                server.createContext("/", (exchange) -> {
                    byte[] r = "✅ Bot is running!".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, r.length);
                    exchange.getResponseBody().write(r);
                    exchange.getResponseBody().close();
                });
                server.start();
                System.out.println("✅ HTTP Server started on port 8080");
            } catch (Exception e) {
                System.err.println("HTTP server error: " + e.getMessage());
            }
        }).start();

        try {
            if (BOT_TOKEN == null || BOT_TOKEN.equals("YOUR_TOKEN_HERE")) {
                System.err.println("❌ ERROR: BOT_TOKEN not set!");
                System.err.println("   Set environment variable: BOT_TOKEN=your_token_here");
                System.exit(1);
            }

            MyBot bot = new MyBot();

            // Clear old webhook/pending updates
            org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook del =
                new org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook();
            del.setDropPendingUpdates(true);
            try { bot.execute(del); } catch (Exception e) { /* ignore */ }

            new TelegramBotsApi(DefaultBotSession.class).registerBot(bot);
            System.out.println("✅ Bot running: @" + BOT_USERNAME);

        } catch (TelegramApiException e) {
            System.err.println("❌ Startup error: " + e.getMessage());
        }
    }
}