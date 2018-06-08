package android.test.clientsocket;

import android.annotation.TargetApi;
import android.compact.impl.TaskPayload;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

public class MainActivity extends AppCompatActivity {

    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mHandler = new Handler(new HandlerThread("ws-client-single-thread") {{
            start();
        }}.getLooper());

        final TextView hint1 = (TextView) findViewById(R.id.hint1);
        final Button btn1 = (Button) findViewById(R.id.btn1);
        btn1.setOnClickListener(new View.OnClickListener() {
            @TargetApi(Build.VERSION_CODES.HONEYCOMB)
            @Override
            public void onClick(View v) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        WebSocketClientManager.get().init("ws://45.32.40.65:8080/echo");
                        final boolean success = WebSocketClientManager.get().syncConnect();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(), "Connect[" + success + "]", Toast.LENGTH_LONG).show();
                                hint1.setText("Switching Protocols");
                            }
                        });
                    }
                });
            }
        });

        final TextView hint2 = (TextView) findViewById(R.id.hint2);
        final Button btn2 = (Button) findViewById(R.id.btn2);
        btn2.setOnClickListener(new View.OnClickListener() {
            @TargetApi(Build.VERSION_CODES.HONEYCOMB)
            @Override
            public void onClick(View v) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        final TaskPayload payload = WebSocketClientManager.get().syncSendMsg("hello");
                        final boolean success = payload != null;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(), "Send[" + success + "]", Toast.LENGTH_LONG).show();
                                hint2.setText(payload == null ? "" : new Gson().toJson(payload));
                            }
                        });
                    }
                });
            }
        });

        final TextView hint3 = (TextView) findViewById(R.id.hint3);
        final Button btn3 = (Button) findViewById(R.id.btn3);
        btn3.setOnClickListener(new View.OnClickListener() {
            @TargetApi(Build.VERSION_CODES.HONEYCOMB)
            @Override
            public void onClick(View v) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        final boolean success = WebSocketClientManager.get().syncDisconnect();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(), "Disconnect[" + success + "]", Toast.LENGTH_LONG).show();
                                hint3.setText("Closed");
                            }
                        });
                    }
                });
            }
        });
    }
}
