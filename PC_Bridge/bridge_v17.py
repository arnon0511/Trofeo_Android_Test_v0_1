import socket,threading,time,hid,struct
VID,PID=0x0416,0x5302
MAGIC=b"\xDA\xDB\xDC\xDD"
OUT_W,OUT_H=1280,480
latest=None; lk=threading.Lock(); ev=threading.Event(); dev=None
rx_frames=usb_frames=dropped=0

def open_trofeo():
 global dev
 if dev is not None:return
 print("Opening TROFEO 0416:5302...",flush=True)
 d=hid.device(); d.open(VID,PID); d.set_nonblocking(False)
 p=bytearray(512); p[:4]=MAGIC; p[12]=1
 d.write(b"\x00"+p)
 time.sleep(.06)
 try:d.read(512,1000)
 except:pass
 dev=d
 print("TROFEO READY",flush=True)

def send_usb(j):
 global usb_frames
 open_trofeo()
 h=bytearray(20)
 h[:4]=MAGIC
 h[8:10]=OUT_W.to_bytes(2,"little")
 h[10:12]=OUT_H.to_bytes(2,"little")
 h[12:16]=(2).to_bytes(4,"little")
 h[16:20]=len(j).to_bytes(4,"little")
 f=bytes(h)+j
 f+=b"\x00"*((512-len(f)%512)%512)
 for i in range(0,len(f),512):
  dev.write(b"\x00"+f[i:i+512])
 usb_frames+=1

def usb_worker():
 global latest,dropped,dev
 while True:
  ev.wait()
  while True:
   with lk:
    j=latest; latest=None
    if j is None:
     ev.clear(); break
   try:send_usb(j)
   except Exception as e:
    print("USB ERROR:",repr(e),flush=True)
    try:
     if dev:dev.close()
    except:pass
    dev=None

threading.Thread(target=usb_worker,daemon=True).start()

sock=socket.socket(socket.AF_INET,socket.SOCK_DGRAM)
sock.setsockopt(socket.SOL_SOCKET,socket.SO_RCVBUF,4*1024*1024)
sock.bind(("0.0.0.0",8766))
print("Car Media v1.7 UDP -> TROFEO",flush=True)
print("Waiting on UDP port 8766...",flush=True)

parts={}
totals={}
last_seq=-1
last_stat=time.time()
while True:
 d,addr=sock.recvfrom(65535)
 if len(d)<16:continue
 magic,seq,n,total=struct.unpack(">IIII",d[:16])
 if magic!=0x434D3135:continue
 # Ignore stale frames as soon as a newer sequence has arrived.
 if seq<last_seq:continue
 if seq>last_seq:
  last_seq=seq
  for k in list(parts):
   if k<seq:parts.pop(k,None);totals.pop(k,None)
 parts.setdefault(seq,{})[n]=d[16:]
 totals[seq]=total
 if len(parts[seq])==total:
  j=b"".join(parts[seq][x] for x in range(total))
  parts.pop(seq,None);totals.pop(seq,None)
  rx_frames+=1
  with lk:
   if latest is not None:dropped+=1
   latest=j;ev.set()
 now=time.time()
 if now-last_stat>=1:
  print("RX",rx_frames,"USB",usb_frames,"DROP",dropped,"latest_seq",last_seq,flush=True)
  last_stat=now
