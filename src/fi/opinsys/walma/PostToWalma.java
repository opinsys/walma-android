package fi.opinsys.walma;

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
import org.apache.http.conn.HttpHostConnectException;
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
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Toast;
import android.app.ProgressDialog;


public class PostToWalma extends Activity {
	public static final String PREFS_NAME = "WalmaAndroidPrefs";

	Bundle extras;
	Intent intent;
	String server;
	String camera_id;
	
	ProgressDialog dialog;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		dialog = ProgressDialog.show(this, null, getString(R.string.sending_image));

		setContentView(R.layout.activity_post_to_walma);
		
		
		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);

		server = settings.getString("server", "");
		camera_id = settings.getString("camera_id", "");
		
		intent = getIntent();
		extras = intent.getExtras();
		
	    Intent preferenceIntent = new Intent(this, Preferences.class);
	    
		if (server.length() <= 1) {
			PostToWalma.this.notify(getString(R.string.set_server));
	        startActivityForResult(preferenceIntent, 0);	        
	        return;
		}
		if (camera_id.length() == 0) {
			PostToWalma.this.notify(getString(R.string.set_camera_id));
	        startActivityForResult(preferenceIntent, 0);
	        return;
		}

		new UploadImageTask().execute();
	}

	private void notify(CharSequence text) {
		Context context = getApplicationContext();
		Toast toast = Toast.makeText(context, text, Toast.LENGTH_LONG);
		toast.show();
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

	private String getImgMimeType() {
		ContentResolver cr = getContentResolver();
		return cr.getType(getImgUri());
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
			if(!server.startsWith("http://") && !server.startsWith("https://")) {
				server = "http://" + server;            
            }
			
			if( server.substring( server.length() - 1 ).equals("/") ) {
				server = server.substring(0, server.length() - 1);				
			}

			HttpClient httpclient = new DefaultHttpClient();
			httpclient.getParams().setParameter(
					CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
			HttpPost httppost = new HttpPost(
					server + "/api/create_multipart");
						
			try {

				MultipartEntity entity = new MultipartEntity(
						HttpMultipartMode.STRICT, null,
						Charset.forName("UTF-8"));

				entity.addPart("cameraId", escapeToStringBody(camera_id
						.toString()));
								
				entity.addPart("image", new InputStreamBody(
						getImgInputStream(), getImgMimeType(), "image.jpg"));

				httppost.setEntity(entity);
				ResponseHandler<String> responseHandler = new BasicResponseHandler();
				res = httpclient.execute(httppost, responseHandler);

			} catch (ClientProtocolException e) {				
				err = e.toString();
			} catch (HttpHostConnectException e) {
				err = getString(R.string.can_not_connect_to_server);
			} catch (IOException e) {
				err = e.toString();
			}
		}

		@Override
		protected Void doInBackground(Void... params) {
			uploadImage();
			return null;
		}

		public void onPostExecute(Void n) {
			if (err != null) {
				PostToWalma.this.notify( err );
				PostToWalma.this.finish();
				return;
			}
			
			
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
				String url;
				
				try {
					url = json.getString("url");
				} catch (JSONException e) {
					url = "server did not give a url";
				}

				PostToWalma.this.notify( getString(R.string.image_sent) + server + url );
			} else {
				PostToWalma.this.notify(getString(R.string.error_sending_picture) + err);
			}
			PostToWalma.this.finish();
		}

	}    
}