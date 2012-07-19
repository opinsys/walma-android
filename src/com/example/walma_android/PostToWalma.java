package com.example.walma_android;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;

import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreProtocolPNames;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.ClipboardManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


public class PostToWalma extends Activity {
	public static final String PREFS_NAME = "WalmaAndroidPrefs";

	Bundle extras;
	Intent intent;
	String action;

	Button send;
	TextView status;
	EditText server;
	EditText remote_key;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_post_to_walma);

		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);

		ImageView image = (ImageView) findViewById(R.id.imageview);

		server = (EditText) findViewById(R.id.server);
		remote_key = (EditText) findViewById(R.id.remote_key);

		status = (TextView) findViewById(R.id.status);
		send = (Button) findViewById(R.id.send);

		server.setText(settings.getString("server", ""));
		remote_key.setText(settings.getString("remote_key", ""));
		
		intent = getIntent();
		extras = intent.getExtras();
		action = intent.getAction();

		if (isCalledByGallery()) {
			setupSmallImg(image);
		} else {
			notify("Not called by gallery. Nothing to upload.");
			setResult(RESULT_OK);
			finish();
		}

		send.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				if (!isCalledByGallery()) {
					PostToWalma.this
							.notify("Not called by gallery. Nothing to upload.");
				}

				if (server.getText().length() <= 1) {
					PostToWalma.this.notify("Set server");
					return;
				}
				if (remote_key.getText().length() == 0) {
					PostToWalma.this.notify("Set remote key");
					return;
				}

				new UploadImageTask().execute();

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

	private boolean isCalledByGallery() {
		return Intent.ACTION_SEND.equals(action)
				&& extras.containsKey(Intent.EXTRA_STREAM);
	}

	private void notify(CharSequence text) {
		Context context = getApplicationContext();
		Toast toast = Toast.makeText(context, text, Toast.LENGTH_LONG);
		toast.show();
		status.setText(text);
	}

	private InputStream getImgInputStream() throws FileNotFoundException {
		// Get resource path from intent callee
		Uri uri = (Uri) extras.getParcelable(Intent.EXTRA_STREAM);

		// Query gallery for camera picture via
		// Android ContentResolver interface
		ContentResolver cr = getContentResolver();
		return cr.openInputStream(uri);

	}

	private Uri getImgUri() {
		return (Uri) extras.getParcelable(Intent.EXTRA_STREAM);
	}

	private AssetFileDescriptor getImgFile() throws FileNotFoundException {
		ContentResolver cr = getContentResolver();
		return cr.openAssetFileDescriptor(getImgUri(), "r");
	}

	private String getImgMimeType() {
		ContentResolver cr = getContentResolver();
		return cr.getType(getImgUri());
	}

	private void setupSmallImg(ImageView img) {

		Bitmap bitmap;
		try {
			bitmap = BitmapFactory.decodeStream(getImgInputStream());
			double scale = (double) bitmap.getHeight()
					/ (double) bitmap.getWidth();
			img.setImageBitmap(Bitmap.createScaledBitmap(bitmap, 400,
					(int) (400.0 * scale), true));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private class UploadImageTask extends AsyncTask<Void, Void, Void> {

		String res;
		String err;

		/**
		 * Escape non-ascii characters and try to compatible with
		 * querystring.unescape of Node.js.
		 * 
		 * @param s
		 * @throws UnsupportedEncodingException
		 */
		private StringBody escapeToStringBody(String s)
				throws UnsupportedEncodingException {
			s = URLEncoder.encode(s);
			s = s.replace("+", "%20");
			return new StringBody(s);

		}

		private void uploadImage() {

			HttpClient httpclient = new DefaultHttpClient();
			httpclient.getParams().setParameter(
					CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
			HttpPost httppost = new HttpPost(
					server.getText() + "/api/create_multipart");
						
			try {

				MultipartEntity entity = new MultipartEntity(
						HttpMultipartMode.STRICT, null,
						Charset.forName("UTF-8"));

				entity.addPart("remote_key", escapeToStringBody(remote_key.getText()
						.toString()));
								
				entity.addPart("image", new InputStreamBody(
						getImgInputStream(), getImgMimeType(), "image.jpg"));

				httppost.setEntity(entity);
				ResponseHandler<String> responseHandler = new BasicResponseHandler();
				res = httpclient.execute(httppost, responseHandler);

			} catch (ClientProtocolException e) {
				err = e.toString();
			} catch (IOException e) {
				err = e.toString();
			}
		}

		@Override
		protected Void doInBackground(Void... params) {
			uploadImage();
			return null;
		}

		public void onPreExecute() {
			PostToWalma.this.notify("Sending image...");
		}

		public void onPostExecute(Void n) {
			JSONObject json = null;
			try {
				json = new JSONObject(res);
				
				if (json.has("error") ) {
					err = json.getString("message");
				}
				
			} catch (JSONException e) {
				err = "Got mallformed json response from the server";
			}
			
			
			
			if (err == null) {
				ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
				String url;
				
				try {
					url = json.getString("url");
				} catch (JSONException e) {
					url = "server did not give a url";
				}
				
				
				clipboard.setText(url);
				
				
				PostToWalma.this.notify("Image sent! Copied url to clipboard: "
						+ url);
			} else {
				PostToWalma.this.notify("Error sending picture: " + err);
			}
		}

	}    
}