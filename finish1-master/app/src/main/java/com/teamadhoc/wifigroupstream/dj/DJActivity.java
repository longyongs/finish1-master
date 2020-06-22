package com.teamadhoc.wifigroupstream.Player;
import java.io.File;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Parcelable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.teamadhoc.wifigroupstream.R;
import com.teamadhoc.wifigroupstream.Timer;
import com.teamadhoc.wifigroupstream.Player.ServerDeviceListFragment.DJFragmentListener;
public class DJActivity extends Activity implements WifiP2pManager.ChannelListener,
        DJFragmentListener { // DjAc
    // tivity에 implements WifiP2pManager.ChannelListener,DJFragmentListener를 이용한 경우
    public final static int DJ_MODE = 15; // 그룹 소유자 인 가장 높은 성향을 나타냄, 클래스에서 사용할 해당 멤버 변수의 데이터와 그 의미, 용도를 고정
    public static final String TAG = "DJActivity"; // ""
    private WifiP2pManager manager;
    private boolean channelRetried = false; //
    private boolean isWifiP2pEnabled = false;
    private BroadcastReceiver receiver = null; // 브로드캐스드 받는쪽
    ProgressDialog progressDialog = null;
    private Timer timer;
    private CountDownTimer keepAliveTimer;
    private static final int KEEPALIVE_INTERVAL = 5000; //5 초마다 Wi-Fi를 활성 상태로 유지
    private WifiP2pManager.Channel channel;
    private final IntentFilter intentFilter = new IntentFilter();

    @Override
    protected void onCreate(Bundle savedInstanceState) { // onCreate(java에서 main역할)에서는 레이아웃 생성 및 초기화 컴포넌트 불러옴(맨처음 Ativity 시작때 호출)
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dj);

        //Wi-Fi Direct 이벤트를 포착하기위한 인 텐트 필터 ,
        // * intent : 안드로이드에서 페이지 전환과 페이지간 데이터 전달은 Intent를 통해서 구현( 폰-폰간 데이터 전달 및 화면 전환)
        //인텐트는 앱 컴포넌트가 무엇을 할 것인지를 담는 메시지 객체입니다.
        // 메시지는 의사소통을 하기 위해 보내고 받는 것이지요.
        // 메시지를 사용하는 가장 큰 목적은 다른 액티비티,서비스,브로드캐스트 리시버,컨텐트 프로바이더 등을 실행하는 것입니다.
        // 인텐트는 그들 사이에 데이터를 주고 받기 위한 용도
        //특정 컴포넌트에서 암시적 인텐트를 받기 위해서는 매니페스트 파일에서 요소와 함께 어플리케이션 컴포넌트 각각에 대해서 하나 이상의 IntentFilter를 선언
        /* IntentFilter : 각 인텐트 필터는 인텐트의 작업, 데이터 및 카테고리를 기반으로 하여 어느 유형의 인텐트를 수락하는지 지정한다.
          시스템은 인텐트가 인텐트 필터 중 하나를 통과한 경우에만 암시적 인텐트를 앱 구성 요소에 전달
         이미지 갤러리 앱에 있는 어떤 액티비티에 두 개의 필터가 있을 수 있다. 한 필터는 이미지를 보고, 다른 필터는 이미지를 편집하기 위한 것이다.
         액티비티가 시작되면 , Intent를 검사한 다음 Intent에 있는 정보를 근거로 어떻게 동작할 것인지를 결정 */

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        //  [Wi-Fi P2P 활성화시]
        //와이파이 디바이스가 활성화된 뒤에는 와이파이의 연결상태가 변했음을 알리는 WIFI_P2P_STATE_CHANGED_ACTION
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        // [ 연결가능한 피어 목록이 변경시(requestPeers() 구현)]

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        // [ Wi-Fi P2P 연결상태(1:N연결을 위해 여기서 requestConnectionInfo() 호출 하도록 구현)]
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        // [ 현재 단말 상태정보]
        //어플리케이션이 수행되는 단말기의 상태가 변했을 때 발생하는 WIFI_THIS_DEVICE_CHANGED_ACTION

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        // getSystemService : 안드로이드가 제공하는 시스템-레벨 서비스를 요청함.
        channel = manager.initialize(this, getMainLooper(), null); // 초기화
        //onCreate() 메서드가 끝나면 WifiP2pManager의 인스턴스를 가져와 initialize() 메서드를 호출하세요.
        // 이 메서드는 WifiP2pManager.Channel 개체를 반환하며 이 개체는 나중에 앱을 Wi-Fi P2P 프레임워크에 연결하는 데 사용
        // ??? 모르겠음
        // https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager 참고

        //25ms 정밀도로 타이머 시작
        this.timer = new Timer(Timer.DEFAULT_TIMER_PRECISION); // DEFAULT_TIMER_PRECISION 안나옴
        // 타이머를 시작하기위한 비동기 호출
        this.timer.startTimer();

        keepAliveTimer = new CountDownTimer(KEEPALIVE_INTERVAL, KEEPALIVE_INTERVAL) { // CountDownTimer(제한시간, 시간간격) { //제한시간동안 시간간격으로 줄어듦
            /* keepalive를 사용하는 주된 이유는 종단 시스템 중의 하나가 다운될 때 발생할 수 있는 한쪽만 열린 연결 상태를 정리하는 것
             출처: https://hamait.tistory.com/341 [HAMA 블로그]
             흠 모르겠네.... KEEPALIVE 참고 (TCP/IP): https://hamait.tistory.com/341 */
            @Override
            public void onTick(long millisUntilFinished) {
            } /* //타이머가 종료 될 떄 까지 동작하는 onTick함수
            생성자에서 지정 해준 시간 간격이 지날 때 마다 일어날 일을 정의합니다.
              넘겨 받는 millisUntilFinished 라는 인자는, 호출 된 시점부터 종료될 때 까지 남은 시간입니다.  https://gdtbgl93.tistory.com/2
              CountDownTimer*/

            @Override
            public void onFinish() { ////타이머가 종료될 때 실행되는 onFinish함수
                enableWifi();
                keepAliveTimer.start(); //타이머 함수를 실행한다.
            }
        };
    }
    // wi-fi direct 무선랜 디바이스 초기화/ 주변의 디바이스 검색/ 디바이스에 연결하기(사용자 인증,그룹 생성, 오너 결정, IP 주소 배정)

    @Override
    public void onResume() { /*Activity가 전면에 나타날 때 대부분의 상황에서 호출된다. 처음 실행했을 때, onCreate() 이후에도 호출된다. 출처: https://mydevromance.tistory.com/21 [My Dev Romance] */
        super.onResume();
        //Android Beam (NFC)으로 인해 활동이 시작되었는지 확인 (Check to see that the Activity started due to an Android Beam (NFC))
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
            processIntent(getIntent());
        } else {
            receiver = new ServerWiFiDirectBroadcastReceiver(manager, channel, this);
            // 일치하는 의도 값을 사용하여 BroadcastReceiver 등록 (Register the BroadcastReceiver with the intent values to be matched)
            registerReceiver(receiver, intentFilter);

            // 즉시 발견해라 (Start discovering right away!)
            discoverDevices();
            keepAliveTimer.start();
        }
    }

    //https://m.blog.naver.com/PostView.nhn?blogId=wndrlf2003&logNo=70186022536&proxyReferer=https:%2F%2Fwww.google.com%2F NFC 통신 매소드
    // NFC 메시지 수신시 호출 (Called when NFC message is received)
    @Override
    public void onNewIntent(Intent intent) {
        // OnResume은 이 후에 의도를 처리하기 위해 호출 (onResume gets called after this to handle the intent)
        Log.d(TAG, "onNewIntent");
        setIntent(intent);
    }

    // Parses the NDEF (NFC) Message from the intent
    private void processIntent(Intent intent) {
        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(
                NfcAdapter.EXTRA_NDEF_MESSAGES);
        // 빔 중에 하나의 메시지 만 보냄 (Only one message sent during the beam)
        NdefMessage msg = (NdefMessage) rawMsgs[0];
        WifiP2pConfig config = new WifiP2pConfig();
        // 기록 0은 MIME 유형을 포함하며, 기록 1은 AAR(있는 경우) (Record 0 contains the MIME type, record 1 is the AAR, if present)
        config.deviceAddress = new String(msg.getRecords()[0].getPayload());
        config.wps.setup = WpsInfo.PBC;
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        //안드로이드 애플리케이션을 개발하다 보면, Dialog 부류의 위젯을 많이 사용하게 된다. 그리고 이 위젯을 화면에 보여주고 완료하면 dismiss()를 호출해서 종료를 시킨다
        progressDialog = ProgressDialog.show(this, "Press back to cancel",
                "Connecting to: " + config.deviceAddress, true, true);
        // Wi-Fi Direct를 통해 config.deviceAddress를 사용하여 장치에 연결 (Connect to the device with config.deviceAddress through Wi-Fi Direct)
        connect(config);
    }

    // Wi-Fi가 비활성화된 경우 사용 (Enable Wi-Fi if it has been disabled)
    public void enableWifi() {
        WifiManager wifiManager = (WifiManager) this.getApplicationContext().getSystemService(this.WIFI_SERVICE);
        wifiManager.setWifiEnabled(true); //와이파이 활성화
    }
    // 와이파이가 활성화 되어있는지 확인

    public void discoverDevices() {
        //Wi-Fi P2P 켜기Turn on the Wi-Fi P2P
        enableWifi();
        channelRetried = false;

        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {  //WifiP2pManager.ActionListener를 제공하여 discoverPeers()를 호출
            @Override
            public void onSuccess() {
                Log.d(TAG, "Discovery Initiated.");
            }
            //ActionListener.onSuccess() 및 ActionListener.onFailure() 메서드로 알림을 받을 수 있음
            // If we failed, then stop the discovery and start again
            //WIFI_P2P_PEERS_CHANGED_ACTION 인텐트는 discoverPeers() 메서드가 피어 목록이 변경된 것을 발견했는지 브로드캐스트
            @Override
            public void onFailure(int reasonCode) {
                Log.e(TAG, "Discovery Failed. Error Code is: " + reasonCode);
                manager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onFailure(int reason) {
                        Log.e(TAG, "Stopping Discovery Failed. Error Code is: " + reason);
                    }

                    @Override
                    public void onSuccess() {
                        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                Log.d(TAG, "Discovery Initiated.");
                            }

                            @Override
                            public void onFailure(int reasonCode) {
                                Log.e(TAG, "Discovery Failed. Error Code is: " + reasonCode);
                            }
                        });
                    }
                });
            }
        });
    }
    // 오류날떄 코드 같음
    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
        keepAliveTimer.cancel(); //타이머 함수의 동작을 중지한다.
    }

    @Override
    public void onDestroy() {
        disconnect();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_dj, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.atn_direct_enable:
                if (manager != null && channel != null) {
                    // Since this is the system wireless settings activity, it's
                    // not going to send us a result. We will be notified by
                    // the Broadcast Receiver instead.
                    //이것은 시스템 무선 설정 활동이므로 결과를 보내지 않습니다. 대신 브로드 캐스트 리시버가 알림을받습니다.
                    Intent intent = new Intent();
                    //Wi-Fi Direct 설정으로 이동 (Jump to Wi-Fi Direct settings)
                    intent.setClassName("com.android.settings",
                            "com.android.settings.Settings$WifiP2pSettingsActivity");
                    startActivity(intent);
                } else {
                    Log.e(TAG, "Channel or manager is null");
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
     /* onCreateOptionsMenu() 는  최초에 메뉴키가 눌렸을 때 호출되고
        onOptionsItemSelected() 는 메뉴 아이템이 클릭되었을때 호출됩니다.
        그리고, 이번 예제에선  onPrepareOptionsMenu() 를 추가하여 동작을 확인합니다. onPrepareOptionsMenu() 는 옵션메뉴가 화면에 보여질때마다 호출됩니다.
        언제 호출되는가를 확인하기 위해 Log.d()를 사용하여 확인해봅니다.
        출처: https://bitsoul.tistory.com/21 [Happy Programmer~] */

    /**
     * Remove all peers and clear all fields. This is called on
     * BroadcastReceiver receiving a state change event. This is merely an UI update.
     모든 피어를 제거하고 모든 필드를 지우십시오. 상태 변경 이벤트를 수신하는 BroadcastReceiver에서 호출됩니다. 이것은 단지 UI 업데이트입니다.*/

    public void resetDeviceList() {
        ServerDeviceListFragment fragmentList = (ServerDeviceListFragment) getFragmentManager()
                .findFragmentById(R.id.frag_dj_devices);

        if (fragmentList != null) {
            fragmentList.clearPeers();
        }
    }

    @Override
    public void onChannelDisconnected() {
        // 한 번 더 시도 (We will try once more)
        if (manager != null && !channelRetried) {
            Toast.makeText(this, "Wi-Fi Direct Channel lost. Trying again...",
                    Toast.LENGTH_LONG).show();
            resetDeviceList();

            channelRetried = true;
            manager.initialize(this, getMainLooper(), this);
        } else {
            Toast.makeText(this,
                    "Wi-Fi Direct Channel is still lost. Try disabling / re-enabling Wi-Fi Direct in the P2P Settings.",
                    Toast.LENGTH_LONG).show();
        }
    }

    /* 연결이 안되서 다시 접속시도 하는 상황인듯
     * 진행중인 연결 취소 (Cancel an ongoing connection in progress.)
     */
    @Override
    public void cancelDisconnect() {
        if (manager != null) {
            Log.d(TAG, "Someone requested a cancel connect!");

            final ServerDeviceListFragment fragment = (ServerDeviceListFragment) getFragmentManager()
                    .findFragmentById(R.id.frag_dj_devices);

            if (fragment.getDevice() == null || fragment.getDevice().status == WifiP2pDevice.CONNECTED) {
                // disconnect();
            } else if (fragment.getDevice().status == WifiP2pDevice.AVAILABLE
                    || fragment.getDevice().status == WifiP2pDevice.INVITED) {
                manager.cancelConnect(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        Log.e(TAG, "Could not abort connection, the reason is: " + reasonCode);
                    }
                });
            }
        }
    }

    /* Wi-Fi Direct를 통해 장치에 연결하는 주요 방법입니다
     * This is the main method to connect to a device through Wi-Fi Direct
     */
    @Override
    public void connect(WifiP2pConfig config) {
        if (manager == null) {
            return;
        }

        // DJ 모드에서는 그룹 소유자가되고 싶습니다
        // In DJ mode, we want to become the group owner
        WifiP2pConfig newConfig = config;
        newConfig.groupOwnerIntent = DJ_MODE; //그룹 소유자 인 가장 높은 성향을 나타냅 (Indicates the highest inclination to be a group owner)

        manager.connect(channel, newConfig, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                //방송 수신기가 알려줍니다. 지금은 무시 (The Broadcast Receiver will notify us. Ignore for now.)
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(DJActivity.this,
                        "Connection failed. Retrying...", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Wi-Fi Direct connection failed. The error code is: " + reason);
            }
        });
    }
    // 와이파이 다이렉트 연결 실패를 상태메시지로 나타내 주는것 같음

    @Override
    public void disconnect() {
        if (manager == null) {
            return;
        }

        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onFailure(int reasonCode) {
                Log.e(TAG, "Disconnect failed. Reason is: " + reasonCode);
            }

            @Override
            public void onSuccess() {
                Toast.makeText(DJActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Disconnected from a device.");
            }
        });
    }

    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }

    public void playRemoteMusic(String musicFilePath, long startTime, int startPos) {
        ServerDeviceListFragment fragmentList = (ServerDeviceListFragment) getFragmentManager()
                .findFragmentById(R.id.frag_dj_devices);

        File audioFile = new File(musicFilePath);
        fragmentList.playMusicOnClients(audioFile, startTime, startPos);
    }
    // 음악 목록에서 파일  추가하는 것 같음
    public void stopRemoteMusic() {
        ServerDeviceListFragment fragmentList = (ServerDeviceListFragment) getFragmentManager()
                .findFragmentById(R.id.frag_dj_devices);
        fragmentList.stopMusicOnClients();
    }

    public Timer getTimer()
    {
        return timer;
    }
}    // 그만두는것 같음