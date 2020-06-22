package com.teamadhoc.wifigroupstream.Player;

import NanoHTTPD.NanoHTTPD;
import NanoHTTPD.SimpleWebServer;
import android.app.Activity;
import android.app.ListFragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.teamadhoc.wifigroupstream.R;
import com.teamadhoc.wifigroupstream.Utilities;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * A ListFragment that displays available peers on discovery and requests the
 * parent activity to handle user interaction events
 */
public class ServerDeviceListFragment extends ListFragment // ListFragment를 상속받아 사용자 요구에 맞는 Listview 제작 https://recipes4dev.tistory.com/63
        implements //추상메소드 인터페이스 implements <완성되지 않은 method>
        WifiP2pManager.PeerListListener,//Wi-Fi P2P에서 감지한 동종 기기에 관한 정보(안드로이드겠지..?)를 제공하는 인터페이스 또한 이 정보를 사용하면 앱에서 동종 기기가 네트워크에 들어오거나 나가는 시기를 알 수 있습니다. https://developer.android.com/training/connect-devices-wirelessly/wifi-direct?hl=ko
        WifiP2pManager.ConnectionInfoListener,
        //WifiP2pManager.ActionListener(connect() 메서드에 구현됨)만이 시작에 성공하거나 실패했을 때를 알려줍니다.
        // 연결 상태의 변경사항을 수신 대기하려면 WifiP2pManager.ConnectionInfoListener 인터페이스를 구현하세요.
        // 연결 상태가 변경되면 onConnectionInfoAvailable() 콜백이 알려줍니다.
        // 여러 기기가 단일 기기에 연결되는 경우(예: 플레이어가 세 명 이상인 게임, 채팅 앱)
        // 한 기기가 '그룹 소유자'로 지정됩니다. ->마스터
        // 그룹 만들기 섹션의 단계에 따라 특정 기기를 네트워크의 그룹 소유자로 지정할 수 있습니다.
        Handler.Callback {
        //Callback interface you can use when instantiating a Handler to avoid having to implement your own subclass of Handler.

    public static final String TAG = "ServerDeviceList"; // 상수 문자열 TAG="ServerDeviceList" //Logcat 로그 메소드의 첫 번째 인자로 사용할 상수 TAG 정의
    private List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>(); // peers 배열생성  WifiP2pDevice
    ProgressDialog progressDialog = null; // 앱에서 시간이 걸리는 작업을 수행할 때 ProgressDialog 클래스를 이용하면 사용자에게 실시간 진행 상태를 알릴 수 있습니다.
    //진행이 끝나면 팝업창은 사라지게 된다. <스핀(돌아가는 그림)>, <바> 형태 다이얼로그 https://mainia.tistory.com/2031
    private View contentView = null; // 일반적 View
    private WifiP2pDevice device; //device 변수
    private final Handler handler = new Handler(this); //https://itmining.tistory.com/16
    private ServerSocketHandler serverThread; //
    private String httpHostIP = null;
    private Activity activity = null;
    private File wwwroot = null;
    private NanoHTTPD httpServer = null;
    public static final int HTTP_PORT = 9002;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity = activity;
        // Get the application directory
        wwwroot = activity.getApplicationContext().getFilesDir();
    }
    //안드로이드API 23부터 Fragment클래스에서 onAttach()가 deprecated
    //프래그먼트가 액티비티와 연결되어 있었던 경우 호출됩니다(여기에서 Activity가 전달됩니다).

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        contentView = inflater.inflate(R.layout.connected_list, null);
        return contentView;
    }
    //inflater는 xml로 정의된 view (또는 menu 등)를 실제 객체화 시키는 용도입니다.
    //예를 들어 약간 복잡한 구조의 view를 java코드로 만들게 되면 생성하고 속성 넣어주느라 코드가 길어질 수 있는데,
    //그걸 미리 xml로 만들어 놓고 java코드에서는 inflater를 활용하여 바로 view를 생성할 수 있습니다.
    // 뷰 생성

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        this.setListAdapter(new WiFiPeerListAdapter(getActivity(), R.layout.row_devices, peers));
    }
    // Fragment 생성 이후 호출하는 함
    // Disconnect the server when this fragment is no longer visible.
    @Override
    public void onDestroyView() {
        stopServer();
        super.onDestroyView();
    }
    //Fragment의 View가 모두 소멸될 때 호출되는 콜백 메소드이다. 이때 View에 관련된 모든 자원들이 사라지게 된다.

    public WifiP2pDevice getDevice() {
        return device;
    }
    //getDevice 함수 return값으로 device 반환

    private static String getDeviceStatus(int deviceStatus) { // Device 상태 메소드
        Log.d(TAG, "Peer status :" + deviceStatus);
        // Logcat 로그 메소드의 첫 번째 인자로 사용할 상수 TAG 메세지들을 구분하는 구분 값으로 사용되어 집니다. 보통 현재 클래스의 이름을 많이 사용합니다. TAG의 길이가 23자를 넘으면 logcat 출력에서 잘립니다.
        switch (deviceStatus) {
            case WifiP2pDevice.AVAILABLE: // 허용
                return "Available";
            case WifiP2pDevice.INVITED: // 초대된
                return "Invited";
            case WifiP2pDevice.CONNECTED: // 연결된
                return "Connected";
            case WifiP2pDevice.FAILED: // 실패
                return "Failed";
            case WifiP2pDevice.UNAVAILABLE: //이용할 수 없는
                return "Unavailable";
            default:
                return "Unknown";
        }
    }
    //Device상태 메소드

    /**
     * Perform an action with a peer, depending on its state
     */
    @Override
    public void onListItemClick(ListView l, View v, int position, long id) { // ListActivity 상속 받았을 때 intend에 id 값을 실어서 보내는 메소드
        WifiP2pDevice device = (WifiP2pDevice) getListAdapter().getItem(position);
        switch (device.status) {
            case WifiP2pDevice.AVAILABLE:
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;
                config.wps.setup = WpsInfo.PBC;
                progressDialog = ProgressDialog.show(getActivity(), "Press back to cancel",
                        "Connecting to: " + device.deviceName, true, true);

                ((PlayerFragmentListener) getActivity()).connect(config);
                break;

            case WifiP2pDevice.INVITED:
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }

                progressDialog = ProgressDialog.show(getActivity(), "Press back to abort",
                        "Revoking invitation to: " + device.deviceName, true, true);

                ((PlayerFragmentListener) getActivity()).cancelDisconnect();
                // Start another discovery
                ((PlayerFragmentListener) getActivity()).discoverDevices();
                break;

            case WifiP2pDevice.CONNECTED:
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }

                progressDialog = ProgressDialog.show(getActivity(), "Press back to abort",
                        "Disconnecting: " + device.deviceName, true, true);

                ((PlayerFragmentListener) getActivity()).disconnect();
                // Start another discovery
                ((PlayerFragmentListener) getActivity()).discoverDevices();
                break;

            // Refresh the list of devices
            case WifiP2pDevice.FAILED:
            case WifiP2pDevice.UNAVAILABLE:
            default:
                ((PlayerFragmentListener) getActivity()).discoverDevices();
                break;
        }
    }
    //리스트 클릭시 발생하는 메소드

    /**
     * Array adapter for ListFragment that maintains WifiP2pDevice list.
     */

    private class WiFiPeerListAdapter extends ArrayAdapter<WifiP2pDevice> {

        private List<WifiP2pDevice> items;

        public WiFiPeerListAdapter(Context context, int textViewResourceId,
                                   List<WifiP2pDevice> objects) {
            super(context, textViewResourceId, objects);
            items = objects;
        }


        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                LayoutInflater vi = (LayoutInflater) getActivity().getSystemService(
                        Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.row_devices, null);
            }
            WifiP2pDevice device = items.get(position);
            if (device != null) {
                TextView top = (TextView) v.findViewById(R.id.device_name);
                TextView bottom = (TextView) v.findViewById(R.id.device_details);
                if (top != null) {
                    top.setText(device.deviceName);
                }
                if (bottom != null) {
                    if (device.status == WifiP2pDevice.INVITED) {
                        // Show the invited dialog
                        if (progressDialog != null && progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }

                        progressDialog = ProgressDialog.show(getActivity(), "Inviting peer",
                                "Sent invitation to: " + device.deviceName +
                                        "\n\nTap on peer to revoke invitation.", true, true);
                    }
                    bottom.setText(getDeviceStatus(device.status));
                }
            }
            return v;
        }
        // 실제 화면에 그려지는 아이템을 ConvertView 라는 배열로 관리하는데, 화면에 보여지는 만큼 Convert View를 생성하고
        // 스크롤시 View를 재활용하기 때문에 성능면으로 우수한 구조이다. ListView의 재활용 View인Convertview는 Adapter의 getView( )를 통해서 관리된다.
        // ListView는 화면에 새로운 아이템을 표시하라 때마다 Adapter의 getView( )를 호출하게 된다. 여기서 getView 메소드는 각 View를 보여줄 때마다 호출되기 때문에
        // 5개의 View를 보여줄 때 무조건 5번의 호출이 이루어지게 된다.
        // ListView는 자원을 재사용 할 때는 null이 아닌 값이 들어오게 되며, null인 경우에는 레이아웃을 inflate한다.
        // ConvertView가 null이 아닌 경우에는 기존의 View 를 재사용하기 때문에 새롭게 View를 inflate 할 필요 없이 데이터만 바꾸는 작업을 진행하면 된다.
        //출처: https://itmining.tistory.com/2 [IT 마이닝]
    }
    //화면에 보여지는 리스트 어댑터 클래스

    /**
     * Update UI for this device.
     */
    public void updateThisDevice(WifiP2pDevice device) {
        this.device = device;
        TextView view = (TextView) contentView.findViewById(R.id.my_name);
        view.setText(device.deviceName);
        view = (TextView) contentView.findViewById(R.id.my_status);
        view.setText(getDeviceStatus(device.status));
    }
    // 디바이스 상태변화?

    @Override
    public void onPeersAvailable(WifiP2pDeviceList peerList) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        peers.clear();
        peers.addAll(peerList.getDeviceList());
        ((WiFiPeerListAdapter) getListAdapter()).notifyDataSetChanged();
        if (peers.size() == 0) {
            Log.d(TAG, "No devices found");
            return;
        }
    }
    // 피어 접속 가능 메소드

    public void clearPeers() {
        peers.clear();
        ((WiFiPeerListAdapter) getListAdapter()).notifyDataSetChanged();
    }
    //Peer 초기화 메소드

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case ServerSocketHandler.SERVER_CALLBACK:
                serverThread = (ServerSocketHandler) msg.obj;
                Log.d(TAG, "Retrieved server thread.");
                break;

            default:
                Log.d(TAG, "Message type: " + msg.what);
                break;
        }
        return true;
    }
    //메인스레드(송신)에 있는 변수 값을 다른 스레드(수신)에서 값을 변경시켜 주기 위해

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        } // 이해안됨...

        // The group owner IP is now known.
        if (info.groupFormed && info.isGroupOwner) { // p2p group이 생성되어 있고 현재의 device가 group의 owner이면  코드 실행
            try {
                // WARNING:
                // depends on the timing, if we don't get a server back in time,
                // we may end up running multiple threads of the server instance!
                if (this.serverThread == null) {
                    Thread server = new ServerSocketHandler(this.handler);
                    server.start();

                    if (wwwroot != null) {
                        if (httpServer == null) {
                            httpHostIP = info.groupOwnerAddress.getHostAddress();

                            boolean quiet = false;

                            httpServer = new SimpleWebServer(httpHostIP, HTTP_PORT, wwwroot, quiet);

                            try {
                                httpServer.start();
                                Log.d("HTTP Server", "Started web server with IP address: " + httpHostIP);
                                Toast.makeText(contentView.getContext(), "Player Server started.",
                                        Toast.LENGTH_SHORT).show();
                            }
                            catch (IOException ioe) {
                                Log.e("HTTP Server", "Couldn't start server:\n");
                            }
                        }
                    } else {
                        Log.e("HTTP Server", "Could not retrieve a directory for the HTTP server.");
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Can't start server.", e);
            }
        } else if (info.groupFormed) {
            // TODO: clear remembered groups (how?) so that Player mode always becomes group owner
            // In Player mode, we must be the group owner, or else we have a problem
            Log.e(TAG, "Player Mode did not become the group owner.");
            Toast.makeText(contentView.getContext(), "Error: Player Mode did not become the group owner.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    public void stopServer() {
        if (serverThread != null) { //serverThread가 null이 아니면 severThread를 끊고 serverThread를 null
            serverThread.disconnectServer();
            serverThread = null;
        }
        if (httpServer != null) { //httpServer가 null이 아니면 httpServer를 중단시키고 httpServer를 null
            httpServer.stop();
            httpServer = null;
        }
    }//서버 중단 메소드

    public void playMusicOnClients(File musicFile, long startTime, int startPos) {
        if (serverThread == null) {
            Log.d(TAG, "Server has not started. No music will be played remotely.");
            return;
        }

        try {
            // Copy the music file to the web server directory, then pass the URL to the client
            File webFile = new File(wwwroot, URLEncoder.encode(musicFile.getName(), "UTF-8"));

            Utilities.copyFile(musicFile, webFile);

            Uri webMusicURI = Uri.parse("http://" + httpHostIP + ":"
                    + String.valueOf(HTTP_PORT) + "/" + webFile.getName());

            serverThread.sendPlay(webMusicURI.toString(), startTime, startPos);
        } catch (IOException e1) {
            Log.e(TAG, "Can't copy file to HTTP Server.", e1);
        }
    }

    public void stopMusicOnClients() {
        if (serverThread != null) {
            serverThread.sendStop();
        }
    }

    /**
     * An interface-callback for the activity to listen to fragment interaction events.
     */
    public interface PlayerFragmentListener {
        void cancelDisconnect();
        void connect(WifiP2pConfig config);
        void disconnect();
        void discoverDevices();
    }
}
