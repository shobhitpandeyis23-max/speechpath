from difflib import SequenceMatcher
import speech_recognition as sr
import pronouncing
import os
import wave
import struct


def preprocess_wav(wav_file_path: str) -> str:
    """
    Preprocess the WAV file from the Android app to ensure compatibility
    with the SpeechRecognition library.
    """
    output_path = wav_file_path + ".clean.wav"

    try:
        with open(wav_file_path, "rb") as f:
            raw_data = f.read()

        data_offset = raw_data.find(b'data')
        if data_offset == -1:
            pcm_data = raw_data[44:]
        else:
            size_offset = data_offset + 4
            if size_offset + 4 <= len(raw_data):
                chunk_size = struct.unpack('<I', raw_data[size_offset:size_offset + 4])[0]
                pcm_start = size_offset + 4
                pcm_data = raw_data[pcm_start:pcm_start + chunk_size]
            else:
                pcm_data = raw_data[44:]

        if len(pcm_data) < 100:
            print(f"  WARNING: Very little audio data ({len(pcm_data)} bytes)")
            return wav_file_path

        with wave.open(output_path, 'wb') as wav_out:
            wav_out.setnchannels(1)        # Mono
            wav_out.setsampwidth(2)        # 16-bit
            wav_out.setframerate(16000)    # 16kHz
            wav_out.writeframes(pcm_data)

        print(f"  Preprocessed: {len(pcm_data)} bytes of PCM audio → {output_path}")
        return output_path

    except Exception as e:
        print(f"  Preprocessing failed ({e}), using original file")
        return wav_file_path


def normalize_audio(audio_data: sr.AudioData) -> sr.AudioData:
    """
    Normalize audio volume to improve recognition of quiet speech.
    """
    raw = audio_data.get_raw_data()
    samples = struct.unpack(f'<{len(raw)//2}h', raw)

    if not samples:
        return audio_data

    peak = max(abs(s) for s in samples)

    if peak == 0:
        return audio_data

    target_peak = int(32767 * 0.8)
    scale = target_peak / peak

    scale = min(scale, 10.0)

    if scale > 1.1:
        normalized = [max(-32768, min(32767, int(s * scale))) for s in samples]
        normalized_bytes = struct.pack(f'<{len(normalized)}h', *normalized)
        print(f"  Audio normalized: peak {peak} → {int(peak * scale)} (scale: {scale:.1f}x)")
        return sr.AudioData(normalized_bytes, audio_data.sample_rate, audio_data.sample_width)

    return audio_data


def transcribe_audio(wav_file_path: str) -> tuple[str, float]:
    recognizer = sr.Recognizer()
    recognizer.energy_threshold = 100
    recognizer.dynamic_energy_threshold = False
    recognizer.pause_threshold = 1.0

    clean_path = preprocess_wav(wav_file_path)

    try:
        with sr.AudioFile(clean_path) as source:
            audio = recognizer.record(source)

        audio = normalize_audio(audio)

        text, confidence = _try_recognize(recognizer, audio)
        if text:
            return text, confidence

        if clean_path != wav_file_path:
            print("  Retrying with original file...")
            with sr.AudioFile(wav_file_path) as source:
                audio_original = recognizer.record(source)
            audio_original = normalize_audio(audio_original)
            text, confidence = _try_recognize(recognizer, audio_original)
            if text:
                return text, confidence

        return "", 0.0

    except Exception as e:
        print(f"  Transcription error: {e}")
        return "", 0.0

    finally:
        if clean_path != wav_file_path and os.path.exists(clean_path):
            os.unlink(clean_path)


def _try_recognize(recognizer: sr.Recognizer, audio: sr.AudioData) -> tuple[str, float]:
    try:
        result = recognizer.recognize_google(audio, show_all=True, language="en-IN")

        if not result:
            result = recognizer.recognize_google(audio, show_all=True, language="en-US")

        if not result or not isinstance(result, dict) or not result.get("alternative"):
            return "", 0.0

        best = result["alternative"][0]
        text = best.get("transcript", "").lower().strip()
        confidence = best.get("confidence", 0.85)

        return text, float(confidence)

    except sr.UnknownValueError:
        return "", 0.0
    except sr.RequestError as e:
        print(f"  Google Speech API error: {e}")
        return "", 0.0


def get_phonemes(word: str) -> list[str]:
    phones = pronouncing.phones_for_word(word.lower().strip())
    if phones:
        return phones[0].split()
    return []


def phonetic_score(expected_word: str, recognized_text: str) -> float:
    expected_phonemes = get_phonemes(expected_word)
    recognized_phonemes = get_phonemes(recognized_text)

    if not expected_phonemes:
        return text_similarity(expected_word, recognized_text)

    if not recognized_phonemes:
        return 0.0

    expected_str = " ".join(expected_phonemes)
    recognized_str = " ".join(recognized_phonemes)

    return SequenceMatcher(None, expected_str, recognized_str).ratio()


def text_similarity(expected: str, recognized: str) -> float:
    if not recognized:
        return 0.0
    return SequenceMatcher(None, expected.lower(), recognized.lower()).ratio()


def calculate_score(
    expected_word: str,
    recognized_text: str,
    confidence: float
) -> int:
    if not recognized_text:
        return 0

    recognized_words = recognized_text.lower().split()
    expected_lower = expected_word.lower()

    if expected_lower in recognized_words:
        word_match = 1.0
    else:
        best_match = 0.0
        for word in recognized_words:
            similarity = text_similarity(expected_lower, word)
            best_match = max(best_match, similarity)
        word_match = best_match

    best_phonetic = 0.0
    for word in recognized_words:
        score = phonetic_score(expected_word, word)
        best_phonetic = max(best_phonetic, score)

    if best_phonetic < 0.5:
        full_score = phonetic_score(expected_word, recognized_text)
        best_phonetic = max(best_phonetic, full_score)

    conf_score = min(confidence, 1.0)
    final_score = (word_match * 0.40) + (best_phonetic * 0.40) + (conf_score * 0.20)

    return max(0, min(100, round(final_score * 100)))


def generate_feedback(
    score: int,
    expected_word: str,
    recognized_text: str,
    target_phoneme: str
) -> str:
    if not recognized_text:
        return "I couldn't hear you clearly. Try speaking a bit louder and closer to the mic."

    phoneme_clean = target_phoneme.strip("/")

    if score >= 90:
        return f"Excellent! Your /{phoneme_clean}/ sound in \"{expected_word}\" was spot on! 🎉"
    elif score >= 75:
        return f"Great job! Your /{phoneme_clean}/ sound is very good. Keep practicing!"
    elif score >= 60:
        return f"Good effort! Focus more on the /{phoneme_clean}/ sound. I heard \"{recognized_text}\" instead of \"{expected_word}\"."
    elif score >= 40:
        return f"Keep trying! The /{phoneme_clean}/ sound needs work. I heard \"{recognized_text}\" — try saying \"{expected_word}\" more slowly."
    else:
        return f"Let's try again! Focus on the /{phoneme_clean}/ sound in \"{expected_word}\". Speak clearly and slowly."
