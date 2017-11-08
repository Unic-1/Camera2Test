package selfie.app.com;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

public class ServiceActivity extends AppCompatActivity {

    private static String TAG = "ServiceActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_service);
    }

    public void startService(View v) {
        Log.i(TAG, "startService: Service Started");
        startService(new Intent(getBaseContext(), MyService.class));
    }

    public void stopService(View v) {
        Log.i(TAG, "stopService: Service Stopped");
        stopService(new Intent(getBaseContext(), MyService.class));
    }
}
