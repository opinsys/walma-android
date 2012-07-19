package fi.opinsys.walma;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

public class Preferences extends Activity {
	public static final String PREFS_NAME = "WalmaAndroidPrefs";

	Button save;
	EditText server;
	EditText remote_key;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_preferences);

		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);

		server = (EditText) findViewById(R.id.server);
		remote_key = (EditText) findViewById(R.id.remote_key);

		save = (Button) findViewById(R.id.save);

		server.setText(settings.getString("server", ""));
		remote_key.setText(settings.getString("remote_key", ""));

		save.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				finish();
			}
		});
	}
	
	protected void onPause() {
		super.onPause();
		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		Editor editor = settings.edit();
		editor.putString("server", server.getText().toString());
		editor.putString("remote_key", remote_key.getText().toString());
		editor.commit();
	}

}
