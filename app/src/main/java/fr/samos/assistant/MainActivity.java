package fr.samos.assistant;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 60, 40, 40);
        layout.setBackgroundColor(0xFF07111F);
        TextView title = new TextView(this);
        title.setText("SAMOS"); title.setTextSize(32); title.setTextColor(0xFF55D6FF);
        TextView info = new TextView(this);
        info.setText("Prototype Android pour Galaxy S10.\n\nCette première version prépare l'application native et sa notification persistante. La détection du mot « SAMOS » et l'écoute du microphone ne sont pas encore activées.");
        info.setTextSize(17); info.setTextColor(0xFFEAF2FF); info.setPadding(0, 30, 0, 30);
        Button start = new Button(this); start.setText("Activer la présence SAMOS");
        start.setOnClickListener(v -> startService(new Intent(this, PresenceService.class)));
        Button stop = new Button(this); stop.setText("Désactiver");
        stop.setOnClickListener(v -> stopService(new Intent(this, PresenceService.class)));
        layout.addView(title); layout.addView(info); layout.addView(start); layout.addView(stop);
        setContentView(layout);
    }
}
