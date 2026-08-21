import sounddevice as sd
import scipy.io.wavfile as wav
import requests
import json
import time

URL = "http://localhost:8000/api/score"
FS = 16000  # Sample rate
DURATION = 3  # Seconds

print("🎙️  SpeechPath Direct Mic Tester")
print("========================================\n")

word = input("Enter the word you want to practice (e.g., 'ship', 'sun', 'red'): ").strip()
phoneme = input(f"Enter the phoneme to test for '{word}' (e.g., '/sh/', '/s/', '/r/'): ").strip()

print(f"\nGet ready to say '{word}' into your Mac's microphone...")
for i in range(3, 0, -1):
    print(f"{i}...")
    time.sleep(1)

print("\n🔴 RECORDING NOW! Speak into the mic...")
myrecording = sd.rec(int(DURATION * FS), samplerate=FS, channels=1, dtype='int16')
sd.wait()  # Wait until recording is finished
print("✅ RECORDING STOPPED. Processing...\n")

# Save as WAV
wav.write('temp_mac_mic.wav', FS, myrecording)

print("Sending audio to backend...")

with open('temp_mac_mic.wav', 'rb') as f:
    files = {"audio_file": ('temp_mac_mic.wav', f, "audio/wav")}
    data = {
        "expected_text": word,
        "target_phoneme": phoneme
    }
    
    try:
        response = requests.post(URL, files=files, data=data)
        
        if response.status_code == 200:
            result = response.json()
            print("\n" + "="*40)
            print(f"✅  Score: {result['score']}%")
            print(f"📝  Feedback: {result['feedback']}")
            print(f"🤖  Backend Heard: \"{result['recognized_text']}\"")
            print("="*40 + "\n")
        else:
            print(f"❌  Error: {response.status_code} - {response.text}")
            
    except Exception as e:
        print(f"❌  Connection Error: {e}")
