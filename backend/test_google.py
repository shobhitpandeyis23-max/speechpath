import speech_recognition as sr
import json

recognizer = sr.Recognizer()
with sr.AudioFile("temp_mac_mic.wav") as source:
    audio = recognizer.record(source)

result = recognizer.recognize_google(audio, show_all=True)
print(json.dumps(result, indent=2))
