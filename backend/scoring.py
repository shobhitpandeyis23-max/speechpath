from difflib import SequenceMatcher
import speech_recognition as sr
import pronouncing
import os


def transcribe_audio(wav_file_path: str) -> tuple[str, float]:
    """
    Transcribe a WAV audio file to text using Google Speech Recognition.
    Returns (recognized_text, confidence).
    Confidence is estimated from the recognition result.
    """
    recognizer = sr.Recognizer()

    # Adjust for ambient noise sensitivity
    recognizer.energy_threshold = 300
    recognizer.dynamic_energy_threshold = True

    try:
        with sr.AudioFile(wav_file_path) as source:
            audio = recognizer.record(source)

        # Use Google's free speech recognition
        # show_all=True returns detailed results with confidence
        result = recognizer.recognize_google(audio, show_all=True)

        if not result or not result.get("alternative"):
            return "", 0.0

        best = result["alternative"][0]
        text = best.get("transcript", "").lower().strip()
        confidence = best.get("confidence", 0.85)

        return text, float(confidence)

    except sr.UnknownValueError:
        return "", 0.0
    except sr.RequestError as e:
        print(f"Google Speech Recognition API error: {e}")
        return "", 0.0


def get_phonemes(word: str) -> list[str]:
    """
    Get the phoneme representation of a word using the CMU Pronouncing Dictionary.
    Returns a list of phonemes, or an empty list if the word is not found.
    """
    phones = pronouncing.phones_for_word(word.lower())
    if phones:
        # Return the first pronunciation variant
        return phones[0].split()
    return []


def phonetic_score(expected_word: str, recognized_text: str) -> float:
    """
    Compare phonemes of the expected word vs what was recognized.
    Returns a score between 0.0 and 1.0.
    """
    expected_phonemes = get_phonemes(expected_word)
    recognized_phonemes = get_phonemes(recognized_text)

    if not expected_phonemes:
        # Word not in CMU dictionary, fall back to text comparison
        return text_similarity(expected_word, recognized_text)

    if not recognized_phonemes:
        return 0.0

    # Compare phoneme sequences using SequenceMatcher
    expected_str = " ".join(expected_phonemes)
    recognized_str = " ".join(recognized_phonemes)

    return SequenceMatcher(None, expected_str, recognized_str).ratio()


def text_similarity(expected: str, recognized: str) -> float:
    """
    Calculate text similarity between expected and recognized words.
    Returns a score between 0.0 and 1.0.
    """
    if not recognized:
        return 0.0
    return SequenceMatcher(None, expected.lower(), recognized.lower()).ratio()


def calculate_score(
    expected_word: str,
    recognized_text: str,
    confidence: float
) -> int:
    """
    Calculate the final pronunciation score (0-100) using three layers:
    - Word match (40%): exact match of the expected word
    - Phonetic match (40%): phoneme-level comparison
    - Confidence (20%): how confident the recognizer was
    """
    if not recognized_text:
        return 0

    # Layer 1: Word match (40%)
    # Check if the expected word appears in the recognized text
    recognized_words = recognized_text.lower().split()
    expected_lower = expected_word.lower()

    if expected_lower in recognized_words:
        word_match = 1.0
    else:
        # Find the best matching word in the recognized text
        best_match = 0.0
        for word in recognized_words:
            similarity = text_similarity(expected_lower, word)
            best_match = max(best_match, similarity)
        word_match = best_match

    # Layer 2: Phonetic match (40%)
    # Compare phonemes of the best matching word
    best_phonetic = 0.0
    for word in recognized_words:
        score = phonetic_score(expected_word, word)
        best_phonetic = max(best_phonetic, score)

    # If no individual word matched well, try the full text
    if best_phonetic < 0.5:
        full_score = phonetic_score(expected_word, recognized_text)
        best_phonetic = max(best_phonetic, full_score)

    # Layer 3: Confidence (20%)
    conf_score = min(confidence, 1.0)

    # Weighted combination
    final_score = (word_match * 0.40) + (best_phonetic * 0.40) + (conf_score * 0.20)

    # Convert to 0-100 and clamp
    return max(0, min(100, round(final_score * 100)))


def generate_feedback(
    score: int,
    expected_word: str,
    recognized_text: str,
    target_phoneme: str
) -> str:
    """
    Generate helpful, phoneme-specific feedback based on the score.
    """
    if not recognized_text:
        return "I couldn't hear you clearly. Please try again in a quieter environment."

    # Clean phoneme display (e.g., "/sh/" -> "sh")
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
