import requests
import json

URL = "http://localhost:8000/api/score"

tests = [
    {
        "file": "correct_ship.wav",
        "expected": "ship",
        "phoneme": "/sh/",
        "description": "Correct pronunciation ('ship')"
    },
    {
        "file": "incorrect_ship_sip.wav",
        "expected": "ship",
        "phoneme": "/sh/",
        "description": "Incorrect pronunciation ('sip' instead of 'ship')"
    },
    {
        "file": "incorrect_ship_sheep.wav",
        "expected": "ship",
        "phoneme": "/sh/",
        "description": "Incorrect pronunciation ('sheep' instead of 'ship')"
    }
]

print("🎙️  Testing SpeechPath Backend\n" + "="*40)

for test in tests:
    print(f"\n▶️  TEST: {test['description']}")
    
    with open(test["file"], "rb") as f:
        files = {"audio_file": (test["file"], f, "audio/wav")}
        data = {
            "expected_text": test["expected"],
            "target_phoneme": test["phoneme"]
        }
        
        try:
            response = requests.post(URL, files=files, data=data)
            
            if response.status_code == 200:
                result = response.json()
                print(f"✅  Score: {result['score']}%")
                print(f"📝  Feedback: {result['feedback']}")
                print(f"🤖  Backend Heard: \"{result['recognized_text']}\"")
            else:
                print(f"❌  Error: {response.status_code} - {response.text}")
                
        except Exception as e:
            print(f"❌  Connection Error: {e}")

print("\n" + "="*40 + "\n🏁  Testing Complete!")
