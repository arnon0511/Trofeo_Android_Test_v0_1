package com.tsk.carmedia
import android.app.*
import android.content.*
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity:AppCompatActivity(){
    private val rc=77
    lateinit var ip:EditText
    lateinit var st:TextView
    private val prefs by lazy { getSharedPreferences("car_media", MODE_PRIVATE) }

    override fun onCreate(b:Bundle?){
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        ip=findViewById(R.id.ip)
        st=findViewById(R.id.status)
        ip.setText(prefs.getString("pi_ip","172.19.95.46"))

        findViewById<Button>(R.id.start).setOnClickListener{
            val target=ip.text.toString().trim()
            if(target.isEmpty()){
                st.text="กรุณาใส่ IP ของ Raspberry Pi"
                return@setOnClickListener
            }
            prefs.edit().putString("pi_ip",target).apply()
            startActivityForResult(
                (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
                    .createScreenCaptureIntent(), rc)
        }

        findViewById<Button>(R.id.stop).setOnClickListener{
            stopService(Intent(this,MirrorService::class.java))
            st.text="STOPPED"
        }
    }

    override fun onActivityResult(r:Int,c:Int,d:Intent?){
        super.onActivityResult(r,c,d)
        if(r==rc&&c==RESULT_OK&&d!=null){
            val target=ip.text.toString().trim()
            startForegroundService(
                Intent(this,MirrorService::class.java)
                    .putExtra("code",c).putExtra("data",d).putExtra("ip",target))
            st.text="SENDING → $target:8766 ✓"
        }
    }
}