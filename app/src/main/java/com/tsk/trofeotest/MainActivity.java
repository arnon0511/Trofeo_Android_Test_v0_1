package com.tsk.trofeotest;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.usb.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    static final int VID=0x0416, PID=0x5302, WIDTH=1280, HEIGHT=480;
    static final String ACTION_USB_PERMISSION="com.tsk.trofeotest.USB_PERMISSION";
    UsbManager usb; UsbDevice dev; UsbDeviceConnection conn; UsbInterface intf; UsbEndpoint epOut, epIn;
    TextView status, log; Button connect, test, select, send; Bitmap selected;
    final ExecutorService io = Executors.newSingleThreadExecutor();

    final BroadcastReceiver permissionReceiver = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            if (ACTION_USB_PERMISSION.equals(i.getAction())) {
                UsbDevice d = i.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false) && d!=null) openDevice(d);
                else setStatus("USB permission denied");
            }
        }
    };

    @Override public void onCreate(Bundle b){ super.onCreate(b); usb=(UsbManager)getSystemService(USB_SERVICE); registerReceiver(permissionReceiver,new IntentFilter(ACTION_USB_PERMISSION), RECEIVER_NOT_EXPORTED); buildUi(); }
    @Override protected void onDestroy(){ super.onDestroy(); try{unregisterReceiver(permissionReceiver);}catch(Exception ignored){} closeUsb(); io.shutdownNow(); }

    void buildUi(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(28,28,28,28);
        TextView title=new TextView(this); title.setText("TROFEO CONTROL v0.1"); title.setTextSize(28); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); root.addView(title);
        status=new TextView(this); status.setText("Disconnected — 0416:5302"); status.setTextSize(18); status.setPadding(0,16,0,20); root.addView(status);
        connect=btn("CONNECT TROFEO"); test=btn("TEST IMAGE"); select=btn("SELECT IMAGE"); send=btn("SEND SELECTED IMAGE");
        root.addView(connect); root.addView(test); root.addView(select); root.addView(send);
        log=new TextView(this); log.setText("Log:\nReady"); log.setTextSize(14); log.setPadding(0,24,0,0); root.addView(log,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        connect.setOnClickListener(v->detectAndRequest());
        test.setOnClickListener(v->sendBitmap(makeTestBitmap()));
        select.setOnClickListener(v->{ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i,77); });
        send.setOnClickListener(v->{ if(selected==null) append("Select an image first"); else sendBitmap(selected); });
    }
    Button btn(String s){ Button b=new Button(this); b.setText(s); b.setTextSize(18); b.setAllCaps(false); return b; }

    void detectAndRequest(){
        dev=null; for(UsbDevice d:usb.getDeviceList().values()) if(d.getVendorId()==VID && d.getProductId()==PID){dev=d;break;}
        if(dev==null){setStatus("TROFEO not found"); append("Connect phone in USB Host/OTG mode, then retry."); return;}
        append(String.format(Locale.US,"Found %04X:%04X",dev.getVendorId(),dev.getProductId()));
        if(usb.hasPermission(dev)) openDevice(dev); else {
            PendingIntent pi=PendingIntent.getBroadcast(this,0,new Intent(ACTION_USB_PERMISSION).setPackage(getPackageName()),PendingIntent.FLAG_MUTABLE);
            usb.requestPermission(dev,pi); setStatus("Waiting for USB permission…");
        }
    }

    void openDevice(UsbDevice d){ closeUsb(); dev=d; conn=usb.openDevice(d); if(conn==null){setStatus("Cannot open USB device");return;}
        // Prefer HID interface class 3 with OUT 0x02 and IN 0x83.
        for(int i=0;i<d.getInterfaceCount();i++){
            UsbInterface f=d.getInterface(i); UsbEndpoint o=null,in=null;
            for(int j=0;j<f.getEndpointCount();j++){ UsbEndpoint e=f.getEndpoint(j); if(e.getDirection()==UsbConstants.USB_DIR_OUT && e.getAddress()==0x02)o=e; if(e.getDirection()==UsbConstants.USB_DIR_IN && e.getAddress()==0x83)in=e; }
            if(o!=null && in!=null){intf=f;epOut=o;epIn=in;break;}
        }
        if(intf==null || !conn.claimInterface(intf,true)){setStatus("USB interface 0x02/0x83 unavailable"); closeUsb(); return;}
        setStatus("USB connected — ready for handshake"); append("Interface claimed: OUT 0x02 / IN 0x83");
    }

    void sendBitmap(Bitmap bmp){ if(conn==null){append("Connect TROFEO first");return;} setStatus("Sending…"); io.submit(()->{
        try{
            Handshake h=handshake();
            if(!h.ok) throw new IOException("Handshake failed: "+h.msg);
            if(h.pm!=128) throw new IOException("PM="+h.pm+" — expected 128. Frame NOT sent.");
            byte[] jpg=toJpeg(fitCover(bmp,WIDTH,HEIGHT),95);
            byte[] frame=buildFrame(jpg);
            int sent=transferOut(frame,8000);
            if(sent!=frame.length) throw new IOException("Short write "+sent+"/"+frame.length);
            runOnUiThread(()->{setStatus("TEST SENT — PM 128 / 1280×480"); append("JPEG sent: "+jpg.length+" bytes. Note: v0.1 sends ONE frame per connection to avoid firmware lock.");});
        }catch(Exception e){ runOnUiThread(()->{setStatus("Send failed");append(e.toString());}); }
    }); }

    static class Handshake {boolean ok; int pm; String msg; Handshake(boolean o,int p,String m){ok=o;pm=p;msg=m;}}
    Handshake handshake() throws Exception {
        byte[] init=new byte[512]; init[0]=(byte)0xDA;init[1]=(byte)0xDB;init[2]=(byte)0xDC;init[3]=(byte)0xDD;init[12]=1;
        int w=transferOut(init,3000); if(w!=512)return new Handshake(false,-1,"init write="+w);
        Thread.sleep(200);
        ByteArrayOutputStream r=new ByteArrayOutputStream(); long end=System.currentTimeMillis()+5000;
        while(System.currentTimeMillis()<end && r.size()<36){ byte[] p=transferIn(8,800); if(p!=null && p.length>0)r.write(p); else if(r.size()>=20)break; }
        byte[] a=r.toByteArray();
        if(a.length<20)return new Handshake(false,-1,"response length="+a.length);
        boolean magic=(a[0]&255)==0xDA&&(a[1]&255)==0xDB&&(a[2]&255)==0xDC&&(a[3]&255)==0xDD;
        boolean ok=magic && (a[12]&255)==1; int pm=a[5]&255;
        runOnUiThread(()->append("Handshake: bytes="+a.length+", PM="+pm));
        return new Handshake(ok,pm,ok?"OK":"bad magic/status");
    }

    int transferOut(byte[] data,int timeout) throws Exception {
        UsbRequest req=new UsbRequest(); if(!req.initialize(conn,epOut))throw new IOException("OUT request init failed");
        ByteBuffer bb=ByteBuffer.allocateDirect(data.length); bb.put(data); bb.flip();
        if(!req.queue(bb))throw new IOException("OUT queue failed"); UsbRequest done=conn.requestWait(timeout); req.close();
        if(done==null) return -1; return bb.position();
    }
    byte[] transferIn(int max,int timeout) throws Exception {
        UsbRequest req=new UsbRequest(); if(!req.initialize(conn,epIn))return null; ByteBuffer bb=ByteBuffer.allocateDirect(max);
        if(!req.queue(bb)){req.close();return null;} UsbRequest done=conn.requestWait(timeout); req.close(); if(done==null)return null;
        int n=bb.position(); byte[] out=new byte[n]; bb.flip(); bb.get(out); return out;
    }

    byte[] buildFrame(byte[] jpg){
        int raw=20+jpg.length, padded=((raw+511)/512)*512; ByteBuffer b=ByteBuffer.allocate(padded).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte)0xDA).put((byte)0xDB).put((byte)0xDC).put((byte)0xDD);
        b.putShort((short)2); b.putShort((short)0); b.putShort((short)WIDTH); b.putShort((short)HEIGHT); b.putInt(2); b.putInt(jpg.length); b.put(jpg); return b.array();
    }
    byte[] toJpeg(Bitmap b,int q)throws IOException{ByteArrayOutputStream o=new ByteArrayOutputStream(); if(!b.compress(Bitmap.CompressFormat.JPEG,q,o))throw new IOException("JPEG encode failed"); return o.toByteArray();}
    Bitmap fitCover(Bitmap src,int w,int h){ Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(out); c.drawColor(Color.BLACK); float s=Math.max(w/(float)src.getWidth(),h/(float)src.getHeight()); float dw=src.getWidth()*s,dh=src.getHeight()*s; RectF d=new RectF((w-dw)/2,(h-dh)/2,(w+dw)/2,(h+dh)/2); c.drawBitmap(src,null,d,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG)); return out; }
    Bitmap makeTestBitmap(){Bitmap b=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Color.rgb(12,12,16));Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));p.setTextSize(105);c.drawText("HELLO TROFEO",WIDTH/2f,220,p);p.setTextSize(45);c.drawText("GALAXY S25 ULTRA  •  USB TEST OK",WIDTH/2f,315,p);return b;}

    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(req==77&&res==RESULT_OK&&data!=null){Uri u=data.getData();try(InputStream in=getContentResolver().openInputStream(u)){selected=BitmapFactory.decodeStream(in);setStatus("Image selected — ready to send");append("Image: "+selected.getWidth()+"×"+selected.getHeight());}catch(Exception e){append(e.toString());}}}
    void closeUsb(){try{if(conn!=null&&intf!=null)conn.releaseInterface(intf);}catch(Exception ignored){}try{if(conn!=null)conn.close();}catch(Exception ignored){}conn=null;intf=null;epOut=null;epIn=null;}
    void setStatus(String s){runOnUiThread(()->status.setText(s));}
    void append(String s){runOnUiThread(()->log.append("\n"+s));}
}
