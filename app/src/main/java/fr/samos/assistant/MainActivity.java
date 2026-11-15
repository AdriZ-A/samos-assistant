package fr.samos.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int MIC_REQUEST = 71;
    private static final String PREFS = "samos_local";
    private SharedPreferences prefs;
    private LinearLayout root, messages;
    private ScrollView chatScroll;
    private EditText chatInput, noteInput, taskInput;
    private TextView voiceStatus, presenceStatus;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildScreen();
        initSpeechOutput();
        restoreChat();
    }

    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextSize(size); v.setTextColor(color);
        return v;
    }

    private Button button(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void buildScreen() {
        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(28), dp(18), dp(28));
        root.setBackgroundColor(0xFF07111F);
        page.addView(root);

        TextView title = text("SAMOS", 34, 0xFF55D6FF);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView subtitle = text("Assistant personnel · version Android", 14, 0xFFB8C8DF);
        subtitle.setGravity(Gravity.CENTER);
        addGap(root, 4);
        root.addView(subtitle);
        addGap(root, 18);

        presenceStatus = text("Présence : désactivée", 14, 0xFFEAF2FF);
        root.addView(presenceStatus);
        LinearLayout presenceButtons = new LinearLayout(this);
        presenceButtons.setOrientation(LinearLayout.HORIZONTAL);
        presenceButtons.addView(button("Activer la présence", () -> {
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(new Intent(this, PresenceService.class));
            else startService(new Intent(this, PresenceService.class));
            presenceStatus.setText("Présence : notification active");
        }), new LinearLayout.LayoutParams(0, -2, 1));
        presenceButtons.addView(button("Désactiver", () -> {
            stopService(new Intent(this, PresenceService.class));
            presenceStatus.setText("Présence : désactivée");
        }), new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(presenceButtons);
        addGap(root, 18);

        root.addView(text("Conversation", 20, 0xFF55D6FF));
        chatScroll = new ScrollView(this);
        chatScroll.setBackgroundColor(0xFF111E30);
        chatScroll.setPadding(dp(10), dp(8), dp(10), dp(8));
        chatScroll.setMinimumHeight(dp(180));
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        chatScroll.addView(messages);
        root.addView(chatScroll, new LinearLayout.LayoutParams(-1, dp(230)));
        chatInput = new EditText(this);
        chatInput.setSingleLine(true);
        chatInput.setTextColor(0xFFFFFFFF);
        chatInput.setHintTextColor(0xFF9EACC0);
        chatInput.setHint("Écris à SAMOS…");
        root.addView(chatInput);
        root.addView(button("Envoyer", () -> sendTypedMessage()));
        root.addView(button("🎙 Parler à SAMOS", () -> beginListening()));
        root.addView(button("Arrêter la voix", () -> { if (tts != null) tts.stop(); }));
        voiceStatus = text("La reconnaissance vocale démarre uniquement lorsque tu appuies sur le bouton et que l'application est ouverte.", 13, 0xFFB8C8DF);
        root.addView(voiceStatus);
        root.addView(button("Effacer la conversation", () -> {
            prefs.edit().remove("chat").apply();
            messages.removeAllViews();
            addMessage("SAMOS", "Historique effacé. Bonjour, je suis SAMOS.");
        }));
        addGap(root, 20);

        root.addView(text("Mémoire locale", 20, 0xFF55D6FF));
        TextView memoryHint = text("Enregistre des notes sur cet appareil. Évite les mots de passe et les informations sensibles.", 13, 0xFFB8C8DF);
        root.addView(memoryHint);
        noteInput = new EditText(this);
        noteInput.setTextColor(0xFFFFFFFF); noteInput.setHintTextColor(0xFF9EACC0);
        noteInput.setHint("Note à mémoriser…");
        root.addView(noteInput);
        root.addView(button("Mémoriser cette note", () -> saveNote()));
        root.addView(button("Afficher mes notes", () -> showNotes()));
        root.addView(button("Effacer toutes les notes", () -> {
            prefs.edit().remove("notes").apply();
            reply("Toutes mes notes locales ont été effacées.");
        }));
        addGap(root, 20);

        root.addView(text("Tâches", 20, 0xFF55D6FF));
        taskInput = new EditText(this);
        taskInput.setTextColor(0xFFFFFFFF); taskInput.setHintTextColor(0xFF9EACC0);
        taskInput.setHint("Nouvelle tâche…");
        root.addView(taskInput);
        root.addView(button("Ajouter la tâche", () -> addTask()));
        root.addView(button("Afficher mes tâches", () -> showTasks()));
        root.addView(button("Effacer toutes les tâches", () -> {
            prefs.edit().remove("tasks").apply();
            reply("Toutes les tâches ont été effacées.");
        }));
        addGap(root, 18);
        root.addView(text("SAMOS fonctionne ici avec des commandes locales simples. Il n'est pas encore connecté à une IA en ligne et ne détecte pas encore le mot d'activation en continu.", 13, 0xFFB8C8DF));

        setContentView(page);
    }

    private void addGap(LinearLayout parent, int dpValue) {
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(1, dp(dpValue)));
    }

    private void addMessage(String who, String message) {
        TextView line = text(who + " : " + message, 15, who.equals("Toi") ? 0xFFB9DEFF : 0xFFEAF2FF);
        line.setPadding(dp(8), dp(7), dp(8), dp(7));
        messages.addView(line);
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void saveChatLine(String who, String message) {
        String old = prefs.getString("chat", "");
        String updated = old + who + " : " + message.replace("\n", " ") + "\n";
        String[] lines = updated.split("\n");
        StringBuilder kept = new StringBuilder();
        for (int i = Math.max(0, lines.length - 40); i < lines.length; i++) {
            if (!lines[i].isEmpty()) kept.append(lines[i]).append("\n");
        }
        prefs.edit().putString("chat", kept.toString()).apply();
    }

    private void restoreChat() {
        String saved = prefs.getString("chat", "");
        if (saved.isEmpty()) {
            addMessage("SAMOS", "Bonjour. Je suis SAMOS. Écris-moi ou appuie sur « Parler à SAMOS ».");
        } else {
            for (String line : saved.split("\n")) {
                int split = line.indexOf(" : ");
                if (split > 0) addMessage(line.substring(0, split), line.substring(split + 3));
            }
        }
    }

    private void sendTypedMessage() {
        String value = chatInput.getText().toString().trim();
        if (value.isEmpty()) return;
        chatInput.setText("");
        handleMessage(value);
    }

    private void handleMessage(String value) {
        addMessage("Toi", value); saveChatLine("Toi", value);
        String q = value.toLowerCase(Locale.FRENCH);
        String answer;
        if (q.matches(".*\\b(bonjour|salut|bonsoir|coucou)\\b.*")) {
            answer = "Bonjour. Je suis opérationnel. Que souhaites-tu faire ?";
        } else if (q.contains("qui es-tu") || q.contains("ton nom")) {
            answer = "Je suis SAMOS, ton assistant personnel. Je peux discuter avec des commandes simples, parler à voix haute, mémoriser des notes et gérer une liste de tâches.";
        } else if (q.contains("quelle heure") || q.contains("il est quelle heure")) {
            answer = "Il est " + new SimpleDateFormat("HH:mm", Locale.FRANCE).format(new Date()) + ".";
        } else if (q.contains("quelle date") || q.contains("quel jour") || q.contains("on est quel jour")) {
            answer = "Nous sommes le " + new SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRANCE).format(new Date()) + ".";
        } else if (q.contains("aide") || q.contains("que peux-tu faire")) {
            answer = "Je peux saluer, donner l'heure et la date, mémoriser une note, retrouver tes notes et gérer tes tâches. Essaie : « Mémorise que mon projet s'appelle SAMOS » ou « Ajoute une tâche : réviser ». Je ne suis pas encore une IA connectée.";
        } else if (q.startsWith("mémorise ") || q.startsWith("memorise ") || q.startsWith("retiens ")) {
            String note = value.replaceFirst("(?i)^(mémorise|memorise|retiens)( que)?\\s*", "").trim();
            if (!note.isEmpty()) {
                Set<String> notes = new HashSet<>(prefs.getStringSet("notes", new HashSet<>()));
                notes.add(note); prefs.edit().putStringSet("notes", notes).apply();
                answer = "C'est enregistré dans ma mémoire locale : " + note;
            } else answer = "Dis-moi quelle note tu veux que je mémorise.";
        } else if (q.contains("mes notes") || q.contains("que sais-tu") || q.contains("qu'as-tu retenu")) {
            Set<String> notes = prefs.getStringSet("notes", new HashSet<>());
            answer = notes.isEmpty() ? "Je n'ai encore aucune note enregistrée." : "Voici mes notes : " + join(notes);
        } else if (q.startsWith("ajoute une tâche") || q.startsWith("ajoute une tache")) {
            String task = value.replaceFirst("(?i)^ajoute une tâ?che\\s*:?\\s*", "").trim();
            if (!task.isEmpty()) {
                Set<String> tasks = new HashSet<>(prefs.getStringSet("tasks", new HashSet<>()));
                tasks.add(task); prefs.edit().putStringSet("tasks", tasks).apply();
                answer = "Tâche ajoutée : " + task;
            } else answer = "Quelle tâche veux-tu ajouter ?";
        } else if (q.contains("mes tâches") || q.contains("mes taches")) {
            Set<String> tasks = prefs.getStringSet("tasks", new HashSet<>());
            answer = tasks.isEmpty() ? "Tu n'as aucune tâche enregistrée." : "Voici tes tâches : " + join(tasks);
        } else if (q.contains("merci")) {
            answer = "Avec plaisir. Je reste à ta disposition.";
        } else {
            answer = "Je n'ai pas encore de moteur d'intelligence artificielle connecté. Essaie « aide » pour voir mes commandes disponibles.";
        }
        reply(answer);
    }

    private String join(Set<String> values) {
        StringBuilder result = new StringBuilder();
        for (String item : values) {
            if (result.length() > 0) result.append(" ; ");
            result.append(item);
        }
        return result.toString();
    }

    private void reply(String answer) {
        addMessage("SAMOS", answer); saveChatLine("SAMOS", answer); speak(answer);
    }

    private void initSpeechOutput() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.FRANCE);
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED;
            }
        });
    }

    private void speak(String message) {
        if (ttsReady && tts != null) tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "samos_reply");
    }

    private void beginListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_REQUEST);
            return;
        }
        startRecognition();
    }

    private void startRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            voiceStatus.setText("La reconnaissance vocale n'est pas disponible sur ce téléphone. Tu peux écrire tes messages.");
            return;
        }
        if (recognizer != null) recognizer.destroy();
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new android.speech.RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { voiceStatus.setText("Je t'écoute…"); }
            @Override public void onBeginningOfSpeech() { voiceStatus.setText("Parle maintenant…"); }
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { voiceStatus.setText("Analyse de ta demande…"); }
            @Override public void onError(int error) {
                voiceStatus.setText("Écoute interrompue (code " + error + "). Tu peux réessayer ou écrire.");
            }
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) handleMessage(matches.get(0));
                voiceStatus.setText("Écoute terminée. Appuie sur le bouton pour parler à nouveau.");
            }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Parle à SAMOS");
        recognizer.startListening(intent);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MIC_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startRecognition();
            else voiceStatus.setText("Autorisation du microphone refusée. Tu peux toujours écrire tes messages.");
        }
    }

    private void saveNote() {
        String note = noteInput.getText().toString().trim();
        if (note.isEmpty()) { Toast.makeText(this, "Écris une note d'abord.", Toast.LENGTH_SHORT).show(); return; }
        Set<String> notes = new HashSet<>(prefs.getStringSet("notes", new HashSet<>()));
        notes.add(note); prefs.edit().putStringSet("notes", notes).apply();
        noteInput.setText(""); reply("C'est noté dans ma mémoire locale.");
    }

    private void showNotes() {
        Set<String> notes = prefs.getStringSet("notes", new HashSet<>());
        reply(notes.isEmpty() ? "Je n'ai encore aucune note enregistrée." : "Voici mes notes : " + join(notes));
    }

    private void addTask() {
        String task = taskInput.getText().toString().trim();
        if (task.isEmpty()) { Toast.makeText(this, "Écris une tâche d'abord.", Toast.LENGTH_SHORT).show(); return; }
        Set<String> tasks = new HashSet<>(prefs.getStringSet("tasks", new HashSet<>()));
        tasks.add(task); prefs.edit().putStringSet("tasks", tasks).apply();
        taskInput.setText(""); reply("Tâche ajoutée : " + task);
    }

    private void showTasks() {
        Set<String> tasks = prefs.getStringSet("tasks", new HashSet<>());
        reply(tasks.isEmpty() ? "Tu n'as aucune tâche enregistrée." : "Voici tes tâches : " + join(tasks));
    }

    @Override protected void onDestroy() {
        if (recognizer != null) { recognizer.destroy(); recognizer = null; }
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
