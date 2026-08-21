from fastapi import FastAPI, File, UploadFile, Form
from fastapi.responses import JSONResponse
from scoring import transcribe_audio, calculate_score, generate_feedback
import os
import tempfile
import shutil

app = FastAPI(
    title="SpeechPath Pronunciation Scorer",
    description="Real pronunciation scoring backend for the SpeechPath Android app",
    version="1.0.0"
)

# Set this to True to fake a perfect score (useful when emulator microphone is broken)
DEMO_MODE = True


@app.get("/")
async def root():
    return {"status": "running", "message": "SpeechPath Backend is live!"}


@app.get("/health")
async def health():
    return {"status": "healthy"}


@app.post("/api/score")
async def score_audio(
    audio_file: UploadFile = File(...),
    expected_text: str = Form(...),
    target_phoneme: str = Form(...)
):
    """
    Receive a WAV audio file, transcribe it, and score pronunciation.

    - audio_file: WAV audio recording from the Android app
    - expected_text: The word the user was supposed to say (e.g., "ship")
    - target_phoneme: The phoneme being practiced (e.g., "/sh/")
    """
    temp_path = None

    try:
        # Save uploaded file to a temporary location
        with tempfile.NamedTemporaryFile(
            delete=False, suffix=".wav", dir=tempfile.gettempdir()
        ) as temp_file:
            temp_path = temp_file.name
            shutil.copyfileobj(audio_file.file, temp_file)

        print(f"\n{'='*50}")
        print(f"Received audio for scoring")
        print(f"  Expected word: {expected_text}")
        print(f"  Target phoneme: {target_phoneme}")
        print(f"  Audio file: {audio_file.filename} ({audio_file.content_type})")
        print(f"  Temp path: {temp_path}")
        print(f"  File size: {os.path.getsize(temp_path)} bytes")

        if DEMO_MODE:
            print("  [DEMO MODE ENABLED] Faking successful recognition...")
            recognized_text = expected_text
            confidence = 0.95
        else:
            # Step 1: Transcribe the audio
            recognized_text, confidence = transcribe_audio(temp_path)
        
        print(f"  Recognized: \"{recognized_text}\" (confidence: {confidence:.2f})")

        # Step 2: Calculate pronunciation score
        score = calculate_score(expected_text, recognized_text, confidence)
        print(f"  Score: {score}%")

        # Step 3: Generate feedback
        feedback = generate_feedback(score, expected_text, recognized_text, target_phoneme)
        print(f"  Feedback: {feedback}")
        print(f"{'='*50}\n")

        return JSONResponse(content={
            "score": score,
            "feedback": feedback,
            "recognized_text": recognized_text if recognized_text else "Could not recognize speech"
        })

    except Exception as e:
        print(f"Error processing audio: {e}")
        import traceback
        traceback.print_exc()
        return JSONResponse(
            status_code=500,
            content={
                "score": 0,
                "feedback": f"Server error: {str(e)}",
                "recognized_text": ""
            }
        )

    finally:
        # Clean up temp file
        if temp_path and os.path.exists(temp_path):
            os.unlink(temp_path)


if __name__ == "__main__":
    import uvicorn
    print("\n🎙️  SpeechPath Backend Starting...")
    print("📍 Server will be available at: http://0.0.0.0:8000")
    print("📍 Android emulator can reach it at: http://10.0.2.2:8000")
    print("📍 API docs at: http://localhost:8000/docs\n")
    uvicorn.run(app, host="0.0.0.0", port=8000)
