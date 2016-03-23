package com.dd.webserver.ui;

import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.dd.webserver.R;
import com.dd.webserver.ftp.common.FsService;
import com.dd.webserver.ftp.common.FsSettings;
import com.dd.webserver.jetty.server.FileServer;
import com.dd.webserver.jetty.server.JServer;
import com.dd.webserver.netty.NettyServer2;
import com.dd.webserver.util.FileUtil;
import com.dd.webserver.util.LogUtil;
import com.dd.webserver.util.Utils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * Created by dong on 2015-12-23 0023.
 */
public class MainActivity extends AppCompatActivity {
    private static final int PORT = 8084;
    private static final int FILE_PORT = 8090;
    private TextView infoTextView;
    private TextView fileTextView;
    private TextView ftpTextView;
    private Button conButton;
    private Button fileButton;
    private Button ftpButton;
    private String mIpAddress;
    private JServer mJServer;
    private FileServer mFileServer;

    private FtpSettingsDialog mFtpSettingsDialog;

    private String mInfo = "";

    private Intent serverService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        infoTextView = (TextView) findViewById(R.id.text);
        fileTextView = (TextView) findViewById(R.id.fitext);

        conButton = (Button) findViewById(R.id.btn);
        fileButton = (Button) findViewById(R.id.file_btn);

        ftpTextView = (TextView) findViewById(R.id.ftp_text);
        ftpButton = (Button) findViewById(R.id.ftp_btn);

        mInfo = getResources().getString(R.string.internet_addr);
        try {
            mIpAddress = GetIpAddress();
            if (!mIpAddress.equals("")) {
                infoTextView.setText(R.string.has_internet);
                fileTextView.setText(R.string.has_internet);
                ftpTextView.setText(R.string.has_internet);
                conButton.setEnabled(true);
                fileButton.setEnabled(true);
                ftpButton.setEnabled(true);
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        mJServer = new JServer(this, PORT);
        mFileServer = new FileServer(this, FILE_PORT);
        serverService = new Intent(this, FsService.class);
        mFtpSettingsDialog = new FtpSettingsDialog(this);
    }

    @Override
    protected void onDestroy() {
        if (mJServer.isStarted()) mJServer.stop();
        if (mFileServer.isStarted()) mFileServer.stop();
        super.onDestroy();
    }

    public void onButtonStart(View view) {
        if (mJServer.isStarted()) {
            mJServer.stop();
            ((Button) view).setText(R.string.btn_start);
            infoTextView.setText(mIpAddress.isEmpty() ? R.string.no_internet : R.string.has_internet);
        } else {
            mJServer.start();
            ((Button) view).setText(R.string.btn_stop);
            Utils.serverIpPort = mInfo + "http://" + mIpAddress + ":" + PORT;
            infoTextView.setText(mInfo + "http://" + mIpAddress + ":" + PORT);
        }
    }

    public void onFileStart(View view) {
        if (mFileServer.isStarted()) {
            mFileServer.stop();
            ((Button) view).setText(R.string.btn_file_start);
            fileTextView.setText(mIpAddress.isEmpty() ? R.string.no_internet : R.string.has_internet);
        } else {
            mFileServer.start();
            ((Button) view).setText(R.string.btn_file_stop);
            Utils.serverFileIpPort = mInfo + "http://" + mIpAddress + ":" + FILE_PORT;
            fileTextView.setText(mInfo + "http://" + mIpAddress + ":" + FILE_PORT);
        }
    }

    public void onFtpStart(View view) {
        new Thread() {
            public void run() {
                startNettyServer();
            }

        }.start();

//        if (FsService.isRunning()) {
//            stopService(serverService);
//            ((Button) view).setText(R.string.btn_file_start);
//            fileTextView.setText(mIpAddress.isEmpty() ? R.string.no_internet : R.string.has_internet);
//        } else {
//            InetAddress address = FsService.getLocalInetAddress();
//            if (address == null) {
//                Log.v("FTP", "Unable to retrieve wifi ip address");
//                ftpTextView.setText(R.string.no_internet);
//                return;
//            }
//            String iptext = "ftp://" + address.getHostAddress() + ":" + FsSettings.getPortNumber() + "/";
//            ftpTextView.setText(iptext);
//            startService(serverService);
//            ((Button) view).setText(R.string.btn_file_stop);
//        }
    }

    private void startNettyServer() {
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
        String dhIp = "dh_ip:" + FormatIP(dhcpInfo.ipAddress) + "\n"
                + "dh_gateway" + FormatIP(dhcpInfo.gateway);
        String ipAddress = intToIp(wifiInfo.getIpAddress());

        LogUtil.i("saveFile" + FileUtil.getSDCardRoot());
        NettyServer2 nettyServer = new NettyServer2();
        try {
            nettyServer.run(8888, ipAddress);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static String FormatIP(int IpAddress) {
        return Formatter.formatIpAddress(IpAddress);
    }

    public static String intToIp(int i) {
        return ((i) & 0xFF) + "." + ((i >> 8) & 0xFF) + "."
                + ((i >> 16) & 0xFF) + "." + (i >> 24 & 0xFF);
    }

    public void onFtpSettings(View view) {
        mFtpSettingsDialog.show();
    }

    /**
     * if (intf.getName().toLowerCase().equals("eth0") || intf.getName().toLowerCase().equals("wlan0"))
     * 表示:仅过滤无线和有线的ip. networkInterface是有很多的名称的
     * 比如sim0,remt1.....等等.我不需要用到就直接过滤了
     * <p/>
     * if (!ipaddress.contains("::"))
     * 表示: 过滤掉ipv6的地址.不管无线还是有线 都有这个地址,
     * 我这边显示地址大体是:fe80::288:88ff:fe00:1%eth0 fe80::ee17:2fff:fece:c0b4%wlan0
     * 一般都是出现在第一次循环.第二次循环就是真正的ipv4的地址.
     *
     * @return
     * @throws SocketException
     */
    private String GetIpAddress() throws SocketException {
        String ipaddress = "";
        Enumeration<NetworkInterface> netInterfaces = null;
        try {
            netInterfaces = NetworkInterface.getNetworkInterfaces();
            while (netInterfaces.hasMoreElements()) {
                NetworkInterface intf = netInterfaces.nextElement();
                if (intf.getName().toLowerCase().equals("eth0") || intf.getName().toLowerCase().equals("wlan0")) {
                    for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                        InetAddress inetAddress = enumIpAddr.nextElement();
                        if (!inetAddress.isLoopbackAddress()) {
                            ipaddress = inetAddress.getHostAddress().toString();
                            if (!ipaddress.contains("::")) {// ipV6的地址
                                ipaddress = ipaddress;
                            }
                        }
                    }
                } else {
                    continue;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return ipaddress;
    }
}
